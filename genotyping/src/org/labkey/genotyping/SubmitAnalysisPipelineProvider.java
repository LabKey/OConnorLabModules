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
    public SubmitAnalysisPipelineProvider(Module owningModule)
    {
        super("Submit Analysis", owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
    }

/*

    TODO: Add a "Galaxy" button to the status details that links to the data library.  This will require providing some
          way to store/retrieve state associated with completed jobs (e.g., the Galaxy URL to call).

    @Override
    public List<StatusAction> addStatusActions()
    {
        return Collections.singletonList(new StatusAction("Galaxy"));  // TODO: Add only if job is complete
    }

    @Override
    public ActionURL handleStatusAction(ViewContext ctx, String name, PipelineStatusFile sf) throws HandlerException
    {
        return super.handleStatusAction(ctx, name, sf);
    }

 */
}
