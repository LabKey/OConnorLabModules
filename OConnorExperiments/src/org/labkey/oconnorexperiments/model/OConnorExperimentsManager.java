/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package org.labkey.oconnorexperiments.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.oconnorexperiments.OConnorExperimentsModule;
import org.labkey.oconnorexperiments.OConnorExperimentsSchema;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class OConnorExperimentsManager
{
    private static final OConnorExperimentsManager _instance = new OConnorExperimentsManager();

    private OConnorExperimentsManager()
    {
        // prevent external construction with a private default constructor
    }

    public static OConnorExperimentsManager get()
    {
        return _instance;
    }

    /**
     * Get an Experiment Entity row that only includes values from the OConnorExperiments hard-table.
     * For example, the Created and CreatedBy values of the Experiment entity won't be available, but
     * can be retrieved from the workbook Container object instead.
     *
     * @return Columns from the hard-table and not the rolled up Experiment/Container merged object.
     */
    public Experiment getExperiment(Container workbook)
    {
        if (!workbook.isWorkbook())
            throw new IllegalArgumentException("workbook");

        TableSelector selector = new TableSelector(OConnorExperimentsSchema.getInstance().createTableInfoExperiments(), null, null);
        Experiment experiment = selector.getObject(workbook, workbook, Experiment.class);
        return experiment;
    }

    public Experiment ensureExperiment(Container workbook, User user)
    {
        Experiment experiment = getExperiment(workbook);
        if (experiment != null)
            return experiment;

        return insertExperiment(workbook, user);
    }

    public Experiment insertExperiment(Container workbook, User user)
    {
        if (!workbook.isWorkbook())
            throw new IllegalArgumentException("workbook");

        SchemaTableInfo table = OConnorExperimentsSchema.getInstance().createTableInfoExperiments();
        Experiment experiment = new Experiment();
        experiment.setContainer(workbook.getId());
        return Table.insert(user, table, experiment);
    }

    public void updateModified(Container c, User user)
    {
        if (c == null || user == null || !c.isWorkbook() || !c.getParent().getActiveModules().contains(ModuleLoader.getInstance().getModule(OConnorExperimentsModule.class)))
            return;

        Map<String, Object> row = new HashMap<>();
        row.put("Container", c.getEntityId());
        TableSelector selector = new TableSelector(OConnorExperimentsSchema.getInstance().createTableInfoExperiments(), SimpleFilter.createContainerFilter(c), null);
        if (selector.exists())
        {
            Table.update(user, OConnorExperimentsSchema.getInstance().createTableInfoExperiments(), row, c.getEntityId());
        }
    }

    public Collection<Container> parentExperimentContainers(Container experimentContainer)
    {
        Filter filter = SimpleFilter.createContainerFilter(experimentContainer);
        TableSelector selector = new TableSelector(OConnorExperimentsSchema.getInstance().createTableInfoParentExperiments(), filter, null);
        Container[] parentExperimentContainers = selector.getArray(Container.class);
        return Arrays.asList(parentExperimentContainers);
    }

    public Collection<Experiment> getParentExperiments(Container experimentContainer)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT experiments.* FROM\n");
        sql.append(OConnorExperimentsSchema.getInstance().createTableInfoExperiments(), "experiments").append(",\n");
        sql.append(OConnorExperimentsSchema.getInstance().createTableInfoParentExperiments(), "parentExperiments").append("\n");
        sql.append("WHERE parentExperiments.Container = ?");
        sql.append("  AND parentExperiments.ParentExperiment = experiments.Container\n");
        sql.add(experimentContainer);

        SqlSelector selector = new SqlSelector(OConnorExperimentsSchema.getInstance().getSchema(), sql);
        Experiment[] parentExperiments = selector.getArray(Experiment.class);
        return Arrays.asList(parentExperiments);
    }

}
