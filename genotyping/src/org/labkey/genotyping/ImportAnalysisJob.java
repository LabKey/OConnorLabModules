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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Results;
import org.labkey.api.data.Table;
import org.labkey.api.data.TempTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableWriter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.FieldKey;
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
    private GenotypingRun _run;

    public ImportAnalysisJob(ViewBackgroundInfo info, PipeRoot root, File pipelineDir, GenotypingAnalysis analysis)
    {
        super("Import Analysis", info, root);
        _dir = pipelineDir;
        _analysis = analysis;
        _run = GenotypingManager.get().getRun(getContainer(), _analysis.getRun());
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
            File sourceSamples = new File(_dir, GenotypingManager.SAMPLES_FILE_NAME);
            File sourceMatches = new File(_dir, GenotypingManager.MATCHES_FILE_NAME);

            GenotypingSchema gs = GenotypingSchema.get();
            DbSchema schema = gs.getSchema();

            TempTableInfo samples = null;
            TempTableInfo matches = null;

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

                    QueryContext ctx = new QueryContext(schema, samples, matches, gs.getReadsTable(), _analysis.getRun());
                    JspTemplate<QueryContext> jspQuery = new JspTemplate<QueryContext>("/org/labkey/genotyping/view/mhcQuery.jsp", ctx);
                    String sql = jspQuery.render();

                    setStatus("IMPORTING RESULTS");

                    info("Importing list of samples");

                    // We want to store sample RowIds with each match and in the AnalysisSamples table, but samples.txt
                    // does not include RowIds.  So, we "join" the samples temp table (names of all samples used) with
                    // the list of all samples in this library to provide the name -> rowId mapping.
                    Map<String, Integer> sampleKeys = new HashMap<String, Integer>();
                    ResultSet allSamples = null;

                    try
                    {
                        // Select sample names from the samples temp table -- these are the samples that were selected when submitting the analysis
                        String[] analysisSampleNames = Table.executeArray(samples, "sample", null, null, String.class);
                        Set<String> analysisSamples = PageFlowUtil.set(analysisSampleNames);

                        // Select name and rowId of all samples from this library -- we'll only use the selected ones
                        Results results = SampleManager.get().selectSamples(getContainer(), getUser(), _run, "library_sample_name, key");
                        allSamples = results.getResultSet();
                        Map<FieldKey, ColumnInfo> fieldMap = results.getFieldMap();

                        Map<String, Object> sampleMap = new HashMap<String, Object>();   // Map to reuse for each insertion to AnalysisSamples
                        sampleMap.put("analysis", _analysis.getRowId());

                        while (allSamples.next())
                        {
                            String sampleName = (String)fieldMap.get(FieldKey.fromString("library_sample_name")).getValue(allSamples);

                            if (analysisSamples.contains(sampleName))
                            {
                                int sampleId = (Integer)fieldMap.get(FieldKey.fromString("key")).getValue(allSamples);
                                sampleMap.put("sampleId", sampleId);
                                Table.insert(getUser(), gs.getAnalysisSamplesTable(), sampleMap);
                                sampleKeys.put(sampleName, sampleId);
                            }
                        }
                    }
                    finally
                    {
                        ResultSetUtil.close(allSamples);
                    }

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
                                    throw new NotFoundException("Allele name \"" + allele + "\" not found in reference sequences dictionary " +
                                            _analysis.getSequenceDictionary() + ", view \"" + _analysis.getSequencesView() + "\"");

                                alleleIds[i] = sequenceId;
                            }

                            GenotypingManager.get().insertMatch(getUser(), _analysis, sampleKeys.get(sampleId), rs, readIds, alleleIds);
                        }
                    }
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

            if (!GenotypingManager.get().updateAnalysisStatus(_analysis, getUser(), Status.Importing, Status.Complete))
                throw new IllegalStateException("Analysis status should be \"Importing\"");
            setStatus(COMPLETE_STATUS);
            info("Successfully imported genotyping analysis in " + DateUtil.formatDuration(System.currentTimeMillis() - startTime));
        }
        catch (Exception e)
        {
            error("Analysis import failed", e);
            setStatus(ERROR_STATUS);
        }
    }


    // columnNames: comma-separated list of column names to include; null means include all columns
    private TempTableInfo createTempTable(File file, DbSchema schema, @Nullable String columnNames) throws IOException, SQLException
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
