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
package org.labkey.oconnorexperiments;

import org.labkey.api.data.ActionButton;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;

public class WorkbookQueryView extends QueryView
{
    public WorkbookQueryView(ViewContext ctx, UserSchema schema)
    {
        super(schema);

        QuerySettings settings = schema.getSettings(ctx, QueryView.DATAREGIONNAME_DEFAULT, OConnorExperimentsController.EXPERIMENTS);
        setSettings(settings);

        setShadeAlternatingRows(true);
        setShowBorders(true);
        setShowInsertNewButton(true);
        setShowImportDataButton(false);
        setShowDeleteButton(true);
        setFrame(FrameType.NONE);
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
        return view;
    }

    @Override
    public ActionButton createInsertMenuButton(ActionURL overrideInsertUrl, ActionURL overrideImportUrl)
    {
        ActionURL url = urlFor(QueryAction.insertQueryRow);
        if (url != null)
        {
            ActionButton button = new ActionButton(url, getInsertButtonText(INSERT_ROW_TEXT));
            button.setActionType(ActionButton.Action.POST);
            button.setTooltip(getInsertButtonText(INSERT_ROW_TEXT));
            button.setIconCls("plus");

            return button;
        }
        return null;
    }
}
