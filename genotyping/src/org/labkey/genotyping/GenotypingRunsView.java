/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
import org.labkey.api.data.ActionButton;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.springframework.validation.Errors;

import java.util.Collections;
import java.util.List;

/**
 * User: adam
 * Date: Oct 19, 2010
 * Time: 4:05:02 PM
 */
public class GenotypingRunsView extends QueryView
{
    private final boolean _allowDelete;

    public static final WebPartFactory FACTORY = new BaseWebPartFactory("Sequencing Runs")
    {
        public WebPartView getWebPartView(@NotNull ViewContext ctx, @NotNull Portal.WebPart webPart)
        {
            WebPartView view = new GenotypingRunsView(ctx, null, "SequencingRuns", false);
            view.setTitle("Sequencing Runs");
            view.setTitleHref(GenotypingController.getRunsURL(ctx.getContainer()));
            return view;
        }

        @Override
        public List<String> getLegacyNames()
        {
            return Collections.singletonList("Genotyping Runs");
        }
    };

    public GenotypingRunsView(ViewContext ctx, Errors errors, String dataRegion, boolean allowDelete)
    {
        super(getUserSchema(ctx), getQuerySettings(ctx, dataRegion), errors);
        setShadeAlternatingRows(true);
        setShowDeleteButton(allowDelete);
        _allowDelete = allowDelete;
    }

    @Override
    protected boolean canDelete()
    {
        return _allowDelete;
    }

    @Override
    public ActionButton createDeleteButton()
    {
        ActionButton btnDelete = new ActionButton(GenotypingController.DeleteRunsAction.class, "Delete");
        btnDelete.setActionType(ActionButton.Action.POST);
        btnDelete.setRequiresSelection(true, "Are you sure you want to delete this run and its associated analyses?", "Are you sure you want to delete these ${selectedCount} runs and their associated analyses?");

        return btnDelete;
    }

    private static UserSchema getUserSchema(ViewContext ctx)
    {
        return new GenotypingQuerySchema(ctx.getUser(), ctx.getContainer());
    }

    private static QuerySettings getQuerySettings(ViewContext ctx, String dataRegion)
    {
        QuerySettings settings = new QuerySettings(ctx, dataRegion, GenotypingQuerySchema.TableType.Runs.toString());
        settings.setAllowChooseView(true);
        settings.getBaseSort().insertSortColumn("RowId");

        return settings;
    }
}
