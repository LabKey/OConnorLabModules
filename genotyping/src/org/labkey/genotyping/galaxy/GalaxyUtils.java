/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.NotFoundException;

/**
 * User: adam
 * Date: Oct 12, 2010
 * Time: 4:38:58 PM
 */

// Provides a LabKey-specific way to create a GalaxyServer.  This keeps GalaxyServer very generic and easier to publish.
public class GalaxyUtils
{
    // Throws NotFoundException if either galaxy URL (admin responsibility) or web API key (user responsibility) isn't configured.
    public static GalaxyServer get(Container c, User user)
    {
        GalaxyFolderSettings settings = GalaxyManager.get().getSettings(c);

        if (null == settings.getGalaxyURL())
        {
            String who = c.hasPermission(user, AdminPermission.class) ? "you" : "an administrator";
            throw new NotFoundException("To submit data to Galaxy, " + who + " must configure a Galaxy server URL via the genotyping admin page.");
        }

        GalaxyUserSettings userSettings = GalaxyManager.get().getUserSettings(c, user);

        if (null == userSettings.getGalaxyKey())
            throw new NotFoundException("To submit data to Galaxy, you must first configure your Galaxy web API key using the \"my settings\" link");

        return new GalaxyServer(settings.getGalaxyURL(), userSettings.getGalaxyKey());
    }
}
