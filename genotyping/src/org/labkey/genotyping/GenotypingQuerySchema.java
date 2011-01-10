/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.HighlightingDisplayColumn;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;

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

    static
    {
        ColumnInfo alleleName = GS.getSequencesTable().getColumn("AlleleName");
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
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public enum TableType
    {
        Runs() {
            @Override
            FilteredTable createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getRunsTable(), c);
                table.wrapAllColumns(true);
                table.removeColumn(table.getColumn("Container"));
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(user, c));
                setDefaultVisibleColumns(table, "RowId, MetaDataId, Created, CreatedBy");

                String runsQuery = GenotypingManager.get().getSettings(c).getRunsQuery();

                if (null != runsQuery)
                {
                    final QueryHelper qHelper = new QueryHelper(c, user, runsQuery);

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
                FilteredTable table = new FilteredTable(GS.getReadsTable(), c);
                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("Run IN (SELECT Run FROM " + GS.getRunsTable().getFromSQL("r") + " WHERE Container = ?)");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);
                setDefaultVisibleColumns(table, "Name, SampleId, Sequence, Quality");

                String samplesQuery = GenotypingManager.get().getSettings(c).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new QueryHelper(c, user, samplesQuery);
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
                FilteredTable table = new FilteredTable(GS.getAnalysesTable(), c);
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
                FilteredTable table = new FilteredTable(GS.getMatchesTable(), c);
                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("Analysis IN (SELECT a.RowId FROM " + GS.getAnalysesTable().getFromSQL("a") + " INNER JOIN " + GS.getRunsTable().getFromSQL("r") + " ON a.Run = r.RowId WHERE Container = ?)");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);

                // Normal matches view never shows children of combined (or altered) matches
                table.addCondition(new SimpleFilter().addCondition("ParentId", null, CompareType.ISBLANK));
                setDefaultVisibleColumns(table, "SampleId, Reads, Percent, AverageLength, PosReads, NegReads, PosExtReads, NegExtReads, Alleles/AlleleName");

                String samplesQuery = GenotypingManager.get().getSettings(c).getSamplesQuery();

                if (null != samplesQuery)
                {
                    QueryHelper qHelper = new QueryHelper(c, user, samplesQuery);
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
            }};

        abstract FilteredTable createTable(Container c, User user);

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
        super(GS.getSchemaName(), "Contains genotyping data", user, container, GS.getSchema());
    }

    @Override
    protected TableInfo createTable(String name)
    {
        if (TABLE_NAMES.contains(name))
            return TableType.valueOf(name).createTable(getContainer(), getUser());

        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }
}
