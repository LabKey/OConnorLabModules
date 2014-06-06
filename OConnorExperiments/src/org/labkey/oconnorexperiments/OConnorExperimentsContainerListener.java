/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SimpleModuleContainerListener;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;

public class OConnorExperimentsContainerListener extends SimpleModuleContainerListener
{
    public OConnorExperimentsContainerListener(Module owner)
    {
        super(owner);
    }

    @Override
    public void containerCreated(Container c, User user)
    {
        super.containerCreated(c, user);

        if (c.isWorkbook())
        {
            if (c.getParent().getActiveModules().contains(ModuleLoader.getInstance().getModule(OConnorExperimentsModule.class)))
            {
                // Create an empty default wiki with title "Notes"
                ServiceRegistry.get(WikiService.class).insertWiki(user, c, "default", null, WikiRendererType.HTML, "Notes");

                // TODO: Inserting to Experiments table will insert to Workbooks table first, causing this listener to fire
                // TODO: and insert a new Experiment record and ultimately will make the Experiment table's DataIterator
                // TODO: fail to insert the Experimenet for the same container.
                //OConnorExperimentsManager.get().ensureExperiment(c, user);
            }
        }
    }
}
