/*
 * Copyright (c) 2012 LabKey Corporation
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
