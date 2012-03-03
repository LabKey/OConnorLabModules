package org.labkey.genotyping;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.NotFoundException;

/**
* User: adam
* Date: 3/2/12
* Time: 9:59 AM
*/
public class ValidatingGenotypingFolderSettings extends NonValidatingGenotypingFolderSettings
{
    private final User _user;
    private final String _action;

    public ValidatingGenotypingFolderSettings(Container c, User user, String action)
    {
        super(c);
        _user = user;
        _action = action;
    }

    @Override
    public @NotNull String getSequencesQuery()
    {
        //noinspection ConstantConditions
        return super.getSequencesQuery();
    }

    @Override
    public @NotNull String getRunsQuery()
    {
        //noinspection ConstantConditions
        return super.getRunsQuery();
    }

    @Override
    public @NotNull String getSamplesQuery()
    {
        //noinspection ConstantConditions
        return super.getSamplesQuery();
    }

    @Override
    protected @NotNull String getQuery(GenotypingManager.Setting setting)
    {
        String query = super.getQuery(setting);

        if (null != query)
            return query;

        String adminName = _c.hasPermission(_user, AdminPermission.class) ? "you" : "an administrator";
        throw new NotFoundException("Before " + _action + ", " + adminName + " must configure a query specifying " + setting.getDescription() + " via the genotyping admin page");
    }
}
