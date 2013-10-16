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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileListener;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.wiki.WikiChangeListener;
import org.labkey.api.wiki.WikiService;
import org.labkey.oconnorexperiments.model.OConnorExperimentsManager;
import org.labkey.oconnorexperiments.query.OConnorExperimentsUserSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class OConnorExperimentsModule extends DefaultModule
{
    public static final String NAME = "OConnorExperiments";

    private OConnorWikiChangeListener wikiListener;
    private OConnorFileChangeListener fileListener;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 13.21;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
                new BaseWebPartFactory("Experiment Field")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        JspView view = new JspView("/org/labkey/oconnorexperiments/view/experimentWorkbookField.jsp");
                        view.setTitle("Workbook Description");
                        view.setFrame(WebPartView.FrameType.NONE);
                        return view;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return false;
                    }
                }
        ));
    }

    @Override
    protected void init()
    {
        addController("ocexp", OConnorExperimentsController.class);
        OConnorExperimentsUserSchema.register(this);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new OConnorExperimentsContainerListener(this));

        wikiListener = new OConnorWikiChangeListener();
        ServiceRegistry.get(WikiService.class).addWikiListener(wikiListener);

        fileListener = new OConnorFileChangeListener();
        ServiceRegistry.get(FileContentService.class).addFileListener(fileListener);

        ModuleLoader.getInstance().registerFolderType(this, new OConnorExperimentFolderType());
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("oconnorexperiments");
    }

    // Listener that just updates the experiment changed date when a wiki is modified.
    private class OConnorWikiChangeListener implements WikiChangeListener
    {
        @Override
        public void wikiCreated(User user, Container c, String name)
        {
            OConnorExperimentsManager.get().updateModified(c, user);
        }

        @Override
        public void wikiChanged(User user, Container c, String name)
        {
            OConnorExperimentsManager.get().updateModified(c, user);
        }

        @Override
        public void wikiDeleted(User user, Container c, String name)
        {
            OConnorExperimentsManager.get().updateModified(c, user);
        }
    }

    // Listener that just updates the experiment changed date when a file is uploaded/renamed.
    private class OConnorFileChangeListener implements FileListener
    {
        @Override
        public void fileCreated(@NotNull File created, @Nullable User user, @Nullable Container container)
        {
            OConnorExperimentsManager.get().updateModified(container, user);
        }

        @Override
        public void fileMoved(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container)
        {
            OConnorExperimentsManager.get().updateModified(container, user);
        }

        @Override
        public Collection<File> listFiles(@Nullable Container container)
        {
            return null;
        }

        @Override
        public String getSourceName()
        {
            return null;
        }

        @Override
        public SQLFragment listFilesQuery()
        {
            return null;
        }
    }
}
