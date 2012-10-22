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
import org.labkey.api.collections.CaseInsensitiveHashSet;
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
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MultiValuedLookupColumn;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.query.QuerySchema;
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
import org.labkey.api.view.DataView;
import org.labkey.api.view.NotFoundException;
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
    public static final String NAME = GS.getSchemaName();
    private static final Set<String> TABLE_NAMES;

    @Nullable private final Integer _analysisId;

    @SuppressWarnings({"UnusedDeclaration"})
    public enum TableType
    {
        Runs() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = new FilteredTable(GS.getRunsTable(), schema.getContainer());
                table.wrapAllColumns(true);
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(schema.getUser(), schema.getContainer()));
                setDefaultVisibleColumns(table, "RowId, MetaDataId, Created, CreatedBy");
                //TODO
                //table.setDetailsURL(DetailsURL.fromString(c, "/genotyping/runs.view?run=${RowId}"));

                // Ignore meta data if not configured
                String runsQuery = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getRunsQuery();

                if (null != runsQuery)
                {
                    final QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), runsQuery);

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
                    StringExpression url = qHelper.getTableInfo().getDetailsURL(Collections.singleton(new FieldKey(null, "run_num")), schema.getContainer());
                    if (url != null)
                    {
                        url = DetailsURL.fromString(url.getSource().replace("run_num", "MetaDataId"));
                        metaData.setURL(url);
                    }
                }

                table.setDescription("Contains one row per sequencing run");

                return table;
            }},

        Sequences() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = new FilteredTable(GS.getSequencesTable(), schema.getContainer());
                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getDictionariesTable().getFromSQL("d") + " WHERE d.RowId = " + GS.getSequencesTable() + ".Dictionary) = ?");
                containerCondition.add(schema.getContainer().getId());
                table.addCondition(containerCondition);
                removeFromDefaultVisibleColumns(table, "Dictionary");
                table.setDescription("Contains one row per reference sequence");

                return table;
            }},

        Reads() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = new FilteredTable(GS.getReadsTable(), schema.getContainer())
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
                containerCondition.add(schema.getContainer().getId());
                table.addCondition(containerCondition);
                setDefaultVisibleColumns(table, "Name, SampleId, Sequence, Quality");

                // No validation... ignore sample meta data if not set
                String samplesQuery = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), samplesQuery);
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
                    StringExpression url = samples.getDetailsURL(Collections.singleton(new FieldKey(null, "key")), schema.getContainer());
                    if (url != null)
                    {
                        url = DetailsURL.fromString(url.getSource().replace("Key", "sampleId"));
                        sampleId.setURL(url);
                    }
                }

                table.setDescription("Contains one row per sequencing read");

                return table;
            }},

        MatchReads() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = Reads.createTable(schema);
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
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = new FilteredTable(GS.getAnalysesTable(), schema.getContainer())
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
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(schema.getUser(), schema.getContainer()));
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getRunsTable() + " r WHERE r.RowId = " + GS.getAnalysesTable() + ".Run) = ?");
                containerCondition.add(schema.getContainer().getId());
                table.addCondition(containerCondition);
                setDefaultVisibleColumns(table, "RowId, Run, Created, CreatedBy, Description, SequenceDictionary, SequencesView");
                table.setDescription("Contains one row per genotyping analysis");

                return table;
            }},

        // TODO: Add matches view that displays the original match information (omitting combining/altering).  SQL for this is below
        // SELECT * FROM genotyping.matches matches LEFT JOIN genotyping.matches combined on matches.rowid = combined.parentid WHERE matches.analysis = 38 AND combined.rowid IS NULL order by matches.rowid

        Matches() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                return createTable(schema, null);
            }

            public FilteredTable createTable(GenotypingQuerySchema schema, @Nullable final Integer analysisId)
            {
                FilteredTable table = new FilteredTable(GS.getMatchesTable(), schema.getContainer());
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
                containerCondition.add(schema.getContainer().getId());
                table.addCondition(containerCondition);

                // Normal matches view never shows children of combined / altered / deleted matches
                table.addCondition(new SimpleFilter().addCondition("ParentId", null, CompareType.ISBLANK));
                setDefaultVisibleColumns(table, "SampleId, Reads, Percent, AverageLength, PosReads, NegReads, PosExtReads, NegExtReads, Alleles/AlleleName");

                // Ignore samples meta data if not configured
                String samplesQuery = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), samplesQuery);
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
            FilteredTable createTable(final GenotypingQuerySchema schema)
            {
                FilteredTable table = new FilteredTable(GS.getSequenceFilesTable(), schema.getContainer())
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
                        return new ExpSchema(schema.getUser(), schema.getContainer()).getDatasTable();
                    }
                });

                final ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(schema.getContainer(), schema.getUser(), "query");
                final QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), settings.getSamplesQuery());

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
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                String samplesQuery = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), samplesQuery);
                    TableInfo table = qHelper.getTableInfo();
                    //FilteredTable ft = new FilteredTable(table);
                    //ft.setDescription("Contains sample information and metadata");

                    return (FilteredTable)table;
                }

                return null;
            }},

        RunMetadata() {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                String queryName = new NonValidatingGenotypingFolderSettings(schema.getContainer()).getRunsQuery();

                if (null != queryName)
                {
                    QueryHelper qHelper = new GenotypingQueryHelper(schema.getContainer(), schema.getUser(), queryName);
                    TableInfo table = qHelper.getTableInfo();
                    //FilteredTable ft = new FilteredTable(table);
                    //ft.setDescription("Contains metadata about each genotyping run");

                    return (FilteredTable)table;
                }

                return null;
            }

            @Override
            boolean isAvailable(GenotypingQuerySchema schema)
            {
                return createTable(schema) != null;
            }
        },

        IlluminaTemplates() {
            @Override
            FilteredTable createTable(final GenotypingQuerySchema schema)
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
                        return schema.getContainer().hasPermission(user, perm);
                    }
                };
                table.wrapAllColumns(true);
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(schema.getUser(), schema.getContainer()));
                table.getColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(schema.getUser(), schema.getContainer()));

                setDefaultVisibleColumns(table, "Name, Json, Editable");
                table.setDescription("Contains one row per saved illumina import template");

                return table;
            }
        },
        Animal()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = new SimpleUserSchema.SimpleTable(schema, GS.getAnimalTable());
                table.setDescription("Contains one row per animal");
                return table;
            }
        },
        Haplotype()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = new SimpleUserSchema.SimpleTable(schema, GS.getHaplotypeTable());
                table.setDescription("Contains one row per haplotype");
                return table;
            }
        },
        AnimalAnalysis()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = new SimpleUserSchema.SimpleTable(schema, GS.getAnimalAnalysisTable());

                SQLFragment haplotypeSubselectSql = new SQLFragment("SELECT aha.AnimalAnalysisId, h.Name AS Haplotype, h.Type"
                    + "\n            FROM " + GS.getAnimalHaplotypeAssignmentTable() + " aha"
                    + "\n            JOIN " + GS.getHaplotypeTable() + " h ON aha.HaplotypeId = h.RowId");

                // add a concatenated string of the haplotypes assigned to the given animalId
                SQLFragment haplotypeConcatSql = new SQLFragment("(SELECT " + GS.getSqlDialect().getGroupConcat(new SQLFragment("x.Haplotype"), false, true)
                    + "\n  FROM (SELECT y.AnimalAnalysisId, y.Haplotype FROM "
                    + "\n           (" + haplotypeSubselectSql + ") AS y"
                    + "\n        WHERE y.AnimalAnalysisId = " + ExprColumn.STR_TABLE_ALIAS + ".RowID"
                    + "\n       ) x"
                    + "\n  GROUP BY x.AnimalAnalysisId)");
                ExprColumn haplotypeConcatCol = new ExprColumn(table, "ConcatenatedHaplotypes", haplotypeConcatSql, JdbcType.VARCHAR);
                table.addColumn(haplotypeConcatCol);

                // add min/max values to display the Mamu-A and Mamu-B Haplotypes
                SQLFragment mamuAMinSql = new SQLFragment("(SELECT min(x.Haplotype) "
                        + "\n FROM (" + haplotypeSubselectSql + ") AS x"
                        + "\n WHERE x.Type = 'Mamu-A'"
                        + "\n  AND x.AnimalAnalysisId = " + ExprColumn.STR_TABLE_ALIAS + ".RowID)");
                ExprColumn mamuAMinCol = new ExprColumn(table, "MamuAHaplotype1", mamuAMinSql, JdbcType.VARCHAR);
                mamuAMinCol.setLabel("Mamu-A Haplotype 1");
                table.addColumn(mamuAMinCol);

                SQLFragment mamuAMaxSql = new SQLFragment("(SELECT max(x.Haplotype) "
                        + "\n FROM (" + haplotypeSubselectSql + ") AS x"
                        + "\n WHERE x.Type = 'Mamu-A'"
                        + "\n  AND x.AnimalAnalysisId = " + ExprColumn.STR_TABLE_ALIAS + ".RowID)");
                ExprColumn mamuAMaxCol = new ExprColumn(table, "MamuAHaplotype2", mamuAMaxSql, JdbcType.VARCHAR);
                mamuAMaxCol.setLabel("Mamu-A Haplotype 2");
                table.addColumn(mamuAMaxCol);

                SQLFragment mamuBMinSql = new SQLFragment("(SELECT min(x.Haplotype) "
                        + "\n FROM (" + haplotypeSubselectSql + ") AS x"
                        + "\n WHERE x.Type = 'Mamu-B'"
                        + "\n  AND x.AnimalAnalysisId = " + ExprColumn.STR_TABLE_ALIAS + ".RowID)");
                ExprColumn mamuBMinCol = new ExprColumn(table, "MamuBHaplotype1", mamuBMinSql, JdbcType.VARCHAR);
                mamuBMinCol.setLabel("Mamu-B Haplotype 1");
                table.addColumn(mamuBMinCol);

                SQLFragment mamuBMaxSql = new SQLFragment("(SELECT max(x.Haplotype) "
                        + "\n FROM (" + haplotypeSubselectSql + ") AS x"
                        + "\n WHERE x.Type = 'Mamu-B'"
                        + "\n  AND x.AnimalAnalysisId = " + ExprColumn.STR_TABLE_ALIAS + ".RowID)");
                ExprColumn mamuBMaxCol = new ExprColumn(table, "MamuBHaplotype2", mamuBMaxSql, JdbcType.VARCHAR);
                mamuBMaxCol.setLabel("Mamu-B Haplotype 2");
                table.addColumn(mamuBMaxCol);

                // calculated field for % Unknown = (Total Reads - Identified Reads) / Total Reads
                SQLFragment percUnknownSql = new SQLFragment("((TotalReads-IdentifiedReads)*100.0/TotalReads)");
                ExprColumn percUnknownCol = new ExprColumn(table, "PercentUnknown", percUnknownSql, JdbcType.DOUBLE);
                percUnknownCol.setLabel("% Unknown");
                table.addColumn(percUnknownCol);

                setDefaultVisibleColumns(table, "AnimalId, TotalReads, IdentifiedReads, PercentUnknown, ConcatenatedHaplotypes, MamuAHaplotype1, MamuAHaplotype2, MamuBHaplotype1, MamuBHaplotype2, Enabled");
                table.setDescription("Contains one row per animal in a given run");

                return table;
            }
        },
        AnimalHaplotypeAssignment()
        {
            @Override
            FilteredTable createTable(GenotypingQuerySchema schema)
            {
                FilteredTable table = new SimpleUserSchema.SimpleTable(schema, GS.getAnimalHaplotypeAssignmentTable());
                setDefaultVisibleColumns(table, "AnimalAnalysisId/RunId, AnimalAnalysisId/AnimalId, HaplotypeId");
                table.setDescription("Contains one row per animal/haplotype assignment in a given run");
                return table;
            }
        };

        abstract FilteredTable createTable(GenotypingQuerySchema schema);
        boolean isAvailable(GenotypingQuerySchema schema)
        {
            return true;
        }

        // Special factory method for Matches table, to pass through analysis id (if present)
        FilteredTable createTable(GenotypingQuerySchema schema, @Nullable Integer analysisId)
        {
            return createTable(schema);
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

        TABLE_NAMES = Collections.unmodifiableSet(new CaseInsensitiveHashSet(names));
    }

    public static void register(final GenotypingModule module)
    {
        DefaultSchema.registerProvider(NAME, new DefaultSchema.SchemaProvider()
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
        super(NAME, "Contains genotyping data", user, container, GS.getSchema());
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

            return TableType.Matches.createTable(this, analysisId);
        }

        if (TABLE_NAMES.contains(name))
        {
            // Can't just use TableType.valueOf() because we need to be case-insensitive
            for (TableType tableType : TableType.values())
            {
                if (name.equalsIgnoreCase(tableType.name()))
                {
                    return tableType.createTable(this);
                }
            }
        }

        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> names = new LinkedHashSet<String>();

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
        if(TableType.Samples.name().equalsIgnoreCase(settings.getQueryName()))
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
