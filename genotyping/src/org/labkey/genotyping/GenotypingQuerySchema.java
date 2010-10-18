/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.LinkedHashSet;
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

    @SuppressWarnings({"UnusedDeclaration"})
    public enum TableType
    {
        Runs() {
            @Override
            TableInfo createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getRunsTable(), c);
                table.wrapAllColumns(true);
                table.removeColumn(table.getColumn("Container"));
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(user, c));
                table.setDescription("Contains one row per sequencing run");

                return table;
            }},
        Sequences() {
            @Override
            TableInfo createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getSequencesTable(), c);
                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getDictionariesTable() + " d WHERE d.RowId = " + GS.getSequencesTable() + ".Dictionary) = ?");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);
                table.setDescription("Contains one row per reference sequence");

                return table;
            }},
        Reads() {
            @Override
            TableInfo createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getReadsTable(), c);
                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getRunsTable() + " r WHERE r.RowId = " + GS.getReadsTable() + ".Run) = ?");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);

                // TODO: Join to specified runs query?  Need to address case where query is not defined.
//
//             QueryHelper qHelper = new QueryHelper(getContainer(), getUser(), GenotypingManager.get().getSettings(getContainer()).getRunsQuery());
//            qHelper.select() ;

                table.setDescription("Contains one row per sequencing read");

                return table;
            }},
        Analyses() {
            @Override
            TableInfo createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getAnalysesTable(), c);
                table.wrapAllColumns(true);
                table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(user, c));
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getRunsTable() + " r WHERE r.RowId = " + GS.getAnalysesTable() + ".Run) = ?");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);
                table.setDescription("Contains one row per genotyping analysis");

                return table;
            }},
        Matches() {
            @Override
            TableInfo createTable(Container c, User user)
            {
                FilteredTable table = new FilteredTable(GS.getMatchesTable(), c);
                table.wrapAllColumns(true);
                SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getAnalysesTable() + " a INNER JOIN " + GS.getRunsTable() + " r ON a.Run = r.RowId WHERE a.RowId = " + GS.getMatchesTable() + ".Analysis) = ?");
                containerCondition.add(c.getId());
                table.addCondition(containerCondition);
                table.setDescription("Contains one row per genotyping match");

                return table;

            }};

        abstract TableInfo createTable(Container c, User user);
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
