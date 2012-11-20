/*
 * Copyright (c) 2012 LabKey Corporation
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

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

/**
 * User: jeckels
 * Date: 10/19/12
 */
public class HaplotypeProtocolSchema extends AssayProtocolSchema
{
    public static final String DUPLICATE_ASSIGNMENT_QUERY_NAME = "DuplicateAssignment";

    public HaplotypeProtocolSchema(User user, Container container, ExpProtocol protocol, Container targetStudy)
    {
        super(user, container, protocol, targetStudy);
    }

    @Override
    public FilteredTable createDataTable(boolean includeCopiedToStudyColumns)
    {
        FilteredTable table = (FilteredTable)new GenotypingQuerySchema(getUser(), getContainer()).getTable(GenotypingQuerySchema.TableType.AnimalAnalysis.name());
        table.getColumn("RunId").setFk(new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getTable(RUNS_TABLE_NAME);
            }
        });
        return table;
    }

    @Override
    protected RunListQueryView createRunsQueryView(ViewContext context, QuerySettings settings, BindException errors)
    {
        return new RunListQueryView(getProtocol(), context)
        {
            @Override
            public DataView createDataView()
            {
                DataView result = super.createDataView();
                ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(getContainer(), getProtocol(), GenotypingController.HaplotypeAssignmentReportAction.class);
                ActionButton reportButton = new ActionButton(url, "Produce Report");
                result.getDataRegion().getButtonBar(DataRegion.MODE_GRID).add(reportButton);
                return result;
            }
        };
    }

    @Override
    protected ResultsQueryView createDataQueryView(ViewContext context, QuerySettings settings, BindException errors)
    {
        return new ResultsQueryView(getProtocol(), context, settings)
        {
            @Override
            public DataView createDataView()
            {
                DataView result = super.createDataView();
                DataRegion rgn = result.getDataRegion();
                rgn.setRecordSelectorValueColumns("AnimalId");

                ActionButton reportButton = new ActionButton(GenotypingController.HaplotypeAssignmentReportAction.class, "Produce Report");
                result.getDataRegion().getButtonBar(DataRegion.MODE_GRID).add(reportButton);
                return result;
            }
        };
    }
}
