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

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * User: adam
 * Date: Sep 10, 2010
 * Time: 9:43:21 PM
 */
public class SubmitAnalysisJob extends PipelineJob
{
    private final File _dir;
    private final GenotypingRun _run;
    private final GenotypingAnalysis _analysis;
    private final File _analysisDir;

    private File _completionFile = null;   // Used for dev mode only
    private URLHelper _galaxyURL = null;

    public SubmitAnalysisJob(ViewBackgroundInfo info, PipeRoot root, File reads, GenotypingRun run, GenotypingAnalysis analysis) throws SQLException
    {
        super("Submit Analysis", info, root);      // No pipeline provider
        _dir = reads.getParentFile();
        _run = run;
        _analysis = analysis;

        _analysisDir = new File(_dir, "analysis_" + _analysis.getRowId());

        if (_analysisDir.exists())
            throw new IllegalArgumentException("Analysis directory already exists: " + _analysisDir.getPath());

        _analysisDir.mkdir();

        setLogFile(new File(_analysisDir, FileUtil.makeFileNameWithTimestamp("submit_analysis", "log")));
        info("Creating analysis directory: " + _analysisDir.getName());
        _analysis.setPath(_analysisDir.getAbsolutePath());
        Table.update(getUser(), GenotypingSchema.get().getAnalysesTable(), PageFlowUtil.map("path", _analysis.getPath()), _analysis.getRowId());
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
            updateAnalysisRecord();
            writeProperties();
            writeFasta();
            sendFilesToGalaxy(server);
            monitorCompletion();
        }
        catch (Exception e)
        {
            error("Submitting genotyping analysis failed", e);
            setStatus(ERROR_STATUS);
            return;
        }

        info("Submitting genotyping analysis job complete");
        setStatus(COMPLETE_STATUS);
    }


    private void updateAnalysisRecord()
    {
        //To change body of created methods use File | Settings | File Templates.
    }


    private List<Integer> writeSamples() throws SQLException, ServletException, IOException
    {
        info("Writing sample file");
        setStatus("WRITING SAMPLES");
        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(getContainer());
        QueryHelper qHelper = new QueryHelper(getContainer(), getUser(), settings.getSamplesQuery());
        SimpleFilter extraFilter = new SimpleFilter("library_number", _run.getSampleLibrary());

        final ResultSet rs = qHelper.select(extraFilter);
        final List<Integer> mids = new LinkedList<Integer>();

        // Need a custom writer since TSVGridWriter does not work in background threads
        TSVWriter writer = new TSVWriter() {
            @Override
            protected void write()
            {
                _pw.println("mid_sequence\tmid_num\tsample");

                try
                {
                    while (rs.next())
                    {
                        int mid = rs.getInt(2);
                        _pw.println(rs.getString(1) + "\t" + mid + "\t" + rs.getString(3));
                        mids.add(mid);
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


    // Wait until analysis is completely prepared and has been submitted to Galaxy before monitoring
    private void monitorCompletion()
    {
        if (null != _completionFile)
            WorkflowCompletionMonitor.get().monitor(_completionFile);
    }
}
