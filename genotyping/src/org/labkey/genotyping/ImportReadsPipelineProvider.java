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
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.genotyping.GenotypingController.ImportReadsAction;

import java.io.File;
import java.io.FileFilter;

/**
 * User: adam
 * Date: Sep 10, 2010
 * Time: 8:42:36 PM
 */
public class ImportReadsPipelineProvider extends PipelineProvider
{
    public ImportReadsPipelineProvider(Module owningModule)
    {
        super("Import Reads", owningModule);
        setShowActionsIfModuleInactive(true);     // TODO: Make galaxy "active"
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        // Only admins can submit genotyping runs?
        if (!context.getContainer().hasPermission(context.getUser(), AdminPermission.class))
            return;

        String actionId = createActionId(ImportReadsAction.class, null);
        addAction(actionId, ImportReadsAction.class, "Import Reads", directory, directory.listFiles(new ReadsFilter()), false, false, includeAll);
    }

    private static class ReadsFilter implements FileFilter
    {
        public boolean accept(File file)
        {
            return file.getName().equalsIgnoreCase(GenotypingManager.READS_FILE_NAME);
        }
    }
}
