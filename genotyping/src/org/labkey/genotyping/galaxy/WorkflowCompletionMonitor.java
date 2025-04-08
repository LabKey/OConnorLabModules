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

import org.apache.logging.log4j.Logger;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.genotyping.GenotypingManager;
import org.labkey.vfs.FileLike;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User: adam
 * Date: Sep 28, 2010
 * Time: 9:41:02 PM
 */
public class WorkflowCompletionMonitor implements ShutdownListener
{
    private static final Logger LOG = LogHelper.getLogger(WorkflowCompletionMonitor.class, "Logger for Genotyping workflow completion monitor");
    private static final WorkflowCompletionMonitor INSTANCE = new WorkflowCompletionMonitor();

    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Genotyping Workflow Completion Monitor"));
    private final List<FileLike> _pendingCompletionFiles = new CopyOnWriteArrayList<>();


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


    public void monitor(FileLike completionFile)
    {
        _pendingCompletionFiles.add(completionFile);
        LOG.info("Monitoring for {}", FileUtil.getAbsolutePath(completionFile.toNioPathForRead()));
    }


    @Override
    public String getName()
    {
        return "Genotyping workflow completion monitor";
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
                LOG.info("Checking for completion of {} analys{}", size, 1 == size ? "is" : "es");

                for (FileLike file : _pendingCompletionFiles)
                {
                    if (file.exists())
                    {
                        try
                        {
                            // Load analysis properties
                            Properties props = GenotypingManager.get().readProperties(file.getParent());

                            // POST to the provided URL to signal LabKey Server that the workflow is complete
                            String url = (String) props.get("url");
                            String analysisId = (String) props.get("analysis");
                            LOG.info("Detected completion file for analysis {}; attempting to signal LabKey Server at {}", analysisId, url);

                            HttpClient client = null;
                            try
                            {
                                client = HttpClient.newHttpClient();
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .POST(HttpRequest.BodyPublishers.noBody())
                                        .build();
                                BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
                                HttpResponse<String> response = client.send(request, bodyHandler);
                                String message = response.body();

                                LOG.info("LabKey response to analysis {} completion: \"{}\"", analysisId, message);
                            }
                            finally
                            {
                                // Unclear to me why this can't be used with the try-with-resources...
                                if (client != null)
                                    client.close();
                            }
                        }
                        catch (Throwable t)
                        {
                            LOG.error("Exception while completing {}", file.toNioPathForRead().toAbsolutePath(), t);
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
