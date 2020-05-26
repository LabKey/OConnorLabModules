/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * User: adam
 * Date: Oct 4, 2010
 * Time: 8:26:17 PM
 */
public class GenotypingQuerySchema extends UserSchema
{
    private static final GenotypingSchema GS = GenotypingSchema.get();
    public static final String NAME = GS.getSchemaName();
    private static final Set<String> TABLE_NAMES;
    /** When building up concatenated haplotypes, a placeholder for NULL values */
    private static final String NULL_HAPLOTYPE_MARKER = "~";
    private static final Logger LOG = Logger.getLogger(GenotypingQuerySchema.class);

    private SortedMap<Integer, Map<String, String>> _allHaplotypes;

    @Nullable private final Integer _analysisId;

    public enum TableType
    {
        Runs() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                FilteredTable table = new FilteredTable<>(GS.getRunsTable(), schema, cf);
                table.wrapAllColumns(true);
                table.getMutableColumn("CreatedBy").setFk(new UserIdQueryForeignKey(schema, true));
                setDefaultVisibleColumns(table, "RowId, MetaDataId, Created, CreatedBy");
                //TODO
                //table.setDetailsURL(DetailsURL.fromString(c, "/genotyping/runs.view?run=${RowId}"));

                // Ignore meta data if not configured
                String runsQuery = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getRunsQuery();

                if (null != runsQuery)
                {
                    final QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), runsQuery);

                    var metaData = table.getMutableColumn("MetaDataId");
                    metaData.setFk(new LookupForeignKey(cf, GenotypingQueryHelper.RUN_NUM, GenotypingQueryHelper.RUN_NUM) {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return qHelper.getTableInfo(getLookupContainerFilter());
                        }
                    });

                    metaData.setLabel(qHelper.getQueryName());

                    // TODO: Better way to do this?
                    StringExpression url = qHelper.getTableInfo(cf).getDetailsURL(Collections.singleton(FieldKey.fromParts(GenotypingQueryHelper.RUN_NUM)), schema.getContainer());
                    if (url != null)
                    {
                        url = DetailsURL.fromString(url.getSource().replace(GenotypingQueryHelper.RUN_NUM, "MetaDataId"));
                        metaData.setURL(url);
                    }
                }

                table.setDescription("Contains one row per sequencing run");

                return table;
            }},

        Sequences() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                FilteredTable table = new FilteredTable<>(GS.getSequencesTable(), schema, cf);
                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM ").append(GS.getDictionariesTable().getFromSQL("d")).append(" WHERE d.RowId = ").append(GS.getSequencesTable()).append(".Dictionary) = ?");
                containerCondition.add(schema.getContainer().getId());
                table.addCondition(containerCondition);
                removeFromDefaultVisibleColumns(table, "Dictionary");
                table.setDescription("Contains one row per reference sequence");

                return table;
            }},

        Reads() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                FilteredTable table = new FilteredTable<GenotypingQuerySchema>(GS.getReadsTable(), schema, cf)
                {
                    @Override
                    protected void applyContainerFilter(ContainerFilter filter)
                    {
                        FieldKey containerFieldKey = FieldKey.fromParts("Container");
                        clearConditions(containerFieldKey);
                        SQLFragment sql = new SQLFragment("Run IN (SELECT r.RowId FROM ");
                        sql.append(GS.getRunsTable(), "r");
                        sql.append(" WHERE ");
                        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("r.Container"), getContainer()));
                        sql.append(")");
                        addCondition(sql, containerFieldKey);
                    }
                };
                table.setContainerFilter(table.getContainerFilter());

                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("Run IN (SELECT Run FROM ").append(GS.getRunsTable().getFromSQL("r")).append(" WHERE Container = ?)");
                containerCondition.add(schema.getContainer().getId());
                table.addCondition(containerCondition);
                setDefaultVisibleColumns(table, "Name, SampleId, Sequence, Quality");

                // No validation... ignore sample meta data if not set
                String samplesQuery = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), samplesQuery);
                    final TableInfo samples = qHelper.getTableInfo(cf);

                    var sampleId = table.getMutableColumn("SampleId");
                    sampleId.setFk(new LookupForeignKey("key", "library_sample_name") {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return samples;
                        }
                    });

                    // TODO: Better way to do this?
                    StringExpression url = samples.getDetailsURL(Collections.singleton(new FieldKey(null, "key")), schema.getContainer());
                    if (url != null)
                    {
                        url = DetailsURL.fromString(url.getSource().replace("Key", "sampleId"));
                        sampleId.setURL(url);
                    }
                }

                table.setDescription("Contains one row per sequencing read");

                return table;
            }

            @Override
            QueryView createQueryView(ViewContext context, QuerySettings settings, BindException errors, GenotypingQuerySchema schema)
            {
                return new QueryView(schema, settings, errors)
                {
                    @Override
                    @NotNull
                    public PanelButton createExportButton(@Nullable List<String> recordSelectorColumns)
                    {
                        PanelButton result = super.createExportButton(recordSelectorColumns);
                        ActionURL url = getViewContext().cloneActionURL();
                        url.addParameter("exportType", GenotypingController.FASTQ_FORMAT);

                        HttpView filesView = new JspView<>("/org/labkey/genotyping/view/fastqExportOptions.jsp", url);
                        result.addSubPanel("FASTQ", filesView);
                        return result;
                    }
                };
            }
        },

        MatchReads() {
            @Override
            FilteredTable createTable(final GenotypingQuerySchema schema, ContainerFilter cf)
            {
                FilteredTable table = Reads.createTable(schema, cf);
                table.setDescription("Contains genotyping matches joined to their corresponding reads");

                var readId = table.getMutableColumn("RowId");
                readId.setFk(new LookupForeignKey("ReadId", "MatchId") {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        FilteredTable junction = new FilteredTable(GS.getReadsJunctionTable(), schema);
                        junction.wrapAllColumns(true);
                        var matchId = junction.getMutableColumn("MatchId");
                        matchId.clearFk();
                        return junction;
                    }
                });

                return table;
            }},

        Analyses() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                FilteredTable table = new FilteredTable<GenotypingQuerySchema>(GS.getAnalysesTable(), schema, cf)
                {
                    @Override
                    protected void applyContainerFilter(ContainerFilter filter)
                    {
                        FieldKey containerFieldKey = FieldKey.fromParts("Container");
                        clearConditions(containerFieldKey);
                        SQLFragment sql = new SQLFragment("Run IN (SELECT r.RowId FROM ");
                        sql.append(GS.getRunsTable(), "r");
                        sql.append(" WHERE ");
                        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("r.Container"), getContainer()));
                        sql.append(")");
                        addCondition(sql, containerFieldKey);
                    }
                };
                table.setContainerFilter(table.getContainerFilter());

                table.wrapAllColumns(true);
                table.getMutableColumn("CreatedBy").setFk(new UserIdQueryForeignKey(schema, true));
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getRunsTable() + " r WHERE r.RowId = " + GS.getAnalysesTable() + ".Run) = ?");
                containerCondition.add(schema.getContainer().getId());
                table.addCondition(containerCondition);
                setDefaultVisibleColumns(table, "RowId, Run, Created, CreatedBy, Description, SequenceDictionary, SequencesView");
                table.setDescription("Contains one row per genotyping analysis");

                return table;
            }},

        Matches() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                return createTable(schema, cf, null);
            }

            @Override
            public FilteredTable createTable(final GenotypingQuerySchema schema, ContainerFilter cf, @Nullable final Integer analysisId)
            {
                FilteredTable table = new FilteredTable<>(GS.getMatchesTable(), schema, cf);
                //TODO: filter on container??

                table.wrapAllColumns(true);

                // Big hack to work around our lack of multi-column foreign keys and fix bad performance issues related to
                // the alleles multi-valued column, see #11949.  If we're given an analysisId (most callers do) then wrap
                // the junction table and filter it to the analysis of interest.
                //
                // TODO: Support multi-column foreign keys so we can push analysis and matchid around more easily.
                if (null != analysisId)
                {
                    var alleles = table.getMutableColumn("Alleles");

                    ForeignKey fk = new MultiValuedForeignKey(new BaseColumnInfo.SchemaForeignKey(alleles, GS.getSchemaName(), "AllelesJunction", "MatchId", false) {
                        @Override
                        // This override lets us filter on analysis ID inside the group by
                        public TableInfo getLookupTableInfo()
                        {
                            FilteredTable analysisFilteredJunction = new FilteredTable(super.getLookupTableInfo(), schema);
                            analysisFilteredJunction.wrapAllColumns(true);
                            analysisFilteredJunction.addCondition(new SimpleFilter(FieldKey.fromParts("Analysis"), analysisId));

                            return analysisFilteredJunction;
                        }
                    }, "SequenceId") {
                        @Override
                        protected MultiValuedLookupColumn createMultiValuedLookupColumn(ColumnInfo alleleName, ColumnInfo parent, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk)
                        {
                            final DisplayColumnFactory factory = alleleName.getDisplayColumnFactory();
                            ((BaseColumnInfo)alleleName).setDisplayColumnFactory(colInfo -> new HighlightingDisplayColumn(factory.createRenderer(colInfo),
                                    FieldKey.fromString("SampleId"),
                                    FieldKey.fromString("Alleles/AlleleName")));

                            return super.createMultiValuedLookupColumn(alleleName, parent, childKey, junctionKey, fk);
                        }
                    };

                    alleles.setFk(fk);
                }

                SQLFragment containerCondition = new SQLFragment("Analysis IN (SELECT a.RowId FROM ").append(GS.getAnalysesTable().getFromSQL("a")).append(" INNER JOIN ").append(GS.getRunsTable().getFromSQL("r")).append(" ON a.Run = r.RowId WHERE Container = ?)");
                containerCondition.add(schema.getContainer().getId());
                table.addCondition(containerCondition);

                // Normal matches view never shows children of combined / altered / deleted matches
                table.addCondition(new SimpleFilter().addCondition(FieldKey.fromParts("ParentId"), null, CompareType.ISBLANK));
                setDefaultVisibleColumns(table, "SampleId, Reads, Percent, AverageLength, PosReads, NegReads, PosExtReads, NegExtReads, Alleles/AlleleName");

                // Ignore samples meta data if not configured
                String samplesQuery = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), samplesQuery);
                    final TableInfo samples = qHelper.getTableInfo(cf);

                    var sampleId = table.getMutableColumn("SampleId");
                    sampleId.setFk(new LookupForeignKey("key", "library_sample_name") {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return samples;
                        }
                    });

                    // TODO: Better way to do this?
                    StringExpression url = samples.getDetailsURL(Collections.singleton(new FieldKey(null, "key")), schema.getContainer());
                    if (url != null)
                    {
                        url = DetailsURL.fromString(url.getSource().replace("Key", "sampleId"));
                        sampleId.setURL(url);
                    }
                }

                table.setDescription("Contains one row per genotyping match");

                return table;
            }},

        SequenceFiles() {
            @Override
            FilteredTable createTable(final GenotypingQuerySchema schema, ContainerFilter cf)
            {
                FilteredTable table = new FilteredTable<GenotypingQuerySchema>(GS.getSequenceFilesTable(), schema, cf)
                {
                    @Override
                    protected void applyContainerFilter(ContainerFilter filter)
                    {
                        FieldKey containerFieldKey = FieldKey.fromParts("Container");
                        clearConditions(containerFieldKey);
                        SQLFragment sql = new SQLFragment("Run IN (SELECT r.RowId FROM ");
                        sql.append(GS.getRunsTable(), "r");
                        sql.append(" WHERE ");
                        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("r.Container"), getContainer()));
                        sql.append(")");
                        addCondition(sql, containerFieldKey);
                    }
                };
                table.setContainerFilter(table.getContainerFilter());

                table.wrapAllColumns(true);

                table.getMutableColumn("PoolNum").setLabel("Pool Num");

                table.getMutableColumn("DataId").setLabel("Filename");
                table.getMutableColumn("DataId").setFk(new LookupForeignKey("RowId")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return new ExpSchema(schema.getUser(), schema.getContainer()).getDatasTable();
                    }
                });

                final ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(schema.getContainer(), schema.getUser(), "query");
                final QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), settings.getSamplesQuery());

                table.getMutableColumn("SampleId").setFk(new LookupForeignKey(cf, qHelper.getQueryGridURL(), SampleManager.KEY_COLUMN_NAME, SampleManager.KEY_COLUMN_NAME, SampleManager.KEY_COLUMN_NAME)
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return qHelper.getTableInfo(getLookupContainerFilter());
                    }
                });
                setDefaultVisibleColumns(table, "Run, DataId, SampleId, ReadCount, PoolNum");
                table.setDescription("Contains one row per sequence file imported with runs");

                return table;
            }

            @Override
            QueryView createQueryView(ViewContext context, QuerySettings settings, BindException errors, GenotypingQuerySchema schema)
            {
                return new QueryView(schema, settings, errors)
                {
                    @Override
                    protected void populateButtonBar(DataView view, ButtonBar bar)
                    {
                        //add custom button to download files
                        ActionButton btn = new ActionButton("Download Selected");
                        btn.setScript("LABKEY.Genotyping.exportFilesBtnHandler('" + view.getDataRegion().getName() + "');");
                        btn.setRequiresSelection(true);

                        bar.add(btn);

                        super.populateButtonBar(view, bar);
                    }

                    @NotNull
                    @Override
                    public LinkedHashSet<ClientDependency> getClientDependencies()
                    {
                        LinkedHashSet<ClientDependency> resources = super.getClientDependencies();
                        resources.add(ClientDependency.fromPath("Ext4"));
                        resources.add(ClientDependency.fromPath("genotyping/RunExportWindow.js"));
                        return resources;
                    }
                };
            }
        },

        Samples() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                String samplesQuery = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), samplesQuery);
                    TableInfo table = qHelper.getTableInfo(cf, true);

                    return (FilteredTable)table;
                }

                return null;
            }

            @Override
            QueryView createQueryView(ViewContext context, QuerySettings settings, BindException errors, GenotypingQuerySchema schema)
            {
                return new QueryView(schema, settings, errors)
                {
                    @Override
                    protected void populateButtonBar(DataView view, ButtonBar bar)
                    {
                        super.populateButtonBar(view, bar);

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
        },

        RunMetadata() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                String queryName = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getRunsQuery();

                if (null != queryName)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), queryName);
                    TableInfo table = qHelper.getTableInfo(cf, true);

                    return (FilteredTable)table;
                }

                return null;
            }

            @Override
            boolean isAvailable(GenotypingQuerySchema schema)
            {
                return createTable(schema, null) != null;
            }
        },

        IlluminaTemplates() {
            @Override
            FilteredTable createTable(final GenotypingQuerySchema schema, ContainerFilter cf)
            {
                FilteredTable table = new FilteredTable<GenotypingQuerySchema>(GS.getIlluminaTemplatesTable(), schema, cf)
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
                    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
                    {
                        return schema.getContainer().hasPermission(user, perm);
                    }
                };
                table.wrapAllColumns(true);
                table.getMutableColumn("CreatedBy").setFk(new UserIdQueryForeignKey(schema, true));
                table.getMutableColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(schema, true));

                setDefaultVisibleColumns(table, "Name, Json, Editable");
                table.setDescription("Contains one row per saved illumina import template");

                return table;
            }
        },
        Animal()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                SimpleUserSchema.SimpleTable table = new SimpleUserSchema.SimpleTable(schema, GS.getAnimalTable(), cf).init();
                table.setDescription("Contains one row per animal");

                var ci = schema.createConcatenatedHaplotypeColumn(table, true);
                if (ci != null)
                    table.addColumn(ci);

                return table;
            }
        },
        Haplotype()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                SimpleUserSchema.SimpleTable table = new SimpleUserSchema.SimpleTable(schema, GS.getHaplotypeTable(), cf).init();
                table.setDescription("Contains one row per haplotype");
                return table;
            }
        },
        Species()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                SimpleUserSchema.SimpleTable table = new SimpleUserSchema.SimpleTable(schema, GS.getSpeciesTable(), cf).init();
                table.setDescription("Contains one row per species");
                return table;
            }
        },
        AnimalAnalysis()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                SimpleUserSchema.SimpleTable table = new SimpleUserSchema.SimpleTable<GenotypingQuerySchema>(schema, GS.getAnimalAnalysisTable(), cf)
                {
                    @Override
                    protected void applyContainerFilter(ContainerFilter filter)
                    {
                        FieldKey containerFieldKey = FieldKey.fromParts("Container");
                        clearConditions(containerFieldKey);
                        SQLFragment sql = new SQLFragment("RunId IN (SELECT RowId FROM ");
                        sql.append(ExperimentService.get().getTinfoExperimentRun(), "r");
                        sql.append(" WHERE ");
                        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("r.Container"), getContainer()));
                        sql.append(")");
                        addCondition(sql, containerFieldKey);
                    }
                };
                table.init();
                table.setContainerFilter(table.getContainerFilter());

                var concatCol = schema.createConcatenatedHaplotypeColumn(table, false);
                if (concatCol != null)
                    table.addColumn(concatCol);

                // calculated field for % Unknown = (Total Reads - Identified Reads) / Total Reads
                SQLFragment percUnknownSql = new SQLFragment("(CASE WHEN (IdentifiedReads IS NULL OR TotalReads IS NULL OR TotalReads = 0) THEN NULL "
                        + "ELSE ((TotalReads-IdentifiedReads)*100.0/TotalReads) END)");
                ExprColumn percUnknownCol = new ExprColumn(table, "PercentUnknown", percUnknownSql, JdbcType.DOUBLE);
                percUnknownCol.setLabel("% Unknown");
                percUnknownCol.setFormat("0.0");
                table.addColumn(percUnknownCol);

                // disable the insert, delete, and import urls to hide the button bar buttons for Insert New, Import Data, etc. for this table
                table.setInsertURL(AbstractTableInfo.LINK_DISABLER);
                table.setDeleteURL(AbstractTableInfo.LINK_DISABLER);
                table.setImportURL(AbstractTableInfo.LINK_DISABLER);

                // set the edit link URL to use the custom haplotype assignment editing page
                ActionURL updateUrl = new ActionURL(GenotypingController.EditHaplotypeAssignmentAction.class, null);
                table.setUpdateURL(new DetailsURL(updateUrl, Collections.singletonMap("rowId", FieldKey.fromString("RowId"))));

                setDefaultVisibleColumns(table, "AnimalId, TotalReads, IdentifiedReads, PercentUnknown, ConcatenatedHaplotypes, Enabled");
                table.setDescription("Contains one row per animal in a given run");

                return table;
            }
        },
        AnimalHaplotypeAssignment()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf)
            {
                SimpleUserSchema.SimpleTable table = new SimpleUserSchema.SimpleTable<GenotypingQuerySchema>(schema, GS.getAnimalHaplotypeAssignmentTable(), cf)
                {
                    @Override
                    protected void applyContainerFilter(ContainerFilter filter)
                    {
                        FieldKey containerFieldKey = FieldKey.fromParts("Container");
                        clearConditions(containerFieldKey);
                        SQLFragment sql = new SQLFragment("AnimalAnalysisid IN (SELECT aa.RowId FROM ");
                        sql.append(GS.getAnimalAnalysisTable(), "aa");
                        sql.append(" JOIN ");
                        sql.append(ExperimentService.get().getTinfoExperimentRun(), "r");
                        sql.append(" ON aa.RunId = r.RowId ");
                        sql.append(" WHERE ");
                        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("r.Container"), getContainer()));
                        sql.append(")");
                        addCondition(sql, containerFieldKey);
                    }
                };
                table.init();
                table.setContainerFilter(table.getContainerFilter());

                setDefaultVisibleColumns(table, "AnimalAnalysisId/RunId, AnimalAnalysisId/AnimalId, HaplotypeId");
                table.setDetailsURL(null);
                table.setDescription("Contains one row per animal/haplotype assignment in a given run");
                return table;
            }
        };

        abstract FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf);

        QueryView createQueryView(ViewContext context, QuerySettings settings, BindException errors, GenotypingQuerySchema schema)
        {
            return schema.createDefaultQueryView(context, settings, errors);
        }

        boolean isAvailable(GenotypingQuerySchema schema)
        {
            return true;
        }

        // Special factory method for Matches table, to pass through analysis id (if present)
        FilteredTable createTable(GenotypingQuerySchema schema, ContainerFilter cf, @Nullable Integer analysisId)
        {
            return createTable(schema, cf);
        }

        // Set an explicit list of default columns by name
        private static void setDefaultVisibleColumns(TableInfo table, String columnNames)
        {
            List<FieldKey> fieldKeys = new LinkedList<>();

            for (String name : columnNames.split(",\\s*"))
                fieldKeys.add(FieldKey.fromString(name));

            table.setDefaultVisibleColumns(fieldKeys);
        }


        // Leave all columns as default visible, except for columnsToRemove
        private static TableInfo removeFromDefaultVisibleColumns(TableInfo table, String columnsToRemove)
        {
            Set<String> removeColumns = Sets.newCaseInsensitiveHashSet(columnsToRemove.split(",\\s*"));

            List<FieldKey> keys = table.getDefaultVisibleColumns();
            List<FieldKey> visibleColumns = new ArrayList<>(keys.size());

            for (FieldKey key : keys)
                if (!removeColumns.contains(key.getName()))
                    visibleColumns.add(key);

            table.setDefaultVisibleColumns(visibleColumns);

            return table; // For convenience
        }
    }

    /** All of the haplotype types, such as mhcA, mhcB, etc (not specific assignments like A001, A002a, etc) used in this container */
    private SortedMap<Integer, Map<String, String>> getAllHaplotypes(TableInfo haplotypeTypes)
    {
        if (_allHaplotypes == null)
        {
            SQLFragment sql = new SQLFragment("SELECT DISTINCT h.Type, hap.sortorder, hap.prefix FROM ");
            sql.append(getDbSchema().getTable("Haplotype"), "h");
            sql.append(" JOIN ");
            sql.append(haplotypeTypes, "hap");
            sql.append(" ON h.Type=hap.Type");
            sql.append(" WHERE h.Container = ?");
            sql.add(getContainer());
            sql.append(" ORDER BY hap.sortorder");

            _allHaplotypes = new TreeMap<>();
            try (TableResultSet results = new SqlSelector(getDbSchema(), sql).getResultSet())
            {
                for (Map<String, Object> result : results)
                {
                    Map<String, String> prefixType = new HashMap<>();
                    prefixType.put("type", (String) result.get("type"));
                    prefixType.put("prefix", (String) result.get("prefix"));

                    _allHaplotypes.put((Integer) result.get("sortorder"), prefixType);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }
        return _allHaplotypes;
    }

    /** Do the default thing provided by the superclass */
    private QueryView createDefaultQueryView(ViewContext context, QuerySettings settings, BindException errors)
    {
        return super.createView(context, settings, errors);
    }

    static
    {
        Set<String> names = new LinkedHashSet<>();

        for (TableType type : TableType.values())
        {
            names.add(type.toString());
        }

        TABLE_NAMES = Collections.unmodifiableSet(new CaseInsensitiveHashSet(names));
    }

    private ExprColumn createConcatenatedHaplotypeColumn(SimpleUserSchema.SimpleTable table, boolean fromAnimal)
    {
        List<SQLFragment> haplotypeSQLs = new ArrayList<>();
        TableInfo haplotypeTypes = QueryService.get().getUserSchema(getUser(), getContainer(), "lists").getTable("HaplotypeTypes");

        List<String> prefixes = new ArrayList<>();

        if (haplotypeTypes == null)
        {
            LOG.error("HaplotypeTypes List is not found. This list must have columns Type, Prefix and SortOrder.");
            return null;
        }
        // Build up an ordered list of haplotype assignments. We want mhcA1, mhcA2, mhcB1, mhcB2, etc, which means
        // that we can't use a simple GROUP_CONCAT
        for (Map<String, String> haplotypeType : getAllHaplotypes(haplotypeTypes).values())
        {
            haplotypeSQLs.add(getHaplotypeSelect(haplotypeType, 1, fromAnimal, haplotypeTypes.getSqlDialect()));
            haplotypeSQLs.add(new SQLFragment("', '"));
            haplotypeSQLs.add(getHaplotypeSelect(haplotypeType, 2, fromAnimal, haplotypeTypes.getSqlDialect()));
            haplotypeSQLs.add(new SQLFragment("', '"));

            if (haplotypeType.get("prefix") != null)
            {
                prefixes.add(haplotypeType.get("prefix"));
            }
        }
        // Add a final marker to make replacement simpler
        haplotypeSQLs.add(new SQLFragment("'" + NULL_HAPLOTYPE_MARKER + "'"));

        // We need to strip out the unassigned, empty markers
        // Example concatenated values, pre-replacement, are '~, A001, ~, B004, B023, ~'; '~, ~, ~, ~, ~, ~'
        SQLFragment concatSQL = new SQLFragment("REPLACE(REPLACE(REPLACE(");
        concatSQL.append(table.getSqlDialect().concatenate(haplotypeSQLs.toArray(new SQLFragment[haplotypeSQLs.size()])));
        concatSQL.append(", '" + NULL_HAPLOTYPE_MARKER + ", ', ''), ', " + NULL_HAPLOTYPE_MARKER + "', ''), '" + NULL_HAPLOTYPE_MARKER + "', '')");

        ExprColumn result = new ExprColumn(table, "ConcatenatedHaplotypes", concatSQL, JdbcType.VARCHAR);
        // Ignore meta data if not configured
        String haplotypesQuery = new NonValidatingGenotypingFolderSettings(getContainer()).getHaplotypesQuery();

        if (haplotypesQuery != null)
        {
            final QueryHelper qHelper = new GenotypingQueryHelper(getContainer(), getUser(), haplotypesQuery);
            final TableInfo haplotypeTableInfo = qHelper.getTableInfo(table.getContainerFilter());
            if (haplotypeTableInfo != null)
            {
                result.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new ConcatenatedHaplotypesDisplayColumn(colInfo, getContainer(), haplotypeTableInfo, prefixes);
                    }
                });
            }
        }
        return result;
    }

    /** Generate SQL that pulls the specific haplotype assignment for a given type (mhcA, for example) and number (1 or 2) */
    private SQLFragment getHaplotypeSelect(Map<String, String> haplotypeType, int diploidNumber, boolean fromAnimal, SqlDialect dialect)
    {
        SQLFragment sql = new SQLFragment();
        if (haplotypeType.get("prefix") != null)
        {
            sql.append("(SELECT (CASE WHEN (MIN(h.Name) IS NOT NULL) THEN (CASE WHEN (");
            sql.append(dialect.sqlLocate(new SQLFragment("?", haplotypeType.get("prefix").substring(0, haplotypeType.get("prefix").length() - 1)), new SQLFragment("MIN(h.Name)")));
            sql.append("= 1) THEN (MIN(h.Name)) ELSE (");
            sql.append(dialect.concatenate(new SQLFragment("?", haplotypeType.get("prefix")), new SQLFragment("MIN(h.Name)")));
            sql.append(")END) ELSE '" + NULL_HAPLOTYPE_MARKER + "' END) FROM ");
        }
        else
        {
            sql.append("(SELECT COALESCE(MIN(h.Name), '" + NULL_HAPLOTYPE_MARKER + "') FROM ");
        }
        sql.append(getDbSchema().getTable("Haplotype"), "h");
        sql.append(", ");
        sql.append(getDbSchema().getTable("AnimalHaplotypeAssignment"), "aha");
        if (fromAnimal)
        {
            sql.append(", ");
            sql.append(getDbSchema().getTable("AnimalAnalysis"), "aa");
            sql.append(" WHERE h.RowId = aha.HaplotypeId AND aha.AnimalAnalysisId = aa.RowId AND aa.Enabled = ? AND aa.AnimalId = ");
            sql.add(true);
        }
        else
        {
            sql.append(" WHERE h.RowId = aha.HaplotypeId AND aha.AnimalAnalysisId = ");
        }
        sql.append(ExprColumn.STR_TABLE_ALIAS);
        sql.append(".RowId AND h.Type = ?");
        sql.add(haplotypeType.get("type"));
        sql.append(" AND aha.DiploidNumber = ");
        sql.append(Integer.toString(diploidNumber));
        sql.append(")");
        return sql;
    }

    public static void register(final GenotypingModule module)
    {
        DefaultSchema.registerProvider(NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new GenotypingQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public GenotypingQuerySchema(User user, Container container)
    {
        this(user, container, null);
    }

    public GenotypingQuerySchema(User user, Container container, @Nullable Integer analysisId)
    {
        super(NAME, "Contains genotyping data", user, container, GS.getSchema());
        _analysisId = analysisId;
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
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

            return TableType.Matches.createTable(this, cf, analysisId);
        }

        TableType type = findTableType(name);

        return type == null ? null : type.createTable(this, cf);
    }

    @Nullable
    private TableType findTableType(String name)
    {
        if (TABLE_NAMES.contains(name))
        {
            // Can't just use TableType.valueOf() because we need to be case-insensitive
            for (TableType tableType : TableType.values())
            {
                if (name.equalsIgnoreCase(tableType.name()))
                {
                    return tableType;
                }
            }
        }
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> names = new LinkedHashSet<>();

        for (TableType type : TableType.values())
        {
            if (type.isAvailable(this))
            {
                names.add(type.toString());
            }
        }
        return names;
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        TableType type = findTableType(settings.getQueryName());
        return type == null ? super.createView(context, settings, errors) : type.createQueryView(context, settings, errors, this);
    }

    @Override
    public String getDomainURI(String queryName)
    {
        TableInfo table = getTable(queryName);
        if (table == null)
            throw new NotFoundException("Table '" + queryName + "' not found in this container '" + getContainer().getPath() + "'.");

        if (table instanceof SimpleUserSchema.SimpleTable)
            return ((SimpleUserSchema.SimpleTable)table).getDomainURI();
        return null;
    }
}
