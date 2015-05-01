/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayResultsAction;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: 10/19/12
 */
public class HaplotypeProtocolSchema extends AssayProtocolSchema
{
    public static final String DUPLICATE_ASSIGNMENT_QUERY_NAME = "DuplicateAssignment";
    public static final String AGGREGATED_RESULTS_QUERY_NAME = "AggregatedAnalysisResults";
    private boolean _haplotypeDetailsTableInfoLoaded = false;
    private TableInfo _haplotypeDetailsTableInfo;

    public HaplotypeProtocolSchema(User user, Container container, @NotNull HaplotypeAssayProvider provider, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        super(user, container, provider, protocol, targetStudy);
    }

    private TableInfo getHaplotypeDetailsTableInfo()
    {
        if (!_haplotypeDetailsTableInfoLoaded)
        {
            String haplotypesQuery = new NonValidatingGenotypingFolderSettings(getContainer()).getHaplotypesQuery();

            if (haplotypesQuery != null)
            {
                final QueryHelper qHelper = new GenotypingQueryHelper(getContainer(), getUser(), haplotypesQuery);
                _haplotypeDetailsTableInfo = qHelper.getTableInfo();
            }
            _haplotypeDetailsTableInfoLoaded = true;
        }
        return _haplotypeDetailsTableInfo;
    }

    @Nullable
    @Override
    public TableInfo getTable(String name, boolean includeExtraMetadata)
    {
        TableInfo result = super.getTable(name, includeExtraMetadata);
        if (result != null && name.equalsIgnoreCase(AGGREGATED_RESULTS_QUERY_NAME))
        {
            ActionURL baseURL = new ActionURL(AssayResultsAction.class, getContainer());
            baseURL.addParameter("rowId", getProtocol().getRowId());
            result.getColumn("AnimalId").setURL(new DetailsURL(baseURL, Collections.singletonMap("Data.AnimalId/LabAnimalId~eq", "AnimalId/LabAnimalId")));
        }
        return result;
    }

    @Override
    public FilteredTable createDataTable(boolean includeCopiedToStudyColumns)
    {
        FilteredTable table = (FilteredTable)new GenotypingQuerySchema(getUser(), getContainer()).getTable(GenotypingQuerySchema.TableType.AnimalAnalysis.name());
        List<FieldKey> keys = new ArrayList<>(table.getDefaultVisibleColumns());
        HashSet<String> defaults = HaplotypeAssayProvider.getDefaultColumns();
        List<? extends DomainProperty> props = HaplotypeAssayProvider.getDomainProps(getProtocol());

        SQLFragment haplotypeSubselectSql = new SQLFragment("SELECT aha.AnimalAnalysisId, aha.DiploidNumber, h.Name AS Haplotype, h.Type FROM ");
        haplotypeSubselectSql.append(GenotypingSchema.get().getAnimalHaplotypeAssignmentTable(), "aha");
        haplotypeSubselectSql.append(" JOIN ");
        haplotypeSubselectSql.append(GenotypingSchema.get().getHaplotypeTable(), "h");
        haplotypeSubselectSql.append(" ON aha.HaplotypeId = h.RowId");

        SQLFragment runConditionSQL = new SQLFragment("RunId IN (SELECT RowId FROM " +
                ExperimentService.get().getTinfoExperimentRun() + " WHERE ProtocolLSID = ?)");
        runConditionSQL.add(getProtocol().getLSID());
        table.addCondition(runConditionSQL, FieldKey.fromParts("RunId"));

        for(DomainProperty prop : props)
        {
            String label = prop.getLabel() != null ? prop.getLabel() : ColumnInfo.labelFromName(prop.getName());
            if(!defaults.contains(prop.getName()) && !prop.isShownInInsertView() && (label.contains(" ")) && (label.endsWith("1") || label.endsWith("2")))
            {
                ExprColumn col = makeColumnFromRunField(prop, prop.getName().endsWith("2"), haplotypeSubselectSql, table);
                keys.add(FieldKey.fromParts(prop.getName()));
                if(table.getColumn(prop.getName()) == null)
                    table.addColumn(col);
            }
        }

        table.setDefaultVisibleColumns(keys);

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

    private ExprColumn makeColumnFromRunField(DomainProperty prop, boolean max, SQLFragment selectStatement, FilteredTable table){

        String field = prop.getName();
        String label = prop.getLabel() != null ? prop.getLabel() : ColumnInfo.labelFromName(prop.getName());
        String type = field.substring(0, prop.getName().length()-1).replaceAll("Haplotype", ""); //ColumnInfo.labelFromName(prop.getName()).split(" ")[0];

        SQLFragment sql = new SQLFragment("(SELECT ");
        sql.append("min");
        sql.append("(x.Haplotype) FROM (");
        sql.append(selectStatement);
        sql.append(") AS x WHERE x.DiploidNumber = ? AND x.Type = '" + type + "' AND x.AnimalAnalysisId = " + ExprColumn.STR_TABLE_ALIAS + ".RowID)");
        sql.add(max ? 2 : 1);
        ExprColumn column = new ExprColumn(table, field, sql, JdbcType.VARCHAR);
        TableInfo haplotypeDetailsTableInfo = getHaplotypeDetailsTableInfo();
        if (haplotypeDetailsTableInfo != null && haplotypeDetailsTableInfo.getGridURL(getContainer()) != null)
        {
            Map<String, FieldKey> params = new HashMap<>();
            params.put("query.haplotype~eq", column.getFieldKey());
            params.put("query.species~eq", new FieldKey(new FieldKey(new FieldKey(column.getFieldKey().getParent(), "AnimalId"), "SpeciesId"), "Name"));
            DetailsURL url = new DetailsURL(haplotypeDetailsTableInfo.getGridURL(getContainer()), params);
            column.setURL(url);
        }
        column.setLabel(label);
        return column;
    }

    @Nullable
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

                ButtonBar bar = result.getDataRegion().getButtonBar(DataRegion.MODE_GRID);
                if (!bar.isLocked())
                {
                    ActionButton reportButton = new ActionButton(GenotypingController.HaplotypeAssignmentReportAction.class, "Produce Report");
                    bar.add(reportButton);
                }

                return result;
            }
        };
    }
}
