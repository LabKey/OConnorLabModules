/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.oconnorexperiments.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.ExtendedTable;
import org.labkey.api.data.Filter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.ListofMapsDataIterator;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.etl.ValidatorIterator;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
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
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.oconnorexperiments.OConnorExperimentFolderType;
import org.labkey.oconnorexperiments.OConnorExperimentsController;
import org.labkey.oconnorexperiments.OConnorExperimentsSchema;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
        TableInfo workbooksTable = core.getTable("Workbooks");

        SchemaTableInfo rootTable = OConnorExperimentsSchema.getInstance().createTableInfoExperiments();

        return new ExperimentsTable(name, schema, rootTable, workbooksTable).init();
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
        // XXX: I think we want the hard table column instead of the query column
        //return getRealTable().getColumn("Container");
    }

    @Override
    protected ColumnInfo getBaseLookupKeyColumn()
    {
        return getBaseTable().getColumn("EntityId");
    }

    protected ForeignKey createExtendedForeignKey()
    {
        return new QueryForeignKey(getBaseTable(), "EntityId", null);
    }

    public void addColumns()
    {
        ColumnInfo containerCol = addWrapColumn(getRealTable().getColumn("Container"));
        containerCol.setFk(getExtendedForeignKey());
        //containerCol.setFk(new QueryForeignKey("core", getUserSchema().getContainer(), getUserSchema().getUser(), "Workbooks", "EntityId", null));
        containerCol.setHidden(true);
        containerCol.setSortFieldKeys(Collections.singletonList(FieldKey.fromParts("ExperimentNumber")));
        containerCol.setSortDirection(Sort.SortDirection.DESC);

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
        parentExperimentsCol.setCalculated(true);
        parentExperimentsCol.setUserEditable(true);
        parentExperimentsCol.setNullable(true);
        // BUGBUG: ETL doesn't like using the same PropertyURI for two columns (ParentExperiments and Container).
        // BUGBUG: Clear PropertyURI -- it will be regenerated when .getPropertyURI() is called
        // BUGBUG: When wrapping columns with auto-generated PropertyURIs where the name differs, we should regenerate the PropertyURI.
        parentExperimentsCol.setPropertyURI(null);

        ActionURL projectBeginURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
        DetailsURL parentExperimentsURL = new DetailsURL(projectBeginURL);
        parentExperimentsURL.setContainerContext(new ContainerContext.FieldKeyContext(FieldKey.fromParts("ParentExperiments", "Container")));
        parentExperimentsCol.setURL(parentExperimentsURL);
        addColumn(parentExperimentsCol);

        ColumnInfo folderTypeCol = addBaseTableColumn("FolderType", "FolderType");
        folderTypeCol.setHidden(true);
        //folderTypeCol.setReadOnly(false);
        //folderTypeCol.setUserEditable(true);
        //folderTypeCol.setShownInInsertView(true);
        //folderTypeCol.setShownInUpdateView(false);
        //folderTypeCol.setShownInDetailsView(false);
        //folderTypeCol.setInputType("hidden");
        folderTypeCol.setDefaultValue("OConnorExperiment");

        setTitleColumn("ExperimentNumber");

        DetailsURL detailsURL = new DetailsURL(projectBeginURL);
        setDetailsURL(detailsURL);

        setInsertURL(new DetailsURL(new ActionURL(OConnorExperimentsController.InsertExperimentAction.class, getContainer())));

        // Disable the update URL -- the project begin view is used to edit the experiment
        setUpdateURL(LINK_DISABLER);

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
        public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            // TODO: Handle MultiValueFK junction entries in SimpleQueryUpdateService instead of here...
            deleteParentExperiments(user, container, rows, extraScriptContext);
            try
            {
                insertParentExperiments(user, container, rows, extraScriptContext);
            }
            catch (DuplicateKeyException e)
            {
                throw new QueryUpdateServiceException(e);
            }
            return _wrapped.updateRows(user, container, rows, oldKeys, extraScriptContext);
        }

        @Override
        public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            // TODO: Handle MultiValueFK junction entries in SimpleQueryUpdateService instead of here...
            // Delete all associated ParentExperiment rows before deleting experiment rows
            // so we don't get a constraint violation.
            // CONSIDER: Should we delete all ParentExperiments that refer to Containers being deleted?
            deleteParentExperiments(user, container, keys, extraScriptContext);

            return _wrapped.deleteRows(user, container, keys, extraScriptContext);
        }

        private void insertParentExperiments(User user, Container container, List<Map<String, Object>> rows, Map<String, Object> extraScriptContext) throws SQLException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
        {
            TableInfo parentExperimentsTable = getUserSchema().getTable(OConnorExperimentsUserSchema.Table.ParentExperiments.name());
            QueryUpdateService parentExperimentsQUS = parentExperimentsTable.getUpdateService();
            if (parentExperimentsQUS != null)
            {
                for (Map<String, Object> row : rows)
                {
                    List<Map<String, Object>> parentExperimentRows = new ArrayList<>();
                    String c = (String)row.get("container");
                    Object v = row.get("ParentExperiments");
                    String[] parentExperiments = null;
                    if (v instanceof String[])
                        parentExperiments = (String[])v;
                    else if (v instanceof JSONArray)
                    {
                        ArrayList<String> s = new ArrayList<>();
                        for (Object o : ((JSONArray)v).toArray())
                            s.add(o.toString());
                        parentExperiments = s.toArray(new String[s.size()]);
                    }

                    if (parentExperiments != null && parentExperiments.length > 0)
                    {
                        for (String parentExperiment : parentExperiments)
                        {
                            Map<String, Object> parentExperimentRow = new CaseInsensitiveHashMap<>();
                            parentExperimentRow.put("Container", c);
                            parentExperimentRow.put("ParentExperiment", parentExperiment);
                            parentExperimentRows.add(parentExperimentRow);
                        }
                    }

                    BatchValidationException errors = new BatchValidationException();
                    parentExperimentsQUS.insertRows(user, ContainerManager.getForId(c), parentExperimentRows, errors, extraScriptContext);
                    if (errors.hasErrors())
                        throw errors;
                }


            }

        }

        private void deleteParentExperiments(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext) throws SQLException, QueryUpdateServiceException, BatchValidationException, InvalidKeyException
        {
            TableInfo parentExperimentsTable = getUserSchema().getTable(OConnorExperimentsUserSchema.Table.ParentExperiments.name());
            QueryUpdateService parentExperimentsQUS = parentExperimentsTable.getUpdateService();
            if (parentExperimentsQUS != null)
            {
                List<String> containers = new ArrayList<>();

                for (Map<String, Object> key : keys)
                {
                    String c = (String)key.get("container");
                    containers.add(c);
                }
                Filter filter = new SimpleFilter("Container", containers, CompareType.IN);
                TableSelector selector = new TableSelector(
                        parentExperimentsTable, Arrays.asList(parentExperimentsTable.getColumn("RowId")), filter, null);
                Map<String, Object>[] rows = selector.getMapArray();
                List<Map<String, Object>> rowIds = Arrays.asList(rows);

                parentExperimentsQUS.deleteRows(user, container, rowIds, extraScriptContext);
            }
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
        // Issue 18069: OConnorExperiments: folderType not defaulting to OConnorExperiments when created via LK.Query.insertRows
        // Inject the default folderType of "OConnorExperiments" before handing the input
        DataIterator in = data.getDataIterator(context);
        SimpleTranslator x = new SimpleTranslator(in, context);
        x.setDebugName("ExperimentsTable folderType constant");
        boolean hasFolderType = false;
        for (int i=1 ; i<= in.getColumnCount() ; i++)
        {
            ColumnInfo col = in.getColumnInfo(i);
            if (col.getName().equalsIgnoreCase("folderType"))
                hasFolderType = true;
            x.addColumn(i);
        }

        if (!hasFolderType)
            x.addConstantColumn("folderType", JdbcType.VARCHAR, OConnorExperimentFolderType.NAME);

        in = LoggingDataIterator.wrap(x);

        // Issue 18119: Add length validation for "Experiment Type".
        // Remove this block when StandatdETL adds scale/length validation by default.
        for (int i = 1; i < x.getColumnCount(); i++)
        {
            ColumnInfo col = x.getColumnInfo(i);
            if ("experimentType".equalsIgnoreCase(col.getColumnName()))
            {
                ValidatorIterator validate = new ValidatorIterator(x, context, getContainer(), getUserSchema().getUser());
                validate.setDebugName("ExperimentsTable validate");
                validate.addLengthValidator(i, col);
                in = LoggingDataIterator.wrap(validate);
                break;
            }
        }

        data = new DataIteratorBuilder.Wrapper(in);

        // Feed the modified data iterator to the parent ETL
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
        private final Integer _experimentNumberCol;
        private final Integer _createdByCol;

        private SchemaTableInfo _parentExperimentsTable;

        public _DataIterator(DataIterator data, DataIteratorContext context)
        {
            super(data, context);
            _context = context;

            Map<String,Integer> inputColMap = DataIteratorUtil.createColumnAndPropertyMap(data);
            _containerCol = inputColMap.get("container");
            _parentExperimentsCol = inputColMap.get("parentExperiments");
            _experimentNumberCol = inputColMap.get("experimentNumber");
            _createdByCol = inputColMap.get("createdBy");

            // Just pass all columns through
            selectAll();
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

            String containerEntityId = _containerCol == null ? null : (String)get(_containerCol);
            // Get the newly created workbook container entityid
            Container c = containerEntityId == null ? null : ContainerManager.getForId(containerEntityId);

            if (_experimentNumberCol != null && c != null)
            {
                Object experimentNumber = get(_experimentNumberCol);
                if (experimentNumber != null)
                {
                    c.setSortOrder(((Number)experimentNumber).intValue());
                    new SqlExecutor(CoreSchema.getInstance().getSchema()).execute("UPDATE core.containers SET SortOrder = ? WHERE EntityId = ?", c.getSortOrder(), c.getId());
                }

            }

            // No current Container or ParentExperiments
            if (c == null || _parentExperimentsCol == null || get(_parentExperimentsCol) == null)
                return true;

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

            Collection<String> parentExperiments = (Collection<String>)o;
            if (parentExperiments.size() == 0)
                return true;

            // Validate each ParentExperiment is a workbook and create list of maps for insertion
            List<String> colNames = Arrays.asList("Container", "ParentExperiment");
            List<Map<String, Object>> rows = new ArrayList<>();
            RowMapFactory<Object> factory = new RowMapFactory<>(colNames);
            for (String parentExperiment : parentExperiments)
            {
                Container p = ContainerManager.getForId(parentExperiment);
                if (p == null)
                    p = getContainer().getChild(parentExperiment);

                if (p == null || !p.isWorkbook())
                {
                    addFieldError("ParentExperiment", "ParentExperiment must refer to workbooks");
                    return true;
                }

                Map<String, Object> row = factory.getRowMap(Arrays.<Object>asList(containerEntityId, p.getEntityId()));
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

            // Perform the insert to ParentExperiments table
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
