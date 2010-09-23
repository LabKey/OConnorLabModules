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

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;

import java.util.Map;

public class GalaxyManager
{
    private static final Logger LOG = Logger.getLogger(GalaxyManager.class);
    private static final GalaxyManager _instance = new GalaxyManager();

    public static final String PROPERTIES_FILE_NAME = "properties.xml";
    public static final String SAMPLES_FILE_NAME = "samples.txt";
    public static final String READS_FILE_NAME = "reads.txt";
    public static final String MATCHES_FILE_NAME = "matches.txt";

    private GalaxyManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GalaxyManager get()
    {
        return _instance;
    }

    private static final String SYSTEM_CATEGORY = "GalaxySettings";
    private static final String GALAXY_URL = "GalaxyURL";
    private static final String REFERENCE_SEQUENCES_QUERY = "SequencesQuery";
    private static final String RUNS_QUERY = "RunsQuery";
    private static final String SAMPLES_QUERY = "SamplesQuery";

    public void saveSettings(Container c, GalaxySettings settings)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(c.getId(), SYSTEM_CATEGORY, true);
        map.put(GALAXY_URL, settings.getGalaxyURL());
        map.put(REFERENCE_SEQUENCES_QUERY, settings.getSequencesQuery());
        map.put(RUNS_QUERY, settings.getRunsQuery());
        map.put(SAMPLES_QUERY, settings.getSamplesQuery());
        PropertyManager.saveProperties(map);
    }

    public GalaxySettings getSettings(final Container c)
    {
        return new GalaxySettings() {
            private final Map<String, String> map = PropertyManager.getProperties(c.getId(), SYSTEM_CATEGORY);

            @Override
            public String getGalaxyURL()
            {
                return map.get(GALAXY_URL);
            }

            @Override
            public String getSequencesQuery()
            {
                return map.get(REFERENCE_SEQUENCES_QUERY);
            }

            @Override
            public String getRunsQuery()
            {
                return map.get(RUNS_QUERY);
            }

            @Override
            public String getSamplesQuery()
            {
                return map.get(SAMPLES_QUERY);
            }
        };
    }

    private static final String USER_CATEGORY = "GalaxyUserSettings";
    private static final String GALAXY_KEY = "GalaxyKey";

    public void saveUserSettings(Container c, User user, GalaxyUserSettings userSettings)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user.getUserId(), c.getId(), USER_CATEGORY, true);
        map.put(GALAXY_KEY, userSettings.getGalaxyKey());
        PropertyManager.saveProperties(map);
    }

    public GalaxyUserSettings getUserSettings(final Container c, final User user)
    {
        return new GalaxyUserSettings() {
            private final Map<String, String> map = PropertyManager.getProperties(user.getUserId(), c.getId(), USER_CATEGORY);

            @Override
            public String getGalaxyKey()
            {
                return map.get(GALAXY_KEY);
            }
        };
    }

    public GenotypingRun getRun(Container c, User user, int runId)
    {
        GalaxySettings settings = GalaxyManager.get().getSettings(c);
        TableInfo runs = GenotypingController.getTableInfo(settings.getRunsQuery(), c, user);
        GenotypingRun run = Table.selectObject(runs, runId, GenotypingRun.class);

        return run;
    }
}