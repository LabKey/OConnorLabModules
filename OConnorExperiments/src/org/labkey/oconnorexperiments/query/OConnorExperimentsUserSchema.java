/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.oconnorexperiments.OConnorExperimentsSchema;

import java.util.Set;

/**
 * User: kevink
 * Date: 5/17/13
 */
public class OConnorExperimentsUserSchema extends UserSchema
{
    public static final String NAME = "OConnorExperiments";

    public enum Table
    {
        Experiments,
        ExperimentType,
        ParentExperiments
    }

    public static void register(Module module)
    {
        DefaultSchema.registerProvider(NAME, new DefaultSchema.SchemaProvider(module) {
            @Nullable
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new OConnorExperimentsUserSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    OConnorExperimentsUserSchema(User user, Container container)
    {
        super(NAME, null, user, container, OConnorExperimentsSchema.getInstance().getSchema());
    }

    @Nullable
    @Override
    public TableInfo createTable(String name)
    {
        if (Table.Experiments.name().equalsIgnoreCase(name))
            return createExperimentsTable(name);
        else if (Table.ParentExperiments.name().equalsIgnoreCase(name))
            return createParentExperimentsTable(name);
        else if (Table.ExperimentType.name().equalsIgnoreCase(name))
            return createExperimentTypeTable(name);

        return null;
    }

    public TableInfo createTable(Table t)
    {
        switch (t)
        {
            case Experiments:       return createExperimentsTable(t.name());
            case ParentExperiments: return createParentExperimentsTable(t.name());
        }

        return null;
    }

    @Override
    public Set<String> getVisibleTableNames()
    {
        return Sets.newCaseInsensitiveHashSet(
                Table.Experiments.name(),
                Table.ExperimentType.name()
        );
    }

    @Override
    public Set<String> getTableNames()
    {
        return Sets.newCaseInsensitiveHashSet(
                Table.Experiments.name(),
                Table.ParentExperiments.name()
        );
    }

    private TableInfo createExperimentsTable(String name)
    {
        return ExperimentsTable.create(this, name);
    }

    private TableInfo createExperimentTypeTable(String name)
    {
        SimpleUserSchema.SimpleTable table = new SimpleUserSchema.SimpleTable<>(this, OConnorExperimentsSchema.getInstance().createTableInfoExperimentType());
        table.init();
        return table;
    }

    private TableInfo createParentExperimentsTable(String name)
    {
        SimpleUserSchema.SimpleTable table = new SimpleUserSchema.SimpleTable<>(this, OConnorExperimentsSchema.getInstance().createTableInfoParentExperiments());
        table.init();

        //DetailsURL detailsURL = new DetailsURL(QueryService.get().urlDefault(getContainer(), QueryAction.detailsQueryRow, OConnorExperimentsUserSchema.NAME, Table.Experiments.name(), Collections.<String, Object>emptyMap());
        DetailsURL detailsURL = new DetailsURL(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer()));
        table.setDetailsURL(detailsURL);

        return table;
    }
}
