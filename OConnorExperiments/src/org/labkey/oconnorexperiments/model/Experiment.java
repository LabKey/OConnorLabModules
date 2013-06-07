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

import org.labkey.api.data.Entity;

import java.util.Collection;

/**
 * User: kevink
 * Date: 5/19/13
 */
public class Experiment extends Entity
{
    private String _experimentType;
    private Collection<Experiment> _parentExperiments;

    public String getExperimentType()
    {
        return _experimentType;
    }

    public void setExperimentType(String experimentType)
    {
        _experimentType = experimentType;
    }

    public Collection<Experiment> lookupParentExperiments()
    {
        if (_parentExperiments == null)
        {
            _parentExperiments = OConnorExperimentsManager.get().getParentExperiments(lookupContainer());
        }
        return _parentExperiments;
    }

}
