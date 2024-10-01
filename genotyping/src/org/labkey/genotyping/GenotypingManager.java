/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AtomicDatabaseInteger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.security.User;
import org.labkey.api.util.SafeToRenderEnum;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class GenotypingManager
{
    private static final GenotypingManager _instance = new GenotypingManager();

    public static final String PROPERTIES_FILE_NAME = "properties.xml";
    public static final String READS_FILE_NAME = "reads.txt";
    public static final String MATCHES_FILE_NAME = "matches.txt";
    public static final String SEQUENCES_FILE_NAME = "sequences.fasta";

    public enum SEQUENCE_PLATFORMS implements SafeToRenderEnum
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

    public enum Setting
    {
        ReferenceSequencesQuery("SequencesQuery", "the source of DNA reference sequences"),
        RunsQuery("RunsQuery", "run meta data"),
        SamplesQuery("SamplesQuery", "sample information"),
        HaplotypesQuery("HaplotypesQuery", "haplotype definitions");

        private final String _key;
        private final String _description;

        Setting(String key, String friendlyName)
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
        WritablePropertyMap map = PropertyManager.getWritableProperties(c, FOLDER_CATEGORY, true);
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
        MetaDataRun run = new TableSelector(qHelper.getTableInfo(null)).getObject(runId, MetaDataRun.class);

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
    public boolean updateAnalysisStatus(GenotypingAnalysis analysis, User user, Status expected, Status update)
    {
        assert (expected.getStatusId() + 1) == update.getStatusId();

        AtomicDatabaseInteger status = new AtomicDatabaseInteger(GenotypingSchema.get().getAnalysesTable().getColumn("Status"), null, analysis.getRowId());
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
            deleteRun(run);
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
    public void deleteRun(GenotypingRun run)
    {
        GenotypingSchema gs = GenotypingSchema.get();
        deleteAnalyses(" WHERE Run = ? AND Run IN (SELECT RowId FROM " + gs.getRunsTable() + " WHERE Container = ?)", run.getRowId(), run.getContainer());

        SqlExecutor executor = new SqlExecutor(gs.getSchema());
        executor.execute("DELETE FROM " + gs.getReadsTable() + " WHERE Run = ?", run.getRowId());
        executor.execute("DELETE FROM " + gs.getSequenceFilesTable() + " WHERE Run = ?", run.getRowId());
        executor.execute("DELETE FROM " + gs.getRunsTable() + " WHERE RowId = ?", run.getRowId());
    }


    public void deleteAnalysis(GenotypingAnalysis analysis)
    {
        GenotypingSchema gs = GenotypingSchema.get();
        deleteAnalyses(" WHERE RowId = ? AND Run IN (SELECT RowId FROM " + gs.getRunsTable() + " WHERE Container = ?)", analysis.getRowId(), analysis.getContainer());
    }


    private void deleteAnalyses(CharSequence analysisFilter, Object... params)
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

    public int deleteMatches(Container c, User user, int analysisId, List<Integer> matchIds)
    {
        // Validate analysis was posted and exists in this container
        GenotypingAnalysis analysis = GenotypingManager.get().getAnalysis(c, analysisId);

        // Verify that matches were posted
        if (matchIds.size() < 1)
            throw new IllegalStateException("No matches were selected");

        // Count the corresponding matches in the database, making sure they belong to this analysis
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Analysis"), analysis.getRowId());
        filter.addInClause(FieldKey.fromParts("RowId"), matchIds);
        TableInfo tinfo = GenotypingQuerySchema.TableType.Matches.createTable(new GenotypingQuerySchema(user, c), null, analysis.getRowId());
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