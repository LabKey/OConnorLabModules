/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

package org.labkey.oconnorexperiments;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.dialect.SqlDialect;

public class OConnorExperimentsSchema
{
    public static final String NAME = "oconnorexperiments";
    public static final String EXPERIMENTS = "Experiments";
    public static final String EXPERIMENT_TYPE = "ExperimentType";
    private static final OConnorExperimentsSchema _instance = new OConnorExperimentsSchema();

    public static OConnorExperimentsSchema getInstance()
    {
        return _instance;
    }

    private OConnorExperimentsSchema()
    {
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public SchemaTableInfo createTableInfoExperiments()
    {
        return getSchema().getTable(EXPERIMENTS);
    }

    public SchemaTableInfo createTableInfoExperimentType()
    {
        return getSchema().getTable(EXPERIMENT_TYPE);
    }

    public SchemaTableInfo createTableInfoParentExperiments()
    {
        return getSchema().getTable("ParentExperiments");
    }
}
