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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.genotyping.galaxy.GalaxyServer;
import org.labkey.genotyping.galaxy.GalaxyUtils;
import org.labkey.genotyping.galaxy.WorkflowCompletionMonitor;
import org.labkey.genotyping.sequences.SequenceManager;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
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
    private final File _dir;
    private final GenotypingAnalysis _analysis;
    private final File _analysisDir;
    private final Set<Integer> _sampleIds;

    private URLHelper _galaxyURL = null;
    private File _completionFile = null;   // Used for dev mode only

    // In dev mode only, we'll test the ability to connect to the Galaxy server once; if this connection fails, we'll
    // skip trying to submit to Galaxy on subsequent attempts (until server restart).
    private static Boolean _useGalaxy = null;

    public SubmitAnalysisJob(ViewBackgroundInfo info, PipeRoot root, File reads, GenotypingAnalysis analysis, @NotNull Set<Integer> sampleIds) throws SQLException
    {
        super("Submit Analysis", info, root);      // No pipeline provider
        _dir = reads.getParentFile();
        _analysis = analysis;
        _sampleIds = sampleIds;

        _analysisDir = new File(_dir, "analysis_" + _analysis.getRowId());

        if (_analysisDir.exists())
            throw new MinorConfigurationException("Analysis directory already exists: " + _analysisDir.getPath());

        if (!_analysisDir.mkdir())
            throw new MinorConfigurationException("Can't create analysis directory: " + _analysisDir.getPath());

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
            GalaxyServer server = null;

            try
            {
                server = GalaxyUtils.get(getContainer(), getUser());
            }
            catch (NotFoundException e)
            {
                warn("Can't submit to Galaxy server: " + e.getMessage());
            }

            writeAnalysisSamples();
            writeReads();
            writeProperties(server);
            writeFasta();
            sendFilesToGalaxy(server);
            monitorCompletion();
            if (!GenotypingManager.get().updateAnalysisStatus(_analysis, getUser(), Status.NotSubmitted, Status.Submitted))
                throw new IllegalStateException("Analysis status should be \"NotSubmitted\"");
            info("Submitting genotyping analysis job complete");
            setStatus(TaskStatus.complete);
        }
        catch (Exception e)
        {
            error("Submitting genotyping analysis failed", e);
            setStatus(TaskStatus.error);
        }
    }


    private void writeAnalysisSamples() throws SQLException
    {
        Map<String, Object> sampleMap = new HashMap<>();   // Map to reuse for each insertion to AnalysisSamples
        sampleMap.put("analysis", _analysis.getRowId());

        for (Integer sampleId : _sampleIds)
        {
            sampleMap.put("sampleId", sampleId);
            Table.insert(getUser(), GenotypingSchema.get().getAnalysisSamplesTable(), sampleMap);
        }
    }


    private void writeReads() throws IOException
    {
        info("Writing reads file");
        setStatus("WRITING READS");

        // Need a custom writer since TSVGridWriter does not work in background threads
        try (TSVWriter writer = new TSVWriter() {
            @Override
            protected void write()
            {
                _pw.println("name\tsample\tsequence\tquality");

                TableInfo ti = GenotypingSchema.get().getReadsTable();
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("run"), _analysis.getRun());
                filter.addInClause(FieldKey.fromParts("SampleId"), _sampleIds);

                new TableSelector(ti, ti.getColumns("name,sampleid,sequence,quality"), filter, null).forEach(new Selector.ForEachBlock<ResultSet>()
                {
                    @Override
                    public void exec(ResultSet rs) throws SQLException
                    {
                        _pw.println(rs.getString(1) + "\t" + rs.getInt(2) + "\t" + rs.getString(3) + "\t" + rs.getString(4));
                    }
                });
            }
        })
        {
            writer.write(new File(_analysisDir, "reads.txt"));
        }
    }


    private void writeProperties(@Nullable GalaxyServer server) throws IOException
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
        if (AppProps.getInstance().isDevMode() || null == server)
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
        if (!shouldUseGalaxy(server))
            return;

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


    private synchronized boolean shouldUseGalaxy(@Nullable GalaxyServer server)
    {
        // First time through
        if (null == _useGalaxy)
        {
            if (null == server)
            {
                // Galaxy is not configured, so don't use it
                _useGalaxy = false;
            }
            else if (!AppProps.getInstance().isDevMode())
            {
                // With a Galaxy configuration in production mode, always try to connect to Galaxy server (even if failures occur)
                _useGalaxy = true;
            }
            else
            {
                // In dev mode, attempt a connection now and if it fails skip subsequent connections
                _useGalaxy = server.canConnect();

                if (!_useGalaxy)
                    warn("Test connect to Galaxy server failed");
            }
        }
        else
        {
            if (!_useGalaxy && null != server)  // We already warned in the null case
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
