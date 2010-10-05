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
