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
            String advice = c.hasPermission(user, AdminPermission.class) ? "Please configure the Galaxy settings using the \"admin\" link" : "An administrator must configure the Galaxy settings";
            throw new NotFoundException("Galaxy server URL is not configured. " + advice);
        }

        GalaxyUserSettings userSettings = GalaxyManager.get().getUserSettings(c, user);

        if (null == userSettings.getGalaxyKey())
            throw new NotFoundException("You must first configure a Galaxy web API key using the \"my settings\" link");

        return new GalaxyServer(settings.getGalaxyURL(), userSettings.getGalaxyKey());
    }
}
