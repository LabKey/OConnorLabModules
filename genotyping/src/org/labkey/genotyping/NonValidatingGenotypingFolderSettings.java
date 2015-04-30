/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;

import java.util.Map;

/**
* User: adam
* Date: 3/2/12
* Time: 9:59 AM
*/
class NonValidatingGenotypingFolderSettings implements GenotypingFolderSettings
{
    private final Map<String, String> _map;
    protected final Container _c;

    NonValidatingGenotypingFolderSettings(Container c)
    {
        _map = PropertyManager.getProperties(c, GenotypingManager.FOLDER_CATEGORY);
        _c = c;
    }

    @Override
    public @Nullable String getSequencesQuery()
    {
        return getQuery(GenotypingManager.Setting.ReferenceSequencesQuery);
    }

    @Override
    public @Nullable String getRunsQuery()
    {
        return getQuery(GenotypingManager.Setting.RunsQuery);
    }

    @Override
    public @Nullable String getSamplesQuery()
    {
        return getQuery(GenotypingManager.Setting.SamplesQuery);
    }

    @Override
    public String getHaplotypesQuery()
    {
        return getQuery(GenotypingManager.Setting.HaplotypesQuery);
    }

    protected @Nullable String getQuery(GenotypingManager.Setting setting)
    {
        return _map.get(setting.getKey());
    }
}
