package org.labkey.oconnorexperiments.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ExtendedTable;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.oconnorexperiments.OConnorExperimentsSchema;

/**
 * User: kevink
 * Date: 5/17/13
 *
 * Adds experiments columns to core.Workbooks table.
 */
public class ExperimentsTable extends ExtendedTable<OConnorExperimentsUserSchema>
{
    public ExperimentsTable(String name, OConnorExperimentsUserSchema userSchema, SchemaTableInfo rootTable,
                            @NotNull TableInfo baseTable)
    {
        super(userSchema, rootTable, baseTable);
        setName(name);
    }

    @Override
    protected ColumnInfo getExtendedForeignKeyColumn()
    {
        return getColumn("Container");
    }

    @Override
    protected ColumnInfo getBaseLookupKeyColumn()
    {
        return getBaseTable().getColumn("EntityId");
    }

    protected ForeignKey createMergeForeignKey()
    {
        return new QueryForeignKey(getBaseTable(), "EntityId", null);
    }

    public void addColumns()
    {
        ColumnInfo containerCol = addWrapColumn(getRealTable().getColumn("Container"));
        containerCol.setFk(getExtendedForeignKey());
        containerCol.setHidden(true);

        ColumnInfo idCol = addBaseTableColumn("ID", "ID");
        idCol.setLabel("ID");
        idCol.setHidden(true);

        ColumnInfo expNumberCol = addBaseTableColumn("SortOrder", "ExperimentNumber");
        expNumberCol.setLabel("Experiment Number");
        expNumberCol.setReadOnly(true);

        ColumnInfo descriptionCol = addBaseTableColumn("Description", "Description");
        descriptionCol.setReadOnly(false);
        descriptionCol.setUserEditable(true);

        ColumnInfo modifiedByCol = addWrapColumn(getRealTable().getColumn("ModifiedBy"));
        UserIdQueryForeignKey.initColumn(getUserSchema().getUser(), getUserSchema().getContainer(), modifiedByCol, false);
        modifiedByCol.setLabel("Modified By");

        ColumnInfo modifiedCol = addWrapColumn(getRealTable().getColumn("Modified"));
        modifiedCol.setLabel("Modified");

        ColumnInfo experimentTypeCol = addWrapColumn(getRealTable().getColumn("ExperimentType"));
        experimentTypeCol.setLabel("Experiment Type");

        //ColumnInfo parentExperimentsCol = ;

        setTitleColumn("ExperimentNumber");

        // UNDONE: experiment specific details page
        ActionURL projectBeginURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
        setDetailsURL(new DetailsURL(projectBeginURL));

        // UNDONE: experiment specific insert page
        setInsertURL(DetailsURL.fromString("core/createWorkbook.view"));

        // UNDONE: experiment specific update page
        //setUpdateURL(DetailsURL.fromString("core/updateWorkbook.view"));

        //ActionURL containerDeleteURL = PageFlowUtil.urlProvider(ProjectUrls.class).del
        //setDeleteURL();
    }

    public static ExperimentsTable create(OConnorExperimentsUserSchema schema, String name)
    {
        UserSchema core = QueryService.get().getUserSchema(schema.getUser(), schema.getContainer(), SchemaKey.fromParts("core"));
        //TableInfo containers = core.getTable("Containers");
        TableInfo workbooks = core.getTable("Workbooks");

        SchemaTableInfo rootTable = OConnorExperimentsSchema.getInstance().createTableInfoExperiments();

        return new ExperimentsTable(name, schema, rootTable, workbooks);
    }
}
