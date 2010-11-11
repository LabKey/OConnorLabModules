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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.genotyping.galaxy.GalaxyServer;
import org.labkey.genotyping.galaxy.GalaxyUtils;
import org.labkey.genotyping.galaxy.WorkflowCompletionMonitor;
import org.labkey.genotyping.sequences.SequenceManager;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * User: adam
 * Date: Sep 10, 2010
 * Time: 9:43:21 PM
 */
public class SubmitAnalysisJob extends PipelineJob
{
    public final static Set<Integer> ALL_SAMPLES = new HashSet<Integer>(0);

    private final File _dir;
    private final GenotypingRun _run;
    private final GenotypingAnalysis _analysis;
    private final File _analysisDir;
    private final Set<Integer> _sampleKeys;

    private URLHelper _galaxyURL = null;
    private File _completionFile = null;   // Used for dev mode only

    // In dev mode only, we'll test the ability to connect to the Galaxy server once; if this connection fails, we'll
    // skip trying to submit to Galaxy on subsequence attempts.
    private static Boolean _useGalaxy = null;

    public SubmitAnalysisJob(ViewBackgroundInfo info, PipeRoot root, File reads, GenotypingRun run, GenotypingAnalysis analysis, @NotNull Set<Integer> sampleKeys) throws SQLException
    {
        super("Submit Analysis", info, root);      // No pipeline provider
        _dir = reads.getParentFile();
        _run = run;
        _analysis = analysis;
        _sampleKeys = sampleKeys;

        _analysisDir = new File(_dir, "analysis_" + _analysis.getRowId());

        if (_analysisDir.exists())
            throw new IllegalStateException("Analysis directory already exists: " + _analysisDir.getPath());

        if (!_analysisDir.mkdir())
            throw new IllegalStateException("Can't create analysis directory: " + _analysisDir.getPath());

        setLogFile(new File(_analysisDir, FileUtil.makeFileNameWithTimestamp("submit_analysis", "log")));
        info("Creating analysis directory: " + _analysisDir.getName());
        _analysis.setPath(_analysisDir.getAbsolutePath());
        _analysis.setFileName(_analysisDir.getName());
        Table.update(getUser(), GenotypingSchema.get().getAnalysesTable(), PageFlowUtil.map("Path", _analysis.getPath(), "FileName", _analysis.getFileName()), _analysis.getRowId());
    }


    @Override
    public URLHelper getStatusHref()
    {
        return _galaxyURL;
    }


    @Override
    public String getDescription()
    {
        return "Submit genotyping analysis " + _analysis.getRowId();
    }


    @Override
    public void run()
    {
        try
        {
            // Do this first to ensure that the Galaxy server is configured properly and the user has set a web API key
            info("Verifying Galaxy configuration");
            GalaxyServer server = GalaxyUtils.get(getContainer(), getUser());

            List<Integer> mids = writeSamples();
            writeReads(mids);
            writeProperties();
            writeFasta();
            sendFilesToGalaxy(server);
            monitorCompletion();
            assert GenotypingManager.get().updateAnalysisStatus(_analysis, getUser(), Status.NotSubmitted, Status.Submitted);
            info("Submitting genotyping analysis job complete");
            setStatus(COMPLETE_STATUS);
        }
        catch (Exception e)
        {
            error("Submitting genotyping analysis failed", e);
            setStatus(ERROR_STATUS);
        }
    }


    private List<Integer> writeSamples() throws SQLException, ServletException, IOException
    {
        info("Writing sample file");
        setStatus("WRITING SAMPLES");

        final Results results = SampleManager.get().selectSamples(getContainer(), getUser(), _run, "key, library_sample_name, library_sample_f_mid/mid_name, library_sample_f_mid/mid_sequence");
        final List<Integer> mids = new LinkedList<Integer>();

        // Need a custom writer since TSVGridWriter doesn't work in background threads
        TSVWriter writer = new TSVWriter() {
            @Override
            protected void write()
            {
                ResultSet rs = results.getResultSet();
                Map<FieldKey, ColumnInfo> fieldMap = results.getFieldMap();
                
                _pw.println("mid_sequence\tmid_num\tsample");

                try
                {
                    while (null != rs && rs.next())
                    {
                        int key = (Integer)fieldMap.get(FieldKey.fromString("key")).getValue(rs);

                        if (_sampleKeys == ALL_SAMPLES || _sampleKeys.contains(key))
                        {
                            int mid = (Integer)fieldMap.get(FieldKey.fromString("library_sample_f_mid/mid_name")).getValue(rs);
                            String sequence = (String)fieldMap.get(FieldKey.fromString("library_sample_f_mid/mid_sequence")).getValue(rs);
                            String sampleName = (String)fieldMap.get(FieldKey.fromString("library_sample_name")).getValue(rs);
                            _pw.println(sequence + "\t" + mid + "\t" + sampleName);
                            mids.add(mid);
                        }
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }
            }
        };

        File samplesFile = new File(_analysisDir, GenotypingManager.SAMPLES_FILE_NAME);
        writer.write(samplesFile);
        return mids;
    }


    private void writeReads(List<Integer> mids) throws IOException, SQLException, ServletException
    {
        info("Writing reads file");
        setStatus("WRITING READS");
        TableInfo ti = GenotypingSchema.get().getReadsTable();
        SimpleFilter filter = new SimpleFilter("run", _analysis.getRun());
        filter.addInClause("mid", mids);

        final ResultSet rs = Table.select(ti, ti.getColumns("name,mid,sequence,quality"), filter, null);

        // Need a custom writer since TSVGridWriter does not work in background threads
        TSVWriter writer = new TSVWriter() {
            @Override
            protected void write()
            {
                _pw.println("name\tmid\tsequence\tquality");

                try
                {
                    while (rs.next())
                    {
                        _pw.println(rs.getString(1) + "\t" + rs.getInt(2) + "\t" + rs.getString(3) + "\t" + rs.getString(4));
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }
            }
        };

        writer.write(new File(_analysisDir, "reads.txt"));
    }


    private void writeProperties() throws IOException
    {
        info("Writing properties file");
        setStatus("WRITING PROPERTIES");
        Properties props = new Properties();
        props.put("url", GenotypingController.getWorkflowCompleteURL(getContainer(), _analysis).getURIString());
        props.put("dir", _analysisDir.getName());
        props.put("analysis", String.valueOf(_analysis.getRowId()));
        props.put("user", getUser().getEmail());

        // Tell Galaxy "workflow complete" task to write a file when the workflow is done.  In many dev mode configurations
        // the Galaxy server can't communicate via HTTP with LabKey Server, so watch for this file as a backup plan.
        if (AppProps.getInstance().isDevMode())
        {
            _completionFile = new File(_analysisDir, "analysis_complete.txt");

            if (_completionFile.exists())
                throw new IllegalStateException("Completion file already exists: " + _completionFile.getPath());

            props.put("completionFilename", _completionFile.getName());
        }

        GenotypingManager.get().writeProperties(props, _analysisDir);
    }


    private void writeFasta() throws SQLException, IOException
    {
        info("Writing FASTA file");
        setStatus("WRITING FASTA");
        File fastaFile = new File(_analysisDir, GenotypingManager.SEQUENCES_FILE_NAME);
        SequenceManager.get().writeFasta(getContainer(), getUser(), _analysis.getSequencesView(), fastaFile);
    }


    private void sendFilesToGalaxy(GalaxyServer server) throws IOException, URISyntaxException
    {
        info("Sending files to Galaxy");
        setStatus("SENDING TO GALAXY");

        try
        {
            if (!shouldUseGalaxy(server))
                return;

            GalaxyServer.DataLibrary library = server.createLibrary(_dir.getName() + "_" + _analysis.getRowId(), "MHC analysis " + _analysis.getRowId() + " for run " + _analysis.getRun(), "An MHC genotyping analysis");
            GalaxyServer.Folder root = library.getRootFolder();
            root.uploadFromImportDirectory(_dir.getName() + "/" + _analysisDir.getName(), "txt", null, true);

            _galaxyURL = library.getURL();

            // Hack for testing without invoking the entire galaxy workflow: if it exists, link the matches.txt file
            // in /matches into the data library.
            if (AppProps.getInstance().isDevMode())
            {
                File matchesDir = new File(_dir, "matches");

                if (matchesDir.exists())
                {
                    File matchesFile = new File(matchesDir, GenotypingManager.MATCHES_FILE_NAME);

                    if (matchesFile.exists())
                        root.uploadFromImportDirectory(_dir.getName() + "/matches", "txt", null, true);
                }
            }
        }
        catch (IOException e)
        {
            // Fail the job in production mode, but succeed in dev mode.  This allows us to test in an environment
            // where Galaxy is not reachable.
            if (!AppProps.getInstance().isDevMode())
                throw e;

            info("Could not connect to Galaxy server", e);
        }
    }


    private synchronized boolean shouldUseGalaxy(GalaxyServer server)
    {
        // First time through
        if (null == _useGalaxy)
        {
            if (!AppProps.getInstance().isDevMode())
            {
                // In production mode, always try to connect to Galaxy server (even if failures occur).
                _useGalaxy = true;
            }
            else
            {
                // In dev mode, attempt a connection now and if it fails skip subsequent connections.
                _useGalaxy = server.canConnect();

                if (!_useGalaxy)
                    warn("Test connect to Galaxy server failed");
            }
        }
        else
        {
            if (!_useGalaxy)
                warn("Skipping submit to Galaxy server due to previous connection failure");
        }

        return _useGalaxy;
    }

    // Wait until analysis is completely prepared and has been submitted to Galaxy before monitoring
    private void monitorCompletion()
    {
        if (null != _completionFile)
            WorkflowCompletionMonitor.get().monitor(_completionFile);
    }
}
