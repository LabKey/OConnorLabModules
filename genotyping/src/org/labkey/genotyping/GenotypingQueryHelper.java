/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

/**
 * User: bbimber
 * Date: 7/18/12
 * Time: 4:08 PM
 */
public class GenotypingQueryHelper extends QueryHelper
{
    public static final String RUN_NUM = "run_num";
    public static final String LIBRARY_NUMBER = "library_number";

    public GenotypingQueryHelper(Container c, User user, @NotNull String schemaQueryView)
    {
        super(c, user, schemaQueryView.split(GenotypingFolderSettings.SEPARATOR));
    }

    public static void validateSamplesQuery(TableInfo table)
    {
        if (table == null)
        {
            throw new NotFoundException("No samples query found. It may not be configured, or it may be pointing a query that doesn't exist.");
        }

        if (null == table.getColumn(LIBRARY_NUMBER))
            throw new NotFoundException("Samples query must include a '" + LIBRARY_NUMBER + "' column");
    }

    public static void validateRunsQuery(TableInfo table)
    {
        if (table == null)
        {
            throw new NotFoundException("No runs query found. It may not be configured, or it may be pointing to a query that doesn't exist.");
        }

        ColumnInfo runNumColumn = table.getColumn(RUN_NUM);
        if (runNumColumn == null)
        {
            throw new NotFoundException("Runs query is expected to have a '" + GenotypingQueryHelper.RUN_NUM + "' column that is an Integer, but no column was found");
        }
        if (runNumColumn.getJdbcType() != JdbcType.INTEGER)
        {
            throw new NotFoundException("Runs query is expected to have a '" + GenotypingQueryHelper.RUN_NUM + "' column that is an Integer, but is was of type " + runNumColumn.getJdbcType());
        }
    }
}
