package org.labkey.oconnorexperiments.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ExtendedTable;
import org.labkey.api.data.Filter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.ListofMapsDataIterator;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.oconnorexperiments.OConnorExperimentsController;
import org.labkey.oconnorexperiments.OConnorExperimentsSchema;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    public static ExperimentsTable create(OConnorExperimentsUserSchema schema, String name)
    {
        UserSchema core = QueryService.get().getUserSchema(schema.getUser(), schema.getContainer(), SchemaKey.fromParts("core"));
        TableInfo workbooks = core.getTable("Workbooks");

        SchemaTableInfo rootTable = OConnorExperimentsSchema.getInstance().createTableInfoExperiments();

        return new ExperimentsTable(name, schema, rootTable, workbooks).init();
    }

    @Override
    public ExperimentsTable init()
    {
        return (ExperimentsTable)super.init();
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
        expNumberCol.setShownInInsertView(false);

        ColumnInfo descriptionCol = addBaseTableColumn("Description", "Description");
        descriptionCol.setDescription("Summary information about the experiment");
        descriptionCol.setReadOnly(false);
        descriptionCol.setUserEditable(true);

        ColumnInfo createdByCol = addBaseTableColumn("CreatedBy", "CreatedBy");
        UserIdQueryForeignKey.initColumn(getUserSchema().getUser(), getUserSchema().getContainer(), createdByCol, false);
        createdByCol.setLabel("Created By");

        ColumnInfo createdCol = addBaseTableColumn("Created", "Created");
        createdCol.setLabel("Created");

        ColumnInfo modifiedByCol = addWrapColumn(getRealTable().getColumn("ModifiedBy"));
        UserIdQueryForeignKey.initColumn(getUserSchema().getUser(), getUserSchema().getContainer(), modifiedByCol, false);
        modifiedByCol.setLabel("Modified By");

        ColumnInfo modifiedCol = addWrapColumn(getRealTable().getColumn("Modified"));
        modifiedCol.setLabel("Modified");

        ColumnInfo experimentTypeCol = addWrapColumn(getRealTable().getColumn("ExperimentType"));
        experimentTypeCol.setLabel("Experiment Type");
        experimentTypeCol.setUserEditable(true);

        ColumnInfo parentExperimentsCol = wrapColumn("ParentExperiments", getRealTable().getColumn("Container"));
        MultiValuedForeignKey parentExperimentsFk = new MultiValuedForeignKey(
                new QueryForeignKey(getUserSchema(), OConnorExperimentsUserSchema.Table.ParentExperiments.name(), "Container", null),
                "ParentExperiment");
        parentExperimentsCol.setFk(parentExperimentsFk);
        parentExperimentsCol.setLabel("Parent Experiments");
        parentExperimentsCol.setUserEditable(true);
        parentExperimentsCol.setNullable(true);
        // BUGBUG: ETL doesn't like using the same PropertyURI for two columns (ParentExperiments and Container).
        // BUGBUG: Clear PropertyURI -- it will be regenerated when .getPropertyURI() is called
        // BUGBUG: When wrapping columns with auto-generated PropertyURIs where the name differs, we should regenerate the PropertyURI.
        parentExperimentsCol.setPropertyURI(null);
        addColumn(parentExperimentsCol);

        setTitleColumn("ExperimentNumber");

        // UNDONE: experiment specific details page
        //DetailsURL detailsURL = new DetailsURL(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer()));
        DetailsURL detailsURL = QueryService.get().urlDefault(getContainer(), QueryAction.detailsQueryRow, this);
        //DetailsURL detailsURL = new DetailsURL(new ActionURL(OConnorExperimentsController.ExperimentDetailsAction.class, getContainer()));
        setDetailsURL(detailsURL);

        // UNDONE: experiment specific insert page
        //setInsertURL(new DetailsURL(OConnorExperimentsController.InsertExperimentAction.class));

        // UNDONE: experiment specific update page
        //setUpdateURL(DetailsURL.fromString("core/updateWorkbook.view"));

        setDefaultVisibleColumns(Arrays.asList(
                FieldKey.fromParts("ExperimentNumber"),
                FieldKey.fromParts("CreatedBy"),
                FieldKey.fromParts("Modified"),
                FieldKey.fromParts("Description"),
                FieldKey.fromParts("ExperimentType"),
                FieldKey.fromParts("ParentExperiments")
        ));

    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        final AbstractQueryUpdateService superQUS = (AbstractQueryUpdateService)super.getUpdateService();
        return new ExperimentsQueryUpdateService(this, getRealTable(), superQUS);
    }

    private class ExperimentsQueryUpdateService extends SimpleQueryUpdateService
    {
        private final AbstractQueryUpdateService _wrapped;

        public ExperimentsQueryUpdateService(SimpleUserSchema.SimpleTable queryTable, TableInfo dbTable, AbstractQueryUpdateService wrapped)
        {
            super(queryTable, dbTable);
            _wrapped = wrapped;
        }

        @Override
        public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            // TODO: Handle MultiValueFK junction entries in SimpleQueryUpdateService instead of here...
            // Delete all associated ParentExperiment rows before deleting experiment rows
            // so we don't get a constraint violation.
            // CONSIDER: Should we delete all ParentExperiments that refer to Containers being deleted?
            TableInfo parentExperimentsTable = getUserSchema().getTable(OConnorExperimentsUserSchema.Table.ParentExperiments.name());
            QueryUpdateService parentExperimentsQUS = parentExperimentsTable.getUpdateService();
            if (parentExperimentsQUS != null)
            {
                List<String> containers = new ArrayList<String>();

                for (Map<String, Object> key : keys)
                {
                    String c = (String)key.get("container");
                    containers.add(c);
                }
                Filter filter = new SimpleFilter("Container", containers, CompareType.IN);
                TableSelector selector = new TableSelector(
                        parentExperimentsTable, Arrays.asList(parentExperimentsTable.getColumn("RowId")), filter, null);
                Map<String, Object>[] rows = selector.getArray(Map.class);
                List<Map<String, Object>> rowIds = Arrays.asList(rows);

                parentExperimentsQUS.deleteRows(user, container, rowIds, extraScriptContext);
            }

            return _wrapped.deleteRows(user, container, keys, extraScriptContext);
        }

        @Override
        protected void _delete(Container c, Map<String, Object> row) throws SQLException, InvalidKeyException
        {
            super._delete(c, row);
        }
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        DataIteratorBuilder insertETL = super.persistRows(data, context);
        if (insertETL != null)
            insertETL = new _DataIteratorBuilder(insertETL);
        return insertETL;
    }

    private class _DataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorContext _context;
        final DataIteratorBuilder _in;

        _DataIteratorBuilder(@NotNull DataIteratorBuilder in)
        {
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            DataIterator it = new _DataIterator(input, _context);

            return LoggingDataIterator.wrap(it);
        }
    }

    // TODO: Handle MultiValueFK junction entries in SimpleTable's persistRows() instead of here.
    private class _DataIterator extends SimpleTranslator
    {
        private DataIteratorContext _context;
        private final Integer _containerCol;
        private final Integer _parentExperimentsCol;

        private SchemaTableInfo _parentExperimentsTable;

        public _DataIterator(DataIterator data, DataIteratorContext context)
        {
            super(data, context);
            _context = context;

            Map<String,Integer> inputColMap = DataIteratorUtil.createColumnAndPropertyMap(data);
            _containerCol = inputColMap.get("container");
            _parentExperimentsCol = inputColMap.get("parentExperiments");

            // Just pass all columns through
            for (int i=1; i <= data.getColumnCount(); i++)
            {
                ColumnInfo col = data.getColumnInfo(i);
                addColumn(i);
            }
        }

        @Override
        public boolean isScrollable()
        {
            return false;
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();
            if (!hasNext)
                return false;

            // No current Container or ParentExperiments
            if (_containerCol == null || _parentExperimentsCol == null || get(_parentExperimentsCol) == null)
                return true;

            // Get the newly created workbook container entityid
            String containerEntityId = (String)get(_containerCol);
            Container c = ContainerManager.getForId(containerEntityId);
            if (c == null || !c.isWorkbook())
            {
                addFieldError("ParentExperiment", "Current container must be a workbook");
                return true;
            }

            // Get the parent experiments value
            // SimpleTranslator.MultiValueConvertColumn should convert the value into a Collection<String>
            Object o = get(_parentExperimentsCol);
            if (!(o instanceof Collection))
            {
                addFieldError("ParentExperiment", "Expected list of ParentExperiments: " + String.valueOf(o));
                return true;
            }

            // Validate each ParentExperiment is a workbook and create list of maps for insertion
            Collection<String> parentExperiments = (Collection<String>)o;
            List<String> colNames = Arrays.asList("Container", "ParentExperiment");
            List<Map<String, Object>> rows = new ArrayList<>();
            RowMapFactory<Object> factory = new RowMapFactory<>(colNames);
            for (String parentExperiment : parentExperiments)
            {
                Container p = ContainerManager.getForId(parentExperiment);
                if (p == null || !p.isWorkbook())
                {
                    addFieldError("ParentExperiment", "ParentExperiment must refer to workbooks");
                    return true;
                }

                Map<String, Object> row = factory.getRowMap(Arrays.<Object>asList(containerEntityId, parentExperiment));
                rows.add(row);
            }

            if (_parentExperimentsTable == null)
            {
                _parentExperimentsTable = OConnorExperimentsSchema.getInstance().createTableInfoParentExperiments();
                assert _parentExperimentsTable != null;
            }

            // Prepare the iterator context and copy the rows into ParentExperiments table
            ListofMapsDataIterator source = new ListofMapsDataIterator(new HashSet(colNames), rows);
            source.setDebugName("ExperimentsTable.ParentExperiments");

            // Perform the insert
            try
            {
                int rowCount = DataIteratorUtil.copy(_context, source, _parentExperimentsTable, c, null);
            }
            catch (IOException e)
            {
                addRowError(e.getMessage());
            }
            catch (BatchValidationException e)
            {
                getRowError().addErrors(e.getLastRowError());
            }

            return true;
        }
    }
}