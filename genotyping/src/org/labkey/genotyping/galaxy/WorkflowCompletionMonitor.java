/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
package org.labkey.genotyping.galaxy;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ShutdownListener;
import org.labkey.genotyping.GenotypingManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * User: adam
 * Date: Sep 28, 2010
 * Time: 9:41:02 PM
 */
public class WorkflowCompletionMonitor implements ShutdownListener
{
    private static final Logger LOG = Logger.getLogger(WorkflowCompletionMonitor.class);
    private static final WorkflowCompletionMonitor INSTANCE = new WorkflowCompletionMonitor();

    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(@NotNull Runnable r)
        {
            return new Thread(r, "Genotyping Workflow Completion Monitor");
        }
    });
    private final List<File> _pendingCompletionFiles = new CopyOnWriteArrayList<>();


    static
    {
        ContextListener.addShutdownListener(INSTANCE);
    }


    /*
        This gets used only if a genotyping analysis gets submitted to Galaxy while the server is in dev mode. Most
        configurations don't support an external Galaxy server pinging back to a developer's LabKey Server, so we signal
        workflow completion via a file in the analysis directory. Steps:

        - SubmitAnalysisJob adds a "completeFilename" property to properties.xml
        - SubmitAnalysisJob calls monitor() below cause WorkflowCompletionMonitor to watch for the specified file, checking
          every 15 seconds for the existence of any pending files.
        - When Galaxy workflow is complete, the workflow_complete task runs.  If "completeFilename" is set in properties.xml
          the task creates the specified file.
        - The CheckForWorkflowCompletionsRunnable detects the file and signals the LabKey Server by invoking the same URL
          the Galaxy workflow_complete task would have pinged; this provides a good test of the HTTP signaling mechanism.
    */

    public static WorkflowCompletionMonitor get()
    {
        return INSTANCE;
    }


    private WorkflowCompletionMonitor()
    {
        // Check for workflow complete files every 15 seconds
        _executor.scheduleWithFixedDelay(new WorkflowCompletionMonitor.CheckForWorkflowCompletionsRunnable(), 15, 15, TimeUnit.SECONDS);
    }


    public void monitor(File completionFile)
    {
        _pendingCompletionFiles.add(completionFile);
        LOG.info("Monitoring for " + completionFile.getAbsolutePath());
    }


    @Override
    public String getName()
    {
        return "Genotyping workflow completion monitor";
    }

    @Override
    public void shutdownPre()
    {
    }


    @Override
    public void shutdownStarted()
    {
        _executor.shutdown();
    }


    private class CheckForWorkflowCompletionsRunnable implements Runnable
    {
        @Override
        public void run()
        {
            int size = _pendingCompletionFiles.size();

            if (size > 0)
            {
                LOG.info("Checking for completion of " +  size + " analys" + (1 == size ? "is" : "es"));

                for (File file : _pendingCompletionFiles)
                {
                    if (file.exists())
                    {
                        try
                        {
                            // Load analysis properties
                            Properties props = GenotypingManager.get().readProperties(file.getParentFile());

                            // GET the provided URL to signal LabKey Server that the workflow is complete
                            String url = (String)props.get("url");
                            String analysisId = (String)props.get("analysis");
                            LOG.info("Detected completion file for analysis " + analysisId + "; attempting to signal LabKey Server at " + url);
                            InputStream is = (InputStream)new URL(url).getContent();
                            BufferedReader r = new BufferedReader(new InputStreamReader(is));
                            LOG.info("LabKey response to analysis " + analysisId + " completion: \"" + r.readLine() + "\"");
                            r.close();
                            is.close();
                        }
                        catch (Throwable t)
                        {
                            LOG.error("Exception while completing " + file.getAbsolutePath(), t);
                        }
                        finally
                        {
                            _pendingCompletionFiles.remove(file);
                        }
                    }
                }
            }
        }
    }
}
