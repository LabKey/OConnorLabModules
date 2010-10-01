package org.labkey.genotyping;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;

/**
 * User: adam
 * Date: Oct 1, 2010
 * Time: 10:05:35 AM
 */
public class SubmitAnalysisPipelineProvider extends PipelineProvider
{
    public SubmitAnalysisPipelineProvider(String name, Module owningModule)
    {
        super(name, owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
