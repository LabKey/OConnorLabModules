/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
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
        return 18.10;
    }

    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(GenotypingWebPart.FACTORY, GenotypingRunsView.FACTORY, GenotypingAnalysesView.FACTORY);
    }

    protected void init()
    {
        addController("genotyping", GenotypingController.class);
    }

    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new GenotypingContainerListener());
        PipelineService.get().registerPipelineProvider(new Import454ReadsPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new ImportIlluminaReadsPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new SubmitAnalysisPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new ImportAnalysisPipelineProvider(this));
        PipelineService.get().registerPipelineProvider(new ImportPacBioReadsPipelineProvider(this));

        GenotypingQuerySchema.register(this);
        FolderTypeManager.get().registerFolderType(this, new GenotypingFolderType(this));
        AssayService.get().registerAssayProvider(new HaplotypeAssayProvider());
        ExperimentService.get().registerExperimentDataHandler(new HaplotypeDataHandler());

        SQLFragment containerFrag = new SQLFragment();
        containerFrag.append("SELECT r.Container FROM ");
        containerFrag.append(GenotypingSchema.get().getRunsTable(), "r");
        containerFrag.append(" WHERE r.RowId = ").append(TableUpdaterFileListener.TABLE_ALIAS).append(".Run");

        FileContentService.get().addFileListener(new TableUpdaterFileListener(GenotypingSchema.get().getRunsTable(), "Path", TableUpdaterFileListener.Type.filePath, "RowId"));
        FileContentService.get().addFileListener(new TableUpdaterFileListener(GenotypingSchema.get().getAnalysesTable(), "Path", TableUpdaterFileListener.Type.filePath, "RowId", containerFrag));
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        GenotypingManager gm = GenotypingManager.get();
        Collection<String> list = new LinkedList<>();

        int runCount = gm.getRunCount(c);

        if (runCount > 0)
        {
            long readCount = gm.getReadCount(c, null);
            list.add(StringUtilsLabKey.pluralize(runCount, "sequencing run") + " containing " + StringUtilsLabKey.pluralize(readCount, "read"));
        }

        int analysisCount = gm.getAnalysisCount(c, null);

        if (analysisCount > 0)
        {
            long matchCount = gm.getMatchCount(c, null);
            list.add(StringUtilsLabKey.pluralize(analysisCount, "genotyping analysis", "genotyping analyses") + " containing " + StringUtilsLabKey.pluralize(matchCount, "match", "matches"));
        }

        SequenceManager sm = SequenceManager.get();
        int dictionaryCount = sm.getDictionaryCount(c);

        if (dictionaryCount > 0)
        {
            long sequenceCount = sm.getSequenceCount(c);
            list.add(StringUtilsLabKey.pluralize(dictionaryCount, "dictionary", "dictionaries") + " containing " + StringUtilsLabKey.pluralize(sequenceCount, "reference sequence"));
        }

        return list;
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(GenotypingSchema.get().getSchemaName());
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return PageFlowUtil.set(HaplotypeDataHandler.TestCase.class, IlluminaFastqParser.DupeTestCase.class);
    }

    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        return PageFlowUtil.set(IlluminaFastqParser.HeaderTestCase.class);
    }
}
