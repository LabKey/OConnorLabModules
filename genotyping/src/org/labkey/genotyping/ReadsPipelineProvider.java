/*
 * Copyright (c) 2015 LabKey Corporation
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

import java.io.File;
import java.io.FileFilter;

public class ReadsPipelineProvider extends PipelineProvider
{
    String _platform;
    FileFilter _readsFilter = null;

    public ReadsPipelineProvider(String name, Module owningModule, String platform)
    {
        super(name, owningModule);
        _platform = platform;
        _readsFilter = new SampleCSVFilter();
    }

    public ReadsPipelineProvider(String name, Module owningModule, String platform, FileFilter filter)
    {
        super(name, owningModule);
        _platform = platform;
        _readsFilter = filter;
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;

        ActionURL importURL = directory.cloneHref();
        importURL.setAction(GenotypingController.ImportReadsAction.class);
        importURL.addParameter("pipeline", true);    // Distinguish between manual pipeline submission and automated scripts
        importURL.addParameter("platform", _platform);

        String actionId = createActionId(GenotypingController.ImportReadsAction.class, _platform);
        addAction(actionId, importURL, getName(), directory, directory.listFiles(_readsFilter), false, false, includeAll);
    }

    private static class SampleCSVFilter implements FileFilter
    {
        public boolean accept(File file)
        {
            return "csv".equalsIgnoreCase(FileUtil.getExtension(file));
        }
    }
}
