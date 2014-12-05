/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableInfo;
import org.labkey.api.data.TempTableWriter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.genotyping.sequences.SequenceDictionary;
import org.labkey.genotyping.sequences.SequenceManager;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Sep 20, 2010
 * Time: 12:11:53 PM
 */
public class ImportAnalysisJob extends PipelineJob
{
    private File _dir;
    private GenotypingAnalysis _analysis;

    public ImportAnalysisJob(ViewBackgroundInfo info, PipeRoot root, File pipelineDir, GenotypingAnalysis analysis)
    {
        super("Import Analysis", info, root);
        _dir = pipelineDir;
        _analysis = analysis;
        setLogFile(new File(_dir, FileUtil.makeFileNameWithTimestamp("import_analysis", "log")));

        if (!_dir.exists())
            throw new IllegalArgumentException("Pipeline directory does not exist: " + _dir.getAbsolutePath());

        if (null == _analysis)
            throw new IllegalArgumentException("Analysis was not specified");
    }


    @Override
    public ActionURL getStatusHref()
    {
        return GenotypingController.getAnalysisURL(getContainer(), _analysis.getRowId());
    }


    @Override
    public String getDescription()
    {
        return "Import genotyping analysis " + _analysis.getRowId();
    }


    @Override
    public void run()
    {
        long startTime = System.currentTimeMillis();

        try
        {
            File sourceMatches = new File(_dir, GenotypingManager.MATCHES_FILE_NAME);
            GenotypingSchema gs = GenotypingSchema.get();
            DbSchema schema = gs.getSchema();
            TempTableInfo matches = null;

            try
            {
                setStatus("LOADING TEMP TABLES");
                info("Loading matches temp table");
                matches = createTempTable(sourceMatches, null);

                QueryContext ctx = new QueryContext(schema, matches, gs.getReadsTable(), _analysis.getRun());
                JspTemplate<QueryContext> jspQuery = new JspTemplate<>("/org/labkey/genotyping/view/mhcQuery.jsp", ctx);
                String sql = jspQuery.render();

                setStatus("IMPORTING RESULTS");

                info("Executing query to join results");
                info("Importing results");
                SequenceDictionary dictionary = SequenceManager.get().getSequenceDictionary(getContainer(), _analysis.getSequenceDictionary());
                final Map<String, Integer> sequences = SequenceManager.get().getSequences(getContainer(), getUser(), dictionary, _analysis.getSequencesView());

                new SqlSelector(schema, sql).forEach(new Selector.ForEachBlock<ResultSet>()
                {
                    @Override
                    public void exec(ResultSet rs) throws SQLException
                    {
                        Integer sampleId = (Integer)rs.getObject("sampleid");

                        if (null != sampleId)
                        {
                            // Compute array of read row ids
                            String readIdsString = rs.getString("ReadIds");
                            String[] readArray = readIdsString.split(",");
                            int[] readIds = new int[readArray.length];

                            for (int i = 0; i < readArray.length; i++)
                                readIds[i] = Integer.parseInt(readArray[i]);

                            // Compute array of allele row ids and verify each is in the reference sequence dictionary
                            String allelesString = rs.getString("alleles");
                            String[] alleles = allelesString.split(",");
                            int[] alleleIds = new int[alleles.length];

                            for (int i = 0; i < alleles.length; i++)
                            {
                                String allele = alleles[i];
                                Integer sequenceId = sequences.get(allele);

                                if (null == sequenceId)
                                {
                                    String view = _analysis.getSequencesView();
                                    throw new NotFoundException("Allele name \"" + allele + "\" not found in reference sequences dictionary " +
                                            _analysis.getSequenceDictionary() + ", view \"" + (null != view ? view : "<default>") + "\"");
                                }

                                alleleIds[i] = sequenceId;
                            }

                            GenotypingManager.get().insertMatch(getUser(), _analysis, sampleId, rs, readIds, alleleIds);
                        }
                    }
                });
            }
            finally
            {
                info("Deleting temporary tables");

                // Drop the temp table
                if (null != matches)
                    matches.delete();
            }

            // Attempt to fix #11654
            setStatus("UPDATING STATISTICS");
            info("Updating matches table statistics");
            TableInfo matchesTable = gs.getMatchesTable();
            matchesTable.getSchema().getSqlDialect().updateStatistics(matchesTable);

            if (!GenotypingManager.get().updateAnalysisStatus(_analysis, getUser(), Status.Importing, Status.Complete))
                throw new IllegalStateException("Analysis status should be \"Importing\"");
            setStatus(TaskStatus.complete);
            info("Successfully imported genotyping analysis in " + DateUtil.formatDuration(System.currentTimeMillis() - startTime));
        }
        catch (Exception e)
        {
            error("Analysis import failed", e);
            setStatus(TaskStatus.error);
        }
    }


    // columnNames: comma-separated list of column names to include; null means include all columns
    private TempTableInfo createTempTable(File file, @Nullable String columnNames) throws IOException, SQLException
    {
        try (TabLoader loader = new TabLoader(file, true))
        {
            // Load only the specified columns
            if (null != columnNames)
            {
                Set<String> includeNames = PageFlowUtil.set(columnNames.split(","));

                for (ColumnDescriptor descriptor : loader.getColumns())
                    descriptor.load = includeNames.contains(descriptor.name);
            }

            TempTableWriter ttw = new TempTableWriter(loader);
            return ttw.loadTempTable();
        }
    }

    public static class QueryContext
    {
        public final DbSchema schema;
        public final TableInfo matches;
        public final TableInfo reads;
        public final int run;

        private QueryContext(DbSchema schema, TableInfo matches, TableInfo reads, int run)
        {
            this.schema = schema;
            this.matches = matches;
            this.reads = reads;
            this.run = run;
        }
    }
}
