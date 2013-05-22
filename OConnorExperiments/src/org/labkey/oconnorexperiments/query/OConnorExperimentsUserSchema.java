package org.labkey.oconnorexperiments.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.oconnorexperiments.OConnorExperimentsSchema;

import java.util.Arrays;
import java.util.Set;

/**
 * User: kevink
 * Date: 5/17/13
 */
public class OConnorExperimentsUserSchema extends UserSchema
{
    public static final String NAME = "OConnorExperiments";

    public static void register()
    {
        DefaultSchema.registerProvider(NAME, new DefaultSchema.SchemaProvider() {
            @Nullable
            @Override
            public QuerySchema getSchema(DefaultSchema schema)
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
    protected TableInfo createTable(String name)
    {
        if ("Experiments".equalsIgnoreCase(name))
            return createExperimentsTable(name);
        else if ("ParentExperiments".equalsIgnoreCase(name))
            return createParentExperimentsTable(name);

        return null;
    }

    @Override
    public Set<String> getVisibleTableNames()
    {
        return Sets.newCaseInsensitiveHashSet(
                "Experiments"
        );
    }

    @Override
    public Set<String> getTableNames()
    {
        return Sets.newCaseInsensitiveHashSet(
                "Experiments",
                "ParentExperiments"
        );
    }

    private TableInfo createExperimentsTable(String name)
    {
        return ExperimentsTable.create(this, name);
    }

    private TableInfo createParentExperimentsTable(String name)
    {
        //return new FilteredTable<OConnorExperimentsUserSchema>()
        return null;
    }
}
