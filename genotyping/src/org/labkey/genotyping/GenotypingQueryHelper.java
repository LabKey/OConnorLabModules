package org.labkey.genotyping;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.security.User;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 7/18/12
 * Time: 4:08 PM
 */
public class GenotypingQueryHelper extends QueryHelper
{
    public GenotypingQueryHelper(Container c, User user, @NotNull String schemaQueryView)
    {
        super(c, user, schemaQueryView.split(GenotypingFolderSettings.SEPARATOR));
    }
}
