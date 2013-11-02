/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.oconnorexperiments;

import org.labkey.api.files.FileContentService;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;

import java.util.Arrays;

/**
 * User: kevink
 * Date: 6/12/13
 */
public class OConnorExperimentFolderType extends DefaultFolderType
{
    public static final String NAME = "OConnorExperiment";

    public OConnorExperimentFolderType()
    {
        super(NAME,
                "An experiment containing files and notes",
                null,
                Arrays.asList(
                        Portal.getPortalPart("Experiment Field").createWebPart(),
                        createWikiWebPart(),
                        createFileWebPart()
                ),
                getDefaultModuleSet(ModuleLoader.getInstance().getCoreModule(), getModule("Experiment"), getModule(OConnorExperimentsModule.NAME)),
                ModuleLoader.getInstance().getCoreModule());
        setWorkbookType(true);
    }

    @Override
    public String getLabel()
    {
        return "Experiment";
    }

    private static Portal.WebPart createFileWebPart()
    {
        Portal.WebPart result = Portal.getPortalPart("Files").createWebPart(HttpView.BODY);
        result.setProperty("fileSet", FileContentService.PIPELINE_LINK);
        result.setProperty("webpart.title", "Files");
        return result;
    }

    private static Portal.WebPart createWikiWebPart()
    {
        Portal.WebPart result = Portal.getPortalPart("Wiki").createWebPart(HttpView.BODY);
        return result;
    }
}
