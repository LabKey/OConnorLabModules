/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.QueryService;
import org.labkey.oconnorexperiments.OConnorExperimentsSchema;
import org.labkey.oconnorexperiments.query.OConnorExperimentsUserSchema;

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

    public Experiment getExperiment(Container c)
    {
        //Filter filter = SimpleFilter.createContainerFilter(c);
        TableSelector selector = new TableSelector(OConnorExperimentsSchema.getInstance().createTableInfoExperiments(), null, null);
        Experiment experiment = selector.getObject(c, c, Experiment.class);
        return experiment;
    }

}
