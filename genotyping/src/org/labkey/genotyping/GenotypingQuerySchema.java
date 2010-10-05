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
import java.util.Set;
import java.util.TreeSet;

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
        Set<String> names = new TreeSet<String>();
        names.add(GS.getSequencesTable().getName());
        names.add(GS.getReadsTable().getName());
        names.add(GS.getAnalysesTable().getName());
        names.add(GS.getMatchesTable().getName());
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register()
    {
        DefaultSchema.registerProvider(GS.getSchemaName(), new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new GenotypingQuerySchema(schema.getUser(), schema.getContainer());
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
        if (GS.getSequencesTable().getName().equalsIgnoreCase(name))
        {
            FilteredTable table = new FilteredTable(GS.getSequencesTable(), getContainer());
            table.wrapAllColumns(true);
            SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getDictionariesTable() + " d WHERE d.RowId = " + GS.getSequencesTable() + ".Dictionary) = ?");
            containerCondition.add(getContainer().getId());
            table.addCondition(containerCondition);
            table.setDescription("Contains one row per reference sequence");

            return table;
        }

        if (GS.getReadsTable().getName().equalsIgnoreCase(name))
        {
            FilteredTable table = new FilteredTable(GS.getReadsTable(), getContainer());
            table.wrapAllColumns(true);

            QueryHelper qHelper = new QueryHelper(getContainer(), getUser(), GenotypingManager.get().getSettings(getContainer()).getRunsQuery());
//            qHelper.select() ;
            // TODO: Join to specified runs query, and filter on container
/*
            SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getDictionariesTable() + " d WHERE d.RowId = " + GS.getSequencesTable() + ".Dictionary) = ?");
            containerCondition.add(getContainer().getId());
            table.addCondition(containerCondition);
             */
            table.setDescription("Contains one row per genotyping read");

            return table;
        }

        if (GS.getMatchesTable().getName().equalsIgnoreCase(name))
        {
            FilteredTable table = new FilteredTable(GS.getMatchesTable(), getContainer());
            table.wrapAllColumns(true);
            SQLFragment containerCondition = new SQLFragment("(SELECT Container FROM " + GS.getAnalysesTable() + " a WHERE a.RowId = " + GS.getMatchesTable() + ".Analysis) = ?");
            containerCondition.add(getContainer().getId());
            table.addCondition(containerCondition);
            table.setDescription("Contains one row per genotyping match");

            return table;
        }

        if (GS.getAnalysesTable().getName().equalsIgnoreCase(name))
        {
            FilteredTable table = new FilteredTable(GS.getAnalysesTable(), getContainer());
            table.wrapAllColumns(true);
            table.removeColumn(table.getColumn("Container"));
            table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(getUser(), getContainer()));
            table.setDescription("Contains one row per genotyping analysis");

            return table;
        }

        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }
}
