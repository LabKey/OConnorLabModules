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
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.genotyping.GenotypingController.ImportReadsAction;

import java.io.File;
import java.io.FileFilter;

/**
 * User: bbimber
 * Date: Apr 19, 2012
 * Time: 8:42:36 PM
 */
public class ImportIlluminaReadsPipelineProvider extends PipelineProvider
{
    public ImportIlluminaReadsPipelineProvider(Module owningModule)
    {
        super("Import Illumina Reads", owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;

        ActionURL importURL = directory.cloneHref();
        importURL.setAction(ImportReadsAction.class);
        importURL.addParameter("pipeline", true);    // Distinguish between manual pipeline submission and automated scripts
        importURL.addParameter("platform", "ILLUMINA");

        String actionId = createActionId(ImportReadsAction.class, "Illumina");
        addAction(actionId, importURL, "Import Illumina Reads", directory, directory.listFiles(new SampleCSVFilter()), false, false, includeAll);
    }

    private static class SampleCSVFilter implements FileFilter
    {
        public boolean accept(File file)
        {
            return "csv".equalsIgnoreCase(FileUtil.getExtension(file));
        }
    }
}
