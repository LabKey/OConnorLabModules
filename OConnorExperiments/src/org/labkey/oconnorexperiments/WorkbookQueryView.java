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

import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
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
}
