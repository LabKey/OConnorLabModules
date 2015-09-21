/*
 * Copyright (c) 2010-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.genotyping;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AtomicDatabaseInteger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.NotFoundException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class GenotypingManager
{
    private static final GenotypingManager _instance = new GenotypingManager();

    public static final String PROPERTIES_FILE_NAME = "properties.xml";
    public static final String READS_FILE_NAME = "reads.txt";
    public static final String MATCHES_FILE_NAME = "matches.txt";
    public static final String SEQUENCES_FILE_NAME = "sequences.fasta";

    public enum SEQUENCE_PLATFORMS
    {
        LS454, ILLUMINA, PACBIO;

        // Default to ILLUMINA platform (null or unrecognized)
        public static @NotNull SEQUENCE_PLATFORMS getPlatform(@Nullable String platform)
        {
            if(LS454.name().equals(platform))
                return LS454;
            else if(PACBIO.name().equals(platform))
                return PACBIO;

            return ILLUMINA;
        }
    }

    private GenotypingManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GenotypingManager get()
    {
        return _instance;
    }

    static final String FOLDER_CATEGORY = "GenotypingSettings";

    public static enum Setting
    {
        ReferenceSequencesQuery("SequencesQuery", "the source of DNA reference sequences"),
        RunsQuery("RunsQuery", "run meta data"),
        SamplesQuery("SamplesQuery", "sample information"),
        HaplotypesQuery("HaplotypesQuery", "haplotype definitions");

        private final String _key;
        private final String _description;

        private Setting(String key, String friendlyName)
        {
            _key = key;
            _description = friendlyName;
        }

        public String getKey()
        {
            return _key;
        }

        public String getDescription()
        {
            return _description;
        }
    }

    public void saveSettings(Container c, GenotypingFolderSettings settings)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(c, FOLDER_CATEGORY, true);
        map.put(Setting.ReferenceSequencesQuery.getKey(), settings.getSequencesQuery());
        map.put(Setting.RunsQuery.getKey(), settings.getRunsQuery());
        map.put(Setting.SamplesQuery.getKey(), settings.getSamplesQuery());
        map.put(Setting.HaplotypesQuery.getKey(), settings.getHaplotypesQuery());
        map.save();
    }

    public GenotypingRun createRun(Container c, User user, Integer metaDataId, File readsFile, String platform)
    {
        MetaDataRun mdRun = null;

        if (null != metaDataId)
            mdRun = getMetaDataRun(c, user, metaDataId, "importing reads");

        GenotypingRun run = new GenotypingRun(c, readsFile, mdRun, platform);
        return Table.insert(user, GenotypingSchema.get().getRunsTable(), run);
    }

    public @Nullable GenotypingRun getRun(Container c, int runId)
    {
        return new TableSelector(GenotypingSchema.get().getRunsTable()).getObject(c, runId, GenotypingRun.class);
    }

    public MetaDataRun getMetaDataRun(Container c, User user, int runId, String action)
    {
        ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(c, user, action);
        QueryHelper qHelper = new GenotypingQueryHelper(c, user, settings.getRunsQuery());
        MetaDataRun run = new TableSelector(qHelper.getTableInfo()).getObject(runId, MetaDataRun.class);

        if (null != run)
            run.setContainer(c);

        return run;
    }

    public GenotypingAnalysis createAnalysis(Container c, User user, GenotypingRun run, @Nullable String description, @Nullable String sequencesViewName)
    {
        return Table.insert(user, GenotypingSchema.get().getAnalysesTable(), new GenotypingAnalysis(c, user, run, description, sequencesViewName));
    }

    public @NotNull GenotypingAnalysis getAnalysis(Container c, Integer analysisId)
    {
        if (null == analysisId)
            throw new NotFoundException("Analysis parameter is missing");

        GenotypingAnalysis analysis = new TableSelector(GenotypingSchema.get().getAnalysesTable()).getObject(analysisId, GenotypingAnalysis.class);

        if (null != analysis)
        {
            GenotypingRun run = getRun(c, analysis.getRun());

            if (null != run)
            {
                analysis.setContainer(c);
                return analysis;
            }
        }

        throw new NotFoundException("Analysis " + analysisId + " not found in folder " + c.getPath());
    }


    // Multiple threads could attempt to set the status at roughly the same time. (For example, there are several ways
    // to initiate an analysis import: signal from Galaxy, pipeline ui, script, etc.) Use an AtomicDatabaseInteger to
    // synchronously set the status.  Returns true if status was changed, false if it wasn't.
    public boolean updateAnalysisStatus(GenotypingAnalysis analysis, User user, Status expected, Status update) throws SQLException
    {
        assert (expected.getStatusId() + 1) == update.getStatusId();

        AtomicDatabaseInteger status = new AtomicDatabaseInteger(GenotypingSchema.get().getAnalysesTable().getColumn("Status"), user, null, analysis.getRowId());
        return status.compareAndSet(expected.getStatusId(), update.getStatusId());
    }


    public Collection<GenotypingRun> getRuns(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        TableSelector selector = new TableSelector(GenotypingSchema.get().getRunsTable(), filter, null);

        return selector.getCollection(GenotypingRun.class);
    }


    // Delete all runs, reads, analyses, matches, and junction table rows associated with this container
    public void delete(Container c)
    {
        for (GenotypingRun run : getRuns(c))
        {
            try
            {
                deleteRun(run);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        GenotypingSchema gs = GenotypingSchema.get();
        SqlExecutor executor = new SqlExecutor(gs.getSchema());

        SQLFragment deleteSequencesSql = new SQLFragment("DELETE FROM ");
        deleteSequencesSql.append(gs.getSequencesTable().getSelectName()).append(" WHERE Dictionary IN (SELECT RowId FROM ");
        deleteSequencesSql.append(gs.getDictionariesTable().getSelectName()).append(" WHERE Container = ?)").add(c);
        executor.execute(deleteSequencesSql);

        SQLFragment deleteDictionariesSql = new SQLFragment("DELETE FROM ");
        deleteDictionariesSql.append(gs.getDictionariesTable().getSelectName()).append(" WHERE Container = ?").add(c);
        executor.execute(deleteDictionariesSql);

        // delete the haplotype assignment junction tables and animal/haplotype rows
        SQLFragment deleteAssignmentSql = new SQLFragment("DELETE FROM ");
        deleteAssignmentSql.append(gs.getAnimalHaplotypeAssignmentTable().getSelectName());
        deleteAssignmentSql.append(" WHERE AnimalAnalysisId IN (SELECT RowId FROM ");
        deleteAssignmentSql.append(gs.getAnimalAnalysisTable().getSelectName());
        deleteAssignmentSql.append(" WHERE RunId IN (SELECT RowId FROM exp.ExperimentRun ");
        deleteAssignmentSql.append(" WHERE Container = ?))").add(c);
        executor.execute(deleteAssignmentSql);

        SQLFragment deleteAnimalAnalysisSql = new SQLFragment("DELETE FROM ");
        deleteAnimalAnalysisSql.append(gs.getAnimalAnalysisTable().getSelectName());
        deleteAnimalAnalysisSql.append(" WHERE RunId IN (SELECT RowId FROM exp.ExperimentRun ");
        deleteAnimalAnalysisSql.append(" WHERE Container = ?)").add(c);
        executor.execute(deleteAnimalAnalysisSql);

        SQLFragment deleteAnimalSql = new SQLFragment("DELETE FROM ");
        deleteAnimalSql.append(gs.getAnimalTable().getSelectName()).append(" WHERE Container = ?").add(c);
        executor.execute(deleteAnimalSql);

        SQLFragment deleteHaplotypeSql = new SQLFragment("DELETE FROM ");
        deleteHaplotypeSql.append(gs.getHaplotypeTable().getSelectName()).append(" WHERE Container = ?").add(c);
        executor.execute(deleteHaplotypeSql);
    }


    // Deletes all the reads, analyses, and matches associated with a run, including rows in all junction tables.
    public void deleteRun(GenotypingRun run) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();
        deleteAnalyses(" WHERE Run = ? AND Run IN (SELECT RowId FROM " + gs.getRunsTable() + " WHERE Container = ?)", run.getRowId(), run.getContainer());

        SqlExecutor executor = new SqlExecutor(gs.getSchema());
        executor.execute("DELETE FROM " + gs.getReadsTable() + " WHERE Run = ?", run.getRowId());
        executor.execute("DELETE FROM " + gs.getSequenceFilesTable() + " WHERE Run = ?", run.getRowId());
        executor.execute("DELETE FROM " + gs.getRunsTable() + " WHERE RowId = ?", run.getRowId());
    }


    public void deleteAnalysis(GenotypingAnalysis analysis) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();
        deleteAnalyses(" WHERE RowId = ? AND Run IN (SELECT RowId FROM " + gs.getRunsTable() + " WHERE Container = ?)", analysis.getRowId(), analysis.getContainer());
    }


    private void deleteAnalyses(CharSequence analysisFilter, Object... params) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();
        String analysisFrom = " FROM " + gs.getAnalysesTable() + analysisFilter;
        String analysisWhere = " WHERE Analysis IN (SELECT RowId" + analysisFrom + ")";
        String matchesWhere = " WHERE MatchId IN (SELECT RowId FROM " + gs.getMatchesTable() + analysisWhere + ")";

        SqlExecutor executor = new SqlExecutor(gs.getSchema());
        executor.execute("DELETE FROM " + gs.getAllelesJunctionTable() + matchesWhere, params);
        executor.execute("DELETE FROM " + gs.getReadsJunctionTable() + matchesWhere, params);
        executor.execute("DELETE FROM " + gs.getMatchesTable() + analysisWhere, params);
        executor.execute("DELETE FROM " + gs.getAnalysisSamplesTable() + analysisWhere, params);
        executor.execute("DELETE " + analysisFrom, params);
    }


    public void writeProperties(Properties props, File directory) throws IOException
    {
        File propXml = new File(directory, PROPERTIES_FILE_NAME);
        OutputStream os = null;
        try
        {
            os = new FileOutputStream(propXml);
            props.storeToXML(os, null);
        }
        finally
        {
            if (null != os)
                os.close();
        }
    }

    public Properties readProperties(File directory) throws IOException
    {
        if (!directory.exists())
            throw new FileNotFoundException(directory.getAbsolutePath() + " does not exist");

        if (!directory.isDirectory())
            throw new FileNotFoundException(directory.getAbsolutePath() + " is not a directory");

        File properties = new File(directory, PROPERTIES_FILE_NAME);

        // Load properties to determine the run.
        Properties props = new Properties();
        InputStream is = null;

        try
        {
            is = new FileInputStream(properties);
            props.loadFromXML(is);
        }
        finally
        {
            if (null != is)
                is.close();
        }

        return props;
    }


    // Return number of runs in the specified container
    public int getRunCount(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        return (int)new TableSelector(GenotypingSchema.get().getRunsTable(), filter, null).getRowCount();
    }


    // Return number of reads in the specified container or run
    public long getReadCount(Container c, @Nullable GenotypingRun run)
    {
        GenotypingSchema gs = GenotypingSchema.get();
        TableInfo reads = gs.getReadsTable();
        TableInfo runs = gs.getRunsTable();

        SQLFragment sql = new SQLFragment("SELECT RowId FROM " + reads + " WHERE Run IN (SELECT RowId FROM " + runs + " WHERE Container = ?)", c);

        if (null != run)
        {
            sql.append(" AND Run = ?");
            sql.add(run.getRowId());
        }

        return (int)new SqlSelector(gs.getSchema(), sql).getRowCount();
    }


    public boolean hasAnalyses(GenotypingRun run)
    {
        return getAnalysisCount(run.getContainer(), run) > 0;
    }


    // Return number of analyses... associated with the specified run (run != null) or in the folder (run == null)
    public int getAnalysisCount(Container c, @Nullable GenotypingRun run)
    {
        GenotypingSchema gs = GenotypingSchema.get();
        TableInfo analyses = gs.getAnalysesTable();
        TableInfo runs = gs.getRunsTable();

        SQLFragment sql = new SQLFragment("SELECT RowId FROM " + analyses + " WHERE Run IN (SELECT RowId FROM " + runs + " WHERE Container = ?)", c);

        if (null != run)
        {
            sql.append(" AND Run = ?");
            sql.add(run.getRowId());
        }

        return (int)new SqlSelector(gs.getSchema(), sql).getRowCount();
    }


    // Return number of matches... associated with the specified analysis (analysis != null) or in the folder (analysis == null)
    public int getMatchCount(Container c, @Nullable GenotypingAnalysis analysis)
    {
        GenotypingSchema gs = GenotypingSchema.get();
        TableInfo matches = gs.getMatchesTable();
        TableInfo analyses = gs.getAnalysesTable();
        TableInfo runs = gs.getRunsTable();

        SQLFragment sql = new SQLFragment("SELECT RowId FROM " + matches + " WHERE Analysis IN (SELECT RowId FROM " + analyses + " WHERE Run IN (SELECT RowId FROM " + runs + " WHERE Container = ?))", c);

        if (null != analysis)
        {
            sql.append(" AND Analysis = ?");
            sql.add(analysis.getRowId());
        }

        return (int)new SqlSelector(gs.getSchema(), sql).getRowCount();
    }


    // Insert a new match that combines the specified matches (if > 1) and associates the specified alleles with the
    // new match.  Assumes that container permissions have been checked, but validates all other aspects of the incoming
    // data: analysis exists in specified container, one or more matches are provided, one or more alleles are provided,
    // matches belong to this analysis and to a single sample, and alleles belong to these matches.
    public Integer combineMatches(Container c, User user, int analysisId, int[] matchIds, int[] alleleIds)
    {
        GenotypingSchema gs = GenotypingSchema.get();

        // ======== Begin validation ========

        // Validate analysis was posted and exists in this container
        GenotypingAnalysis analysis = GenotypingManager.get().getAnalysis(c, analysisId);

        List<Integer> matchIdList = Arrays.asList(ArrayUtils.toObject(matchIds));
        List<Integer> alleleIdList = Arrays.asList(ArrayUtils.toObject(alleleIds));

        // Verify that matches were posted
        if (matchIdList.size() < 1)
            throw new IllegalStateException("No matches were selected");

        // Verify that alleles were posted
        if (alleleIdList.size() < 1)
            throw new IllegalStateException("No alleles were selected");

        Results results = null;

        // Validate the matches
        try
        {
            // Count the corresponding matches in the database, making sure they belong to this analysis
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Analysis"), analysis.getRowId());
            filter.addInClause(FieldKey.fromParts("RowId"), matchIdList);
            TableInfo tinfo = GenotypingQuerySchema.TableType.Matches.createTable(new GenotypingQuerySchema(user, c), analysis.getRowId());
            results = QueryService.get().select(tinfo, tinfo.getColumns("SampleId"), filter, null);
            Set<Integer> sampleIds = new HashSet<>();
            int matchCount = 0;

            // Stash the sampled ids and count the matches
            while (results.next())
            {
                sampleIds.add(results.getInt("SampleId"));
                matchCount++;
            }

            // Verify that the selected match count equals the number of rowIds posted...
            if (matchCount != matchIdList.size())
                throw new IllegalStateException("Selected match" + (1 == matchIdList.size() ? " has" : "es have") + " been modified");

            // Verify all matches are from the same sample
            if (sampleIds.size() != 1)
                throw new IllegalStateException("Matches were detected from different samples");
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ResultSetUtil.close(results);
        }

        // Validate the alleles
        // Select all the alleles associated with these matches
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts("MatchId"), matchIdList);
        TableInfo tinfo = gs.getAllelesJunctionTable();
        Integer[] mAlleles = new TableSelector(tinfo.getColumn("SequenceId"), filter, null).getArray(Integer.class);
        Set<Integer> matchAlleles = new HashSet<>(Arrays.asList(mAlleles));

        if (!matchAlleles.containsAll(alleleIdList))
            throw new IllegalStateException("Selected alleles aren't owned by the selected matches");

        // ======== End validation ========

        Integer newMatchId;

        // Now update the tables: create the new match, insert new rows in the alleles & reads junction tables, and mark the old matches

        // Group all the matches based on analysis and rowIds
        SimpleFilter matchFilter = new SimpleFilter(FieldKey.fromParts("Analysis"), analysis.getRowId());
        matchFilter.addInClause(FieldKey.fromParts("RowId"), matchIdList);

        // Keyword on some databases
        String percent = gs.getMatchesTable().getColumn("Percent").getSelectName();

        // Sum all the counts and the percentage coverage; calculate new average length
        SQLFragment sql = new SQLFragment("SELECT Analysis, SampleId, CAST(SUM(Reads) AS INT) AS reads, SUM(" + percent + ") AS " + percent + ", SUM(Reads * AverageLength) / SUM(Reads) AS avg_length, ");
        sql.append("CAST(SUM(PosReads) AS INT) AS pos_reads, CAST(SUM(NegReads) AS INT) AS neg_reads, CAST(SUM(PosExtReads) AS INT) AS pos_ext_reads, CAST(SUM(NegExtReads) AS INT) AS neg_ext_reads FROM ");
        sql.append(gs.getMatchesTable(), "matches");
        sql.append(" ");
        sql.append(matchFilter.getSQLFragment(gs.getSqlDialect()));
        sql.append(" GROUP BY Analysis, SampleId");

        DbScope scope = gs.getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            try (ResultSet rs = new SqlSelector(gs.getSchema(), sql).getResultSet())
            {
                rs.next();
                SimpleFilter readsFilter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("MatchId"), matchIdList));
                Integer[] readIds = new TableSelector(gs.getReadsJunctionTable(), PageFlowUtil.set("ReadId"), readsFilter, null).getArray(Integer.class);
                newMatchId = insertMatch(user, analysis, rs.getInt("SampleId"), rs, ArrayUtils.toPrimitive(readIds), alleleIds);
            }

            // Update ParentId column for all combined matches
            SQLFragment updateSql = new SQLFragment("UPDATE ");
            updateSql.append(gs.getMatchesTable().getSelectName());
            updateSql.append(" SET ParentId = ? ");
            updateSql.add(newMatchId);
            updateSql.append(matchFilter.getSQLFragment(gs.getSqlDialect()));

            int rows = new SqlExecutor(gs.getSchema()).execute(updateSql);

            if (rows != matchIds.length)
                throw new IllegalStateException("Incorrect number of ParentIds were updated");

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return newMatchId;
    }


    public int insertMatch(User user, GenotypingAnalysis analysis, int sampleId, ResultSet rs, int[] readIds, int[] alleleIds) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();

        Map<String, Object> row = new HashMap<>();
        row.put("Analysis", analysis.getRowId());
        row.put("SampleId", sampleId);
        row.put("Reads", rs.getInt("reads"));
        row.put("Percent", rs.getFloat("percent"));
        row.put("AverageLength", rs.getFloat("avg_length"));
        row.put("PosReads", rs.getInt("pos_reads"));
        row.put("NegReads", rs.getInt("neg_reads"));
        row.put("PosExtReads", rs.getInt("pos_ext_reads"));
        row.put("NegExtReads", rs.getInt("neg_ext_reads"));

        Map<String, Object> matchOut = Table.insert(user, gs.getMatchesTable(), row);

        int matchId = (Integer)matchOut.get("RowId");

        // Insert all the alleles in this group into AllelesJunction table
        if (alleleIds.length > 0)
        {
            Map<String, Object> alleleJunctionMap = new HashMap<>();  // Reuse for each allele
            alleleJunctionMap.put("Analysis", analysis.getRowId());
            alleleJunctionMap.put("MatchId", matchId);

            for (int alleleId : alleleIds)
            {
                alleleJunctionMap.put("SequenceId", alleleId);
                Table.insert(user, gs.getAllelesJunctionTable(), alleleJunctionMap);
            }
        }

        // Insert RowIds for all the reads underlying this match into ReadsJunction table
        if (readIds.length > 0)
        {
            Map<String, Object> readJunctionMap = new HashMap<>();   // Reuse for each read
            readJunctionMap.put("MatchId", matchId);

            for (int readId : readIds)
            {
                readJunctionMap.put("ReadId", readId);
                Table.insert(user, gs.getReadsJunctionTable(), readJunctionMap);
            }
        }

        return matchId;
    }

    public int deleteMatches(Container c, User user, int analysisId, List<Integer> matchIds) throws SQLException
    {
        // Validate analysis was posted and exists in this container
        GenotypingAnalysis analysis = GenotypingManager.get().getAnalysis(c, analysisId);

        // Verify that matches were posted
        if (matchIds.size() < 1)
            throw new IllegalStateException("No matches were selected");

        // Count the corresponding matches in the database, making sure they belong to this analysis
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Analysis"), analysis.getRowId());
        filter.addInClause(FieldKey.fromParts("RowId"), matchIds);
        TableInfo tinfo = GenotypingQuerySchema.TableType.Matches.createTable(new GenotypingQuerySchema(user, c), analysis.getRowId());
        TableSelector selector = new TableSelector(tinfo, tinfo.getColumns("RowId"), filter, null);

        // Verify that the selected match count equals the number of rowIds posted...
        if (selector.getRowCount() != matchIds.size())
            throw new IllegalStateException("Selected match" + (1 == matchIds.size() ? " has" : "es have") + " been modified");

        // Mark all the posted matches with ParentId = 0; this will filter them out from all displays and queries,
        // effectively "deleting" them. In the future, we could add a mode to show these matches again, to audit changes.
        GenotypingSchema gs = GenotypingSchema.get();
        Map<String, Integer> map = new HashMap<>();
        map.put("ParentId", 0);

        for (Integer matchId : matchIds)
            Table.update(user, gs.getMatchesTable(), map, matchId);

        return matchIds.size();
    }
}