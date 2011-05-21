/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
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
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class GenotypingManager
{
    private static final Logger LOG = Logger.getLogger(GenotypingManager.class);
    private static final GenotypingManager _instance = new GenotypingManager();

    public static final String PROPERTIES_FILE_NAME = "properties.xml";
    public static final String READS_FILE_NAME = "reads.txt";
    public static final String MATCHES_FILE_NAME = "matches.txt";
    public static final String SEQUENCES_FILE_NAME = "sequences.fasta";

    private GenotypingManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GenotypingManager get()
    {
        return _instance;
    }

    private static final String FOLDER_CATEGORY = "GenotypingSettings";
    private static final String REFERENCE_SEQUENCES_QUERY = "SequencesQuery";
    private static final String RUNS_QUERY = "RunsQuery";
    private static final String SAMPLES_QUERY = "SamplesQuery";

    public void saveSettings(Container c, GenotypingFolderSettings settings)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(c.getId(), FOLDER_CATEGORY, true);
        map.put(REFERENCE_SEQUENCES_QUERY, settings.getSequencesQuery());
        map.put(RUNS_QUERY, settings.getRunsQuery());
        map.put(SAMPLES_QUERY, settings.getSamplesQuery());
        PropertyManager.saveProperties(map);
    }

    public GenotypingFolderSettings getSettings(final Container c)
    {
        return new GenotypingFolderSettings() {
            private final Map<String, String> map = PropertyManager.getProperties(c.getId(), FOLDER_CATEGORY);

            @Override
            public String getSequencesQuery()
            {
                return map.get(REFERENCE_SEQUENCES_QUERY);
            }

            @Override
            public String getRunsQuery()
            {
                return map.get(RUNS_QUERY);
            }

            @Override
            public String getSamplesQuery()
            {
                return map.get(SAMPLES_QUERY);
            }
        };
    }

    public GenotypingRun createRun(Container c, User user, int runId, @Nullable Integer metaDataId, File readsFile) throws SQLException
    {
        MetaDataRun mdRun = null;

        if (null != metaDataId)
            mdRun = getMetaDataRun(c, user, metaDataId);

        GenotypingRun run = new GenotypingRun(c, readsFile, runId, mdRun);
        return Table.insert(user, GenotypingSchema.get().getRunsTable(), run);
    }

    public @Nullable GenotypingRun getRun(Container c, int runId)
    {
        return Table.selectObject(GenotypingSchema.get().getRunsTable(), c, runId, GenotypingRun.class);
    }

    public MetaDataRun getMetaDataRun(Container c, User user, int runId)
    {
        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);
        QueryHelper qHelper = new QueryHelper(c, user, settings.getRunsQuery());
        MetaDataRun run = Table.selectObject(qHelper.getTableInfo(), runId, MetaDataRun.class);
        run.setContainer(c);

        return run;
    }

    public GenotypingAnalysis createAnalysis(Container c, User user, GenotypingRun run, @Nullable String description, @Nullable String sequencesViewName) throws SQLException
    {
        return Table.insert(user, GenotypingSchema.get().getAnalysesTable(), new GenotypingAnalysis(c, run, description, sequencesViewName));
    }

    public @NotNull GenotypingAnalysis getAnalysis(Container c, Integer analysisId)
    {
        if (null == analysisId)
            throw new NotFoundException("Analysis parameter is missing");

        GenotypingAnalysis analysis = Table.selectObject(GenotypingSchema.get().getAnalysesTable(), analysisId, GenotypingAnalysis.class);

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


    // Multiple threads could attempt to set the status at roughly the same time.  (For example, there are several ways to
    // initiate an analysis import: signal from Galaxy, pipeline ui, script, etc.)  Use an AtomicDatabaseInteger to
    // synchronously set the status.  Returns true if status was changed, false if it wasn't.
    public boolean updateAnalysisStatus(GenotypingAnalysis analysis, User user, Status expected, Status update) throws SQLException
    {
        assert (expected.getStatusId() + 1) == update.getStatusId();

        AtomicDatabaseInteger status = new AtomicDatabaseInteger(GenotypingSchema.get().getAnalysesTable().getColumn("Status"), user, null, analysis.getRowId());
        return status.compareAndSet(expected.getStatusId(), update.getStatusId());
    }


    // Deletes all the reads, analyses, and matches associated with a run, including rows in all junction tables.
    public void deleteRun(GenotypingRun run) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();
        deleteAnalyses(" WHERE Run = ? AND Run IN (SELECT RowId FROM " + gs.getRunsTable() + " WHERE Container = ?)", run.getRowId(), run.getContainer());

        Table.execute(gs.getSchema(), "DELETE FROM " + gs.getReadsTable() + " WHERE Run = ?", new Object[]{run.getRowId()});
        Table.execute(gs.getSchema(), "DELETE FROM " + gs.getRunsTable() + " WHERE RowId = ?", new Object[]{run.getRowId()});
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

        Table.execute(gs.getSchema(), "DELETE FROM " + gs.getAllelesJunctionTable() + matchesWhere, params);
        Table.execute(gs.getSchema(), "DELETE FROM " + gs.getReadsJunctionTable() + matchesWhere, params);
        Table.execute(gs.getSchema(), "DELETE FROM " + gs.getMatchesTable() + analysisWhere, params);
        Table.execute(gs.getSchema(), "DELETE FROM " + gs.getAnalysisSamplesTable() + analysisWhere, params);
        Table.execute(gs.getSchema(), "DELETE " + analysisFrom, params);
    }


    public void writeProperties(Properties props, File directory) throws IOException
    {
        File propXml = new File(directory, PROPERTIES_FILE_NAME);
        OutputStream os = new FileOutputStream(propXml);
        props.storeToXML(os, null);
        os.close();
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
        InputStream is = new FileInputStream(properties);
        props.loadFromXML(is);
        is.close();

        return props;
    }


    // Return number of runs in the specified container
    public int getRunCount(Container c) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();
        TableInfo runs = gs.getRunsTable();

        SQLFragment sql = new SQLFragment("SELECT CAST(COUNT(*) AS INT) FROM " + runs + " WHERE Container = ?", c);
        return Table.executeSingleton(runs.getSchema(), sql.getSQL(), sql.getParamsArray(), Integer.class);
    }


    public boolean hasAnalyses(GenotypingRun run) throws SQLException
    {
        return getAnalysisCount(run.getContainer(), run) > 0;
    }


    // Return number of analysis... associated with the specified run (run != null) or in the folder (run == null)
    public int getAnalysisCount(Container c, @Nullable GenotypingRun run) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();
        TableInfo analyses = gs.getAnalysesTable();
        TableInfo runs = gs.getRunsTable();

        SQLFragment sql = new SQLFragment("SELECT CAST(COUNT(*) AS INT) FROM " + analyses + " WHERE Run IN (SELECT RowId FROM " + runs + " WHERE Container = ?)", c);

        if (null != run)
        {
            sql.append(" AND Run = ?");
            sql.add(run.getRowId());
        }

        return Table.executeSingleton(analyses.getSchema(), sql.getSQL(), sql.getParamsArray(), Integer.class);
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
            throw new IllegalStateException("No matches were selected.");

        // Verify that alleles were posted
        if (alleleIdList.size() < 1)
            throw new IllegalStateException("No alleles were selected.");

        Results results = null;

        // Validate the matches
        try
        {
            // Count the corresponding matches in the database, making sure they belong to this analysis
            SimpleFilter filter = new SimpleFilter("Analysis", analysis.getRowId());
            filter.addInClause("RowId", matchIdList);
            TableInfo tinfo = GenotypingQuerySchema.TableType.Matches.createTable(c, user);
            results = QueryService.get().select(tinfo, tinfo.getColumns("SampleId"), filter, null);
            Set<Integer> sampleIds = new HashSet<Integer>();
            int matchCount = 0;

            // Stash the sampled ids and count the matches
            while (results.next())
            {
                sampleIds.add(results.getInt("SampleId"));
                matchCount++;
            }

            // Verify that the selected match count equals the number of rowIds posted...
            if (matchCount != matchIdList.size())
                throw new IllegalStateException("Queried matches differ from selected matches.");

            // Verify all matches are from the same sample
            if (sampleIds.size() != 1)
                throw new IllegalStateException("Queried matches differ from selected matches.");
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
        try
        {
            // Select all the alleles associated with these matches
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause("MatchId", matchIdList);
            TableInfo tinfo = gs.getAllelesJunctionTable();
            Integer[] mAlleles = Table.executeArray(tinfo, "SequenceId", filter, null, Integer.class);
            Set<Integer> matchAlleles = new HashSet<Integer>(Arrays.asList(mAlleles));

            if (!matchAlleles.containsAll(alleleIdList))
                throw new IllegalStateException("Selected alleles aren't owned by the selected matches.");
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        // ======== End validation ========

        Integer newMatchId;

        // Now update the tables: create the new match, insert new rows in the alleles & reads junction tables, and mark the old matches

        // Group all the matches based on analysis and rowIds
        SimpleFilter matchFilter = new SimpleFilter("Analysis", analysis.getRowId());
        matchFilter.addInClause("RowId", matchIdList);

        // Sum all the counts and the percentage coverage; calculate new average length
        SQLFragment sql = new SQLFragment("SELECT Analysis, SampleId, CAST(SUM(Reads) AS INT) AS reads, SUM(Percent) AS percent, SUM(Reads * AverageLength) / SUM(Reads) AS avg_length, ");
        sql.append("CAST(SUM(PosReads) AS INT) AS pos_reads, CAST(SUM(NegReads) AS INT) AS neg_reads, CAST(SUM(PosExtReads) AS INT) AS pos_ext_reads, CAST(SUM(NegExtReads) AS INT) AS neg_ext_reads FROM ");
        sql.append(gs.getMatchesTable(), "matches");
        sql.append(" ");
        sql.append(matchFilter.getSQLFragment(gs.getSqlDialect()));
        sql.append(" GROUP BY Analysis, SampleId");

        DbScope scope = gs.getSchema().getScope();
        ResultSet rs = null;

        try
        {
            scope.ensureTransaction();

            rs = Table.executeQuery(gs.getSchema(), sql);
            rs.next();
            SimpleFilter readsFilter = new SimpleFilter(new SimpleFilter.InClause("MatchId", matchIdList));
            Integer[] readIds = Table.executeArray(gs.getReadsJunctionTable(), "ReadId", readsFilter, null, Integer.class);
            newMatchId = insertMatch(user, analysis, rs.getInt("SampleId"), rs, ArrayUtils.toPrimitive(readIds), alleleIds);

            // Update ParentId column for all combined matches
            SQLFragment updateSql = new SQLFragment("UPDATE ");
            updateSql.append(gs.getMatchesTable(), "matches");
            updateSql.append(" SET ParentId = ? ");
            updateSql.add(newMatchId);
            updateSql.append(matchFilter.getSQLFragment(gs.getSqlDialect()));

            int rows = Table.execute(gs.getSchema(), updateSql);

            if (rows != matchIds.length)
                throw new IllegalStateException("Incorrect number of ParentIds were updated");

            scope.commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ResultSetUtil.close(rs);
            scope.closeConnection();
        }

        return newMatchId;
    }


    public int insertMatch(User user, GenotypingAnalysis analysis, int sampleId, ResultSet rs, int[] readIds, int[] alleleIds) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();

        Map<String, Object> row = new HashMap<String, Object>();
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
            Map<String, Object> alleleJunctionMap = new HashMap<String, Object>();  // Reuse for each allele
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
            Map<String, Object> readJunctionMap = new HashMap<String, Object>();   // Reuse for each read
            readJunctionMap.put("MatchId", matchId);

            for (int readId : readIds)
            {
                readJunctionMap.put("ReadId", readId);
                Table.insert(user, gs.getReadsJunctionTable(), readJunctionMap);
            }
        }

        return matchId;
    }
}