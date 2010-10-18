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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableWriter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.genotyping.sequences.SequenceDictionary;
import org.labkey.genotyping.sequences.SequenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
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
        return new ActionURL(GenotypingController.BeginAction.class, getInfo().getContainer());
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
            // TODO: Move to controller
            GenotypingManager.get().updateAnalysisStatus(_analysis, getUser(), Status.Importing);

            File sourceSamples = new File(_dir, GenotypingManager.SAMPLES_FILE_NAME);
            File sourceMatches = new File(_dir, GenotypingManager.MATCHES_FILE_NAME);

            DbSchema schema = GenotypingSchema.get().getSchema();

            Table.TempTableInfo samples = null;
            Table.TempTableInfo matches = null;

            try
            {
                ResultSet rs = null;

                try
                {
                    setStatus("LOADING TEMP TABLES");
                    info("Loading samples temp table");
                    samples = createTempTable(sourceSamples, schema, "mid_num,sample");
                    info("Loading matches temp table");
                    matches = createTempTable(sourceMatches, schema, null);

                    QueryContext ctx = new QueryContext(schema, samples, matches, GenotypingSchema.get().getReadsTable(), _analysis.getRun());
                    JspTemplate<QueryContext> jspQuery = new JspTemplate<QueryContext>("/org/labkey/genotyping/view/mhcQuery.jsp", ctx);
                    String sql = jspQuery.render();
                    Map<String, Object> alleleJunctionMap = new HashMap<String, Object>(); // Map to reuse for each insertion to AllelesJunction
                    Map<String, Object> readJunctionMap = new HashMap<String, Object>();   // Map to reuse for each insertion to ReadsJunction

                    setStatus("IMPORTING RESULTS");
                    info("Executing query to join results");
                    rs = Table.executeQuery(schema, sql, null);
                    info("Importing results");
                    SequenceDictionary dictionary = SequenceManager.get().getSequenceDictionary(getContainer(), _analysis.getSequenceDictionary());
                    Map<String, Integer> sequences = SequenceManager.get().getSequences(getContainer(), getUser(), dictionary, _analysis.getSequencesView());

                    while (rs.next())
                    {
                        String sampleId = rs.getString("sample");

                        if (null != sampleId)
                        {
                            Map<String, Object> row = new HashMap<String, Object>();
                            row.put("Analysis", _analysis.getRowId());
                            row.put("SampleId", sampleId);
                            row.put("Reads", rs.getInt("reads"));
                            row.put("Percent", rs.getFloat("percent"));
                            row.put("AverageLength", rs.getFloat("avg_length"));
                            row.put("PosReads", rs.getInt("pos_reads"));
                            row.put("NegReads", rs.getInt("neg_reads"));
                            row.put("PosExtReads", rs.getInt("pos_ext_reads"));
                            row.put("NegExtReads", rs.getInt("neg_ext_reads"));

                            Map<String, Object> matchOut = Table.insert(getUser(), GenotypingSchema.get().getMatchesTable(), row);
                            int matchId = (Integer)matchOut.get("RowId");

                            // Insert all the alleles in this group into AllelesJunction table
                            String allelesString = rs.getString("alleles");
                            String[] alleles = allelesString.split(",");

                            if (alleles.length > 0)
                            {
                                alleleJunctionMap.put("MatchId", matchId);

                                for (String allele : alleles)
                                {
                                    Integer sequenceId = sequences.get(allele);

                                    if (null == sequenceId)
                                        throw new NotFoundException("Allele name \"" + allele + "\" not found in reference sequences dictionary " +
                                                _analysis.getSequenceDictionary() + ", view \"" + _analysis.getSequencesView() + "\"");

                                    alleleJunctionMap.put("SequenceId", sequenceId);
                                    Table.insert(getUser(), GenotypingSchema.get().getAllelesJunctionTable(), alleleJunctionMap);
                                }
                            }

                            // Insert RowIds for all the reads underlying this group into ReadsJunction table
                            String readIdsString = rs.getString("ReadIds");
                            String[] readIds = readIdsString.split(",");

                            if (readIds.length > 0)
                            {
                                readJunctionMap.put("MatchId", matchId);

                                for (String readId : readIds)
                                {
                                    readJunctionMap.put("ReadId", Integer.parseInt(readId));
                                    Table.insert(getUser(), GenotypingSchema.get().getReadsJunctionTable(), readJunctionMap);
                                }
                            }
                        }
                    }

                    GenotypingManager.get().updateAnalysisStatus(_analysis, getUser(), Status.Complete);
                    setStatus(COMPLETE_STATUS);
                    info("Successfully imported genotyping analysis in " + DateUtil.formatDuration(System.currentTimeMillis() - startTime));
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }
            }
            finally
            {
                info("Deleting temporary tables");

                // Drop the temp tables
                if (null != samples)
                    samples.delete();
                if (null != matches)
                    matches.delete();
            }
        }
        catch (Exception e)
        {
            error("Analysis import failed", e);
            setStatus(ERROR_STATUS);
        }
    }


    // columnNames: comma-separated list of column names to include; null means include all columns
    private Table.TempTableInfo createTempTable(File file, DbSchema schema, @Nullable String columnNames) throws IOException, SQLException
    {
        Reader reader = null;

        try
        {
            reader = new BufferedReader(new FileReader(file));
            TabLoader loader = new TabLoader(reader, true);         // TODO: Constructor that takes an inputstream

            // Load only the specified columns
            if (null != columnNames)
            {
                Set<String> includeNames = PageFlowUtil.set(columnNames.split(","));

                for (ColumnDescriptor descriptor : loader.getColumns())
                    descriptor.load = includeNames.contains(descriptor.name);
            }

            TempTableWriter ttw = new TempTableWriter(loader);
            return ttw.loadTempTable(schema);
        }
        finally
        {
            if (null != reader)
                reader.close();
        }
    }

    public static class QueryContext
    {
        public final DbSchema schema;
        public final TableInfo samples;
        public final TableInfo matches;
        public final TableInfo reads;
        public final int run;

        private QueryContext(DbSchema schema, TableInfo samples, TableInfo matches, TableInfo reads, int run)
        {
            this.schema = schema;
            this.samples = samples;
            this.matches = matches;
            this.reads = reads;
            this.run = run;
        }
    }
}
