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
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.genotyping.galaxy.GalaxyServer;
import org.labkey.genotyping.sequences.SequenceManager;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
public class GenotypingAnalysisJob extends PipelineJob
{
    private final File _dir;
    private final GenotypingRun _run;
    private final int _analysisIndex;
    private final String _sequencesViewName;
    private final File _analysisDir;

    public GenotypingAnalysisJob(ViewBackgroundInfo info, PipeRoot root, File reads, GenotypingRun run, int analysisIndex, String sequencesViewName)
    {
        super("Genotyping Analysis", info, root);
        _dir = reads.getParentFile();
        _run = run;
        _analysisIndex = analysisIndex;
        _sequencesViewName = sequencesViewName;

        _analysisDir = new File(_dir, "analysis_" + (_analysisIndex + 1));

        if (_analysisDir.exists())
            throw new IllegalArgumentException("Analysis directory already exists: " + _analysisDir.getPath());

        _analysisDir.mkdir();

        setLogFile(new File(_analysisDir, FileUtil.makeFileNameWithTimestamp("genotyping_analysis", "log")));
        info("Creating analysis directory: " + _analysisDir.getName());
    }


    @Override
    public ActionURL getStatusHref()
    {
        return new ActionURL(GenotypingController.BeginAction.class, getInfo().getContainer());
    }


    @Override
    public String getDescription()
    {
        return "Genotyping analysis";
    }


    @Override
    public void run()
    {
        try
        {
            // Do this first to ensure that the Galaxy server is configured properly and the user has set a web API key
            info("Verifying Galaxy configuration");
            GalaxyServer server = GalaxyServer.get(getContainer(), getUser());

            List<Integer> mids = writeSamples();
            writeReads(mids);
            updateAnalysisRecord();
            writeProperties();
            writeFasta();
            sendFilesToGalaxy(server);
        }
        catch (Exception e)
        {
            error("Genotyping analysis failed", e);
            setStatus(ERROR_STATUS);
            return;
        }

        info("Genotyping analysis job complete");
        setStatus(COMPLETE_STATUS);
    }


    private void updateAnalysisRecord()
    {
        //To change body of created methods use File | Settings | File Templates.
    }


    private void writeReads(List<Integer> mids) throws IOException, SQLException, ServletException
    {
        TableInfo ti = GenotypingSchema.get().getReadsTable();
        SimpleFilter filter = new SimpleFilter("run", _run.getRun());
        filter.addInClause("mid", mids);

        final ResultSet rs = Table.select(ti, ti.getColumns("name,mid,sequence,quality"), filter, null);

        // Need a custom writer since TSVGridWriter does not work in background threads
        TSVWriter writer = new TSVWriter() {
            @Override
            protected void write()
            {
                _pw.println("name\tmid\tsequence");

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
        Properties props = new Properties();
        props.put("url", GenotypingController.getWorkflowCompleteURL(getContainer(), _run.getRun(), _analysisDir).getURIString());
        props.put("dir", _analysisDir.getName());
        props.put("run", String.valueOf(_run.getRun()));

        // Tell Galaxy "workflow complete" task to write a file when the workflow is done.  In many dev mode configurations
        // the Galaxy server can't communicate via HTTP with the LabKey server, so we'll watch for this file as a backup plan.
        if (AppProps.getInstance().isDevMode())
            props.put("completeFilename", "analysis_complete.txt");

        File propXml = new File(_analysisDir, GenotypingManager.PROPERTIES_FILE_NAME);
        OutputStream os = new FileOutputStream(propXml);
        props.storeToXML(os, null);
        os.close();
    }


    private List<Integer> writeSamples() throws SQLException, ServletException, IOException
    {
        info("Writing sample file");
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

        File samplesFile = new File(_analysisDir, "samples.txt");
        writer.write(samplesFile);
        return mids;
    }


    private void writeFasta() throws SQLException, IOException
    {
        info("Writing FASTA file");
        File fastaFile = new File(_analysisDir, GenotypingManager.SEQUENCES_FILE_NAME);
        SequenceManager.get().writeFasta(getContainer(), getUser(), _sequencesViewName, fastaFile);
    }


    private void sendFilesToGalaxy(GalaxyServer server) throws IOException
    {
        info("Sending files to Galaxy");

        GalaxyServer.DataLibrary library = server.createLibrary(_dir.getName() + "_" + (_analysisIndex + 1), "Run " + _run, "A genotyping experiment");
        GalaxyServer.Folder root = library.getRootFolder();
        root.uploadFromImportDirectory(_analysisDir.getName(), "txt", null, false);
    }
}
