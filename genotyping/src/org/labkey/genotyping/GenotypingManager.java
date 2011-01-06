/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AtomicDatabaseInteger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

public class GenotypingManager
{
    private static final Logger LOG = Logger.getLogger(GenotypingManager.class);
    private static final GenotypingManager _instance = new GenotypingManager();

    public static final String PROPERTIES_FILE_NAME = "properties.xml";
    public static final String SAMPLES_FILE_NAME = "samples.txt";
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
}