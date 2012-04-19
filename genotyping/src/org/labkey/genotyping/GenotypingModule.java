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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.Formats;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.genotyping.sequences.SequenceManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;

public class GenotypingModule extends DefaultModule
{
    public String getName()
    {
        return "Genotyping";
    }

    public double getVersion()
    {
        return 12.11;
    }

    public boolean hasScripts()
    {
        return true;
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(GenotypingWebPart.FACTORY, GenotypingRunsView.FACTORY, GenotypingAnalysesView.FACTORY);
    }

    protected void init()
    {
        addController("genotyping", GenotypingController.class);
    }

    public void startup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new GenotypingContainerListener());
        PipelineService.get().registerPipelineProvider(new Import454ReadsPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new ImportIlluminaReadsPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new SubmitAnalysisPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new ImportAnalysisPipelineProvider(this));
        GenotypingQuerySchema.register(this);
        ModuleLoader.getInstance().registerFolderType(this, new GenotypingFolderType(this));
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        GenotypingManager gm = GenotypingManager.get();
        Collection<String> list = new LinkedList<String>();

        int runCount = gm.getRunCount(c);

        if (runCount > 0)
        {
            long readCount = gm.getReadCount(c, null);
            list.add(pluralize(runCount, "sequencing run") + " containing " + pluralize(readCount, "read"));
        }

        int analysisCount = gm.getAnalysisCount(c, null);

        if (analysisCount > 0)
        {
            long matchCount = gm.getMatchCount(c, null);
            list.add(pluralize(analysisCount, "genotyping analysis", "genotyping analyses") + " containing " + pluralize(matchCount, "match", "matches"));
        }

        SequenceManager sm = SequenceManager.get();
        int dictionaryCount = sm.getDictionaryCount(c);

        if (dictionaryCount > 0)
        {
            long sequenceCount = sm.getSequenceCount(c);
            list.add(pluralize(dictionaryCount, "dictionary", "dictionaries") + " containing " + pluralize(sequenceCount, "reference sequence"));
        }

        return list;
    }

    private String pluralize(long count, String singular)
    {
        return pluralize(count, singular, singular + "s");
    }

    private String pluralize(long count, String singular, String plural)
    {
        return Formats.commaf0.format(count) + " " + (1 == count ? singular : plural);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(GenotypingSchema.get().getSchemaName());
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(GenotypingSchema.get().getSchema());
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new GenotypingUpgradeCode();
    }
}