/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

package org.labkey.genotyping.galaxy;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * User: adam
 * Date: Sep 24, 2010
 * Time: 11:56:14 AM
 */

public class GalaxyManager
{
    private static final Logger LOG = Logger.getLogger(GalaxyManager.class);
    private static final GalaxyManager _instance = new GalaxyManager();

    private GalaxyManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GalaxyManager get()
    {
        return _instance;
    }

    private static final String FOLDER_CATEGORY = "GalaxySettings";
    private static final String GALAXY_URL = "GalaxyURL";

    public void saveSettings(Container c, GalaxyFolderSettings settings)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(c, FOLDER_CATEGORY, true);
        map.put(GALAXY_URL, settings.getGalaxyURL());
        map.save();
    }

    public GalaxyFolderSettings getSettings(final Container c)
    {
        return new GalaxyFolderSettings() {
            private final Map<String, String> map = PropertyManager.getProperties(c, FOLDER_CATEGORY);

            @Override
            public String getGalaxyURL()
            {
                return map.get(GALAXY_URL);
            }
        };
    }

    private static final String USER_CATEGORY = "GalaxyUserSettings";
    private static final String GALAXY_KEY = "GalaxyKey";

    public void saveUserSettings(Container c, User user, GalaxyUserSettings userSettings)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(user, c, USER_CATEGORY, true);
        map.put(GALAXY_KEY, userSettings.getGalaxyKey());
        map.save();
    }

    public GalaxyUserSettings getUserSettings(final Container c, final User user)
    {
        return new GalaxyUserSettings() {
            private final Map<String, String> map = PropertyManager.getProperties(user, c, USER_CATEGORY);

            @Override
            public String getGalaxyKey()
            {
                return map.get(GALAXY_KEY);
            }
        };
    }
}