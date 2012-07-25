/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.HighlightingDisplayColumn;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MultiValuedLookupColumn;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Oct 4, 2010
 * Time: 8:26:17 PM
 */
public class GenotypingQuerySchema extends UserSchema
{
    private static final GenotypingSchema GS = GenotypingSchema.get();
    private static final Set<String> TABLE_NAMES;

    @Nullable private final Integer _analysisId;

    @SuppressWarnings({"UnusedDeclaration"})
    public enum TableType
    {
        Runs() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getRunsTable(), c);
                table.wrapAllColumns(true);
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(user, c));
                setDefaultVisibleColumns(table, "RowId, MetaDataId, Created, CreatedBy");
                //TODO
                //table.setDetailsURL(DetailsURL.fromString(c, "/genotyping/runs.view?run=${RowId}"));

                // Ignore meta data if not configured
                String runsQuery = new NonValidatingGenotypingFolderSettings(c).getRunsQuery();

                if (null != runsQuery)
                {
                    final QueryHelper qHelper = new GenotypingQueryHelper(c, user, runsQuery);

                    ColumnInfo metaData = table.getColumn("MetaDataId");
                    metaData.setFk(new LookupForeignKey("run_num", "run_num") {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return qHelper.getTableInfo();
                        }
                    });

                    metaData.setLabel(qHelper.getQueryName());

                    // TODO: Better way to do this?
                    StringExpression url = qHelper.getTableInfo().getDetailsURL(Collections.singleton(new FieldKey(null, "run_num")), c);
                    url = DetailsURL.fromString(url.getSource().replace("run_num", "MetaDataId"));
                    metaData.setURL(url);
                }

                table.setDescription("Contains one row per sequencing run");

                return table;
            }},

        Sequences() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getSequencesTable(), c);
                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getDictionariesTable().getFromSQL("d") + " WHERE d.RowId = " + GS.getSequencesTable() + ".Dictionary) = ?");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);
                removeFromDefaultVisibleColumns(table, "Dictionary");
                table.setDescription("Contains one row per reference sequence");

                return table;
            }},

        Reads() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getReadsTable(), c)
                {
                    @Override
                    protected void applyContainerFilter(ContainerFilter filter)
                    {
                        FieldKey containerFieldKey = FieldKey.fromParts("Container");
                        clearConditions(containerFieldKey);
                        SQLFragment sql = new SQLFragment("Run IN (SELECT r.RowId FROM ");
                        sql.append(GS.getRunsTable(), "r");
                        sql.append(" WHERE ");
                        sql.append(filter.getSQLFragment(getSchema(), "r.Container", getContainer()));
                        sql.append(")");
                        addCondition(sql, containerFieldKey);
                    }
                };
                table.setContainerFilter(table.getContainerFilter());

                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("Run IN (SELECT Run FROM " + GS.getRunsTable().getFromSQL("r") + " WHERE Container = ?)");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);
                setDefaultVisibleColumns(table, "Name, SampleId, Sequence, Quality");

                // No validation... ignore sample meta data if not set
                String samplesQuery = new NonValidatingGenotypingFolderSettings(c).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(c, user, samplesQuery);
                    final TableInfo samples = qHelper.getTableInfo();

                    ColumnInfo sampleId = table.getColumn("SampleId");
                    sampleId.setFk(new LookupForeignKey("key", "library_sample_name") {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return samples;
                        }
                    });

                    // TODO: Better way to do this?
                    StringExpression url = samples.getDetailsURL(Collections.singleton(new FieldKey(null, "key")), c);
                    url = DetailsURL.fromString(url.getSource().replace("Key", "sampleId"));
                    sampleId.setURL(url);
                }

                table.setDescription("Contains one row per sequencing read");

                return table;
            }},

        MatchReads() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                FilteredTable table = Reads.createTable(c, user);
                table.setDescription("Contains genotyping matches joined to their corresponding reads");

                ColumnInfo readId = table.getColumn("RowId");
                readId.setFk(new LookupForeignKey("ReadId", "MatchId") {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        FilteredTable junction = new FilteredTable(GS.getReadsJunctionTable());
                        junction.wrapAllColumns(true);
                        ColumnInfo matchId = junction.getColumn("MatchId");
                        matchId.setFk(null);
                        return junction;
                    }
                });

                return table;
            }},

        Analyses() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getAnalysesTable(), c)
                {
                    @Override
                    protected void applyContainerFilter(ContainerFilter filter)
                    {
                        FieldKey containerFieldKey = FieldKey.fromParts("Container");
                        clearConditions(containerFieldKey);
                        SQLFragment sql = new SQLFragment("Run IN (SELECT r.RowId FROM ");
                        sql.append(GS.getRunsTable(), "r");
                        sql.append(" WHERE ");
                        sql.append(filter.getSQLFragment(getSchema(), "r.Container", getContainer()));
                        sql.append(")");
                        addCondition(sql, containerFieldKey);
                    }
                };
                table.setContainerFilter(table.getContainerFilter());

                table.wrapAllColumns(true);
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(user, c));
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getRunsTable() + " r WHERE r.RowId = " + GS.getAnalysesTable() + ".Run) = ?");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);
                setDefaultVisibleColumns(table, "RowId, Run, Created, CreatedBy, Description, SequenceDictionary, SequencesView");
                table.setDescription("Contains one row per genotyping analysis");

                return table;
            }},

        // TODO: Add matches view that displays the original match information (omitting combining/altering).  SQL for this is below
        // SELECT * FROM genotyping.matches matches LEFT JOIN genotyping.matches combined on matches.rowid = combined.parentid WHERE matches.analysis = 38 AND combined.rowid IS NULL order by matches.rowid

        Matches() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                return createTable(c, user, null);
            }

            public FilteredTable createTable(Container c, User user, @Nullable final Integer analysisId)
            {
                FilteredTable table = new FilteredTable(GS.getMatchesTable(), c);
                //TODO: filter on container??

                table.wrapAllColumns(true);

                // Big hack to work around our lack of multi-column foreign keys and fix bad performance issues related to
                // the alleles multi-valued column, see #11949.  If we're given an analysisId (most callers do) then wrap
                // the junction table and filter it to the analysis of interest.
                //
                // TODO: Support multi-column foreign keys so we can push analysis and matchid around more easily.
                if (null != analysisId)
                {
                    ColumnInfo alleles = table.getColumn("Alleles");

                    ForeignKey fk = new MultiValuedForeignKey(new ColumnInfo.SchemaForeignKey(alleles, GS.getSchemaName(), "AllelesJunction", "MatchId", false) {
                        @Override
                        // This override lets us filter on analysis ID inside the group by
                        public TableInfo getLookupTableInfo()
                        {
                            FilteredTable analysisFilteredJunction = new FilteredTable(super.getLookupTableInfo());
                            analysisFilteredJunction.wrapAllColumns(true);
                            analysisFilteredJunction.addCondition(new SimpleFilter("Analysis", analysisId));

                            return analysisFilteredJunction;
                        }
                    }, "SequenceId") {
                        @Override
                        protected MultiValuedLookupColumn createMultiValuedLookupColumn(ColumnInfo alleleName, ColumnInfo parent, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk)
                        {
                            final DisplayColumnFactory factory = alleleName.getDisplayColumnFactory();
                            alleleName.setDisplayColumnFactory(new DisplayColumnFactory() {
                                    @Override
                                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                                    {
                                        return new HighlightingDisplayColumn(factory.createRenderer(colInfo),
                                                FieldKey.fromString("SampleId"),
                                                FieldKey.fromString("Alleles/AlleleName"));
                                    }
                                });

                            return super.createMultiValuedLookupColumn(alleleName, parent, childKey, junctionKey, fk);
                        }
                    };

                    alleles.setFk(fk);
                }

                SQLFragment containerCondition = new SQLFragment("Analysis IN (SELECT a.RowId FROM " + GS.getAnalysesTable().getFromSQL("a") + " INNER JOIN " + GS.getRunsTable().getFromSQL("r") + " ON a.Run = r.RowId WHERE Container = ?)");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);

                // Normal matches view never shows children of combined / altered / deleted matches
                table.addCondition(new SimpleFilter().addCondition("ParentId", null, CompareType.ISBLANK));
                setDefaultVisibleColumns(table, "SampleId, Reads, Percent, AverageLength, PosReads, NegReads, PosExtReads, NegExtReads, Alleles/AlleleName");

                // Ignore samples meta data if not configured
                String samplesQuery = new NonValidatingGenotypingFolderSettings(c).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(c, user, samplesQuery);
                    final TableInfo samples = qHelper.getTableInfo();

                    ColumnInfo sampleId = table.getColumn("SampleId");
                    sampleId.setFk(new LookupForeignKey("key", "library_sample_name") {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return samples;
                        }
                    });

                    // TODO: Better way to do this?
                    StringExpression url = samples.getDetailsURL(Collections.singleton(new FieldKey(null, "key")), c);
                    url = DetailsURL.fromString(url.getSource().replace("Key", "sampleId"));
                    sampleId.setURL(url);
                }

                table.setDescription("Contains one row per genotyping match");

                return table;
            }},

        SequenceFiles() {
            @Override
            FilteredTable createTable(final Container c, final User user)
            {
                FilteredTable table = new FilteredTable(GS.getSequenceFilesTable(), c)
                {
                    @Override
                    protected void applyContainerFilter(ContainerFilter filter)
                    {
                        FieldKey containerFieldKey = FieldKey.fromParts("Container");
                        clearConditions(containerFieldKey);
                        SQLFragment sql = new SQLFragment("Run IN (SELECT r.RowId FROM ");
                        sql.append(GS.getRunsTable(), "r");
                        sql.append(" WHERE ");
                        sql.append(filter.getSQLFragment(getSchema(), "r.Container", getContainer()));
                        sql.append(")");
                        addCondition(sql, containerFieldKey);
                    }
                };
                table.setContainerFilter(table.getContainerFilter());

                table.wrapAllColumns(true);
                table.getColumn("DataId").setLabel("Filename");
                table.getColumn("DataId").setFk(new LookupForeignKey("RowId")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return new ExpSchema(user, c).getDatasTable();
                    }
                });

                final ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(c, user, "query");
                final QueryHelper qHelper = new GenotypingQueryHelper(c, user, settings.getSamplesQuery());

                table.getColumn("SampleId").setFk(new LookupForeignKey(qHelper.getQueryGridURL(), SampleManager.KEY_COLUMN_NAME, SampleManager.KEY_COLUMN_NAME, SampleManager.KEY_COLUMN_NAME)
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return qHelper.getTableInfo();
                    }
                });
                //SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getRunsTable() + " r WHERE r.RowId = " + GS.getSequenceFilesTable() + ".Run) = ?");
                //containerCondition.add(c.getId());
                //table.addCondition(containerCondition);
                setDefaultVisibleColumns(table, "Run, DataId, SampleId, ReadCount");
                table.setDescription("Contains one row per sequence file imported with runs");

                return table;
            }},

        Samples() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                String samplesQuery = new NonValidatingGenotypingFolderSettings(c).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(c, user, samplesQuery);
                    TableInfo table = qHelper.getTableInfo();
                    //FilteredTable ft = new FilteredTable(table);
                    //ft.setDescription("Contains sample information and metadata");

                    return (FilteredTable)table;
                }

                return null;
            }},

        RunMetadata() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                String queryName = new NonValidatingGenotypingFolderSettings(c).getRunsQuery();

                if (null != queryName)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(c, user, queryName);
                    TableInfo table = qHelper.getTableInfo();
                    //FilteredTable ft = new FilteredTable(table);
                    //ft.setDescription("Contains metadata about each genotyping run");

                    return (FilteredTable)table;
                }

                return null;
            }
        },

        IlluminaTemplates() {
            @Override
            FilteredTable createTable(final Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getIlluminaTemplatesTable())
                {
                    @Override
                    public QueryUpdateService getUpdateService()
                    {
                        TableInfo table = getRealTable();
                        return (table != null && table.getTableType() == DatabaseTableType.TABLE ?
                                new DefaultQueryUpdateService(this, table):
                                null);
                    }

                    @Override
                    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
                    {
                        return c.hasPermission(user, perm);
                    }
                };
                table.wrapAllColumns(true);
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(user, c));
                table.getColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(user, c));

                setDefaultVisibleColumns(table, "Name, Json, Editable");
                table.setDescription("Contains one row per saved illumina import template");

                return table;
            }
        };

        abstract FilteredTable createTable(Container c, User user);

        // Special factory method for Matches table, to pass through analysis id (if present)
        FilteredTable createTable(Container c, User user, @Nullable Integer analysisId)
        {
            return createTable(c, user);
        }

        // Set an explicit list of default columns by name
        private static void setDefaultVisibleColumns(TableInfo table, String columnNames)
        {
            List<FieldKey> fieldKeys = new LinkedList<FieldKey>();

            for (String name : columnNames.split(",\\s*"))
                fieldKeys.add(FieldKey.fromString(name));

            table.setDefaultVisibleColumns(fieldKeys);
        }


        // Leave all columns as default visible, except for columnsToRemove
        private static TableInfo removeFromDefaultVisibleColumns(TableInfo table, String columnsToRemove)
        {
            Set<String> removeColumns = Sets.newCaseInsensitiveHashSet(columnsToRemove.split(",\\s*"));

            List<FieldKey> keys = table.getDefaultVisibleColumns();
            List<FieldKey> visibleColumns = new ArrayList<FieldKey>(keys.size());

            for (FieldKey key : keys)
                if (!removeColumns.contains(key.getName()))
                    visibleColumns.add(key);

            table.setDefaultVisibleColumns(visibleColumns);

            return table; // For convenience
        }
    }

    static
    {
        Set<String> names = new LinkedHashSet<String>();

        for (TableType type : TableType.values())
        {
            names.add(type.toString());
        }

        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register(final GenotypingModule module)
    {
        DefaultSchema.registerProvider(GS.getSchemaName(), new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                if (schema.getContainer().getActiveModules().contains(module))
                    return new GenotypingQuerySchema(schema.getUser(), schema.getContainer());
                else
                    return null;
            }
        });
    }

    public GenotypingQuerySchema(User user, Container container)
    {
        this(user, container, null);
    }

    public GenotypingQuerySchema(User user, Container container, @Nullable Integer analysisId)
    {
        super(GS.getSchemaName(), "Contains genotyping data", user, container, GS.getSchema());
        _analysisId = analysisId;
    }

    @Override
    protected TableInfo createTable(String name)
    {
        // Special handling for Matches -- need to pass in Analysis
        if (name.startsWith(TableType.Matches.name()))
        {
            Integer analysisId = _analysisId;

            if (null == analysisId)
            {
                String[] split = name.split("_");

                if (split.length > 1)
                {
                    if (split.length == 2 && TableType.Matches.name().equals(split[0]))
                    {
                        analysisId = Integer.parseInt(split[1]);
                    }
                    else
                    {
                        return null;
                    }
                }
            }

            return TableType.Matches.createTable(getContainer(), getUser(), analysisId);
        }

        if (TABLE_NAMES.contains(name))
            return TableType.valueOf(name).createTable(getContainer(), getUser());

        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        if(settings.getQueryName().equalsIgnoreCase(TableType.Samples.name()))
        {
            return new QueryView(this, settings, errors)
            {
                @Override
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    populateButtonBar(view, bar, false);


                    ActionButton btn = new ActionButton("Create Illumina Sample Sheet");
                    btn.setActionType(ActionButton.Action.SCRIPT);
                    btn.setRequiresSelection(true);
                    btn.setScript("var dataRegion = LABKEY.DataRegions['" + view.getDataRegion().getName() + "'];\n" +
                        "var checked = dataRegion.getChecked();\n" +
                        "if(!checked.length){\n" +
                        "    alert('Must select one or more rows');\n" +
                        "    return;\n" +
                        "}\n" +
                        "window.location = LABKEY.ActionURL.buildURL('genotyping', 'illuminaSampleSheetExport', null, {\n" +
                        "    pks: checked,\n" +
                        "})\n"
                    );
                    bar.add(btn);
                }
            };
        }

        return new QueryView(this, settings, errors);
    }
}
