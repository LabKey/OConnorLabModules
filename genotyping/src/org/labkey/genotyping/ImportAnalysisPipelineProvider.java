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

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.FileFilter;

/**
 * User: adam
 * Date: Sep 16, 2010
 * Time: 10:45:54 PM
 */
public class ImportAnalysisPipelineProvider extends PipelineProvider
{
    public ImportAnalysisPipelineProvider(Module owningModule)
    {
        super("Import Analysis", owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;

        File[] files = directory.listFiles(new ResultsFilter());

        String actionId = createActionId(GenotypingController.ImportAnalysisAction.class, null);
        addAction(actionId, GenotypingController.ImportAnalysisAction.class, "Import Analysis", directory, files, true, false, includeAll);
    }

    private static class ResultsFilter implements FileFilter
    {
        public boolean accept(File file)
        {
            return file.getName().equalsIgnoreCase(GenotypingManager.MATCHES_FILE_NAME);
        }
    }
}
