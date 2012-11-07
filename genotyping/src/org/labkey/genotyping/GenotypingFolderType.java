/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.view.Portal;

import java.util.Arrays;

/**
 * User: adam
 * Date: Oct 6, 2010
 * Time: 1:52:59 PM
 */
public class GenotypingFolderType extends MultiPortalFolderType
{
    public GenotypingFolderType(GenotypingModule module)
    {
        super("Genotyping",
                "Manage importing and analyzing next generation sequencing runs.",
            Arrays.asList(
                GenotypingWebPart.FACTORY.createWebPart()
            ),
            Arrays.asList(
                Portal.getPortalPart("Lists").createWebPart(),
                GenotypingRunsView.FACTORY.createWebPart()
            ),
            getDefaultModuleSet(module, getModule("Pipeline")),
            module);
    }
}
