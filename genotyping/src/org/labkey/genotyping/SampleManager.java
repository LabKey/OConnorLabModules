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

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.Report;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: Oct 26, 2010
 * Time: 1:49:15 PM
 */
public class SampleManager
{
    private static final SampleManager INSTANCE = new SampleManager();

    private SampleManager()
    {
    }

    public static SampleManager get()
    {
        return INSTANCE;
    }

    public Report.Results selectSamples(Container c, User user, GenotypingRun run, String columnNames) throws SQLException
    {
        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);
        QueryHelper qHelper = new QueryHelper(c, user, settings.getSamplesQuery());
        SimpleFilter extraFilter = new SimpleFilter("library_number", run.getMetaDataRun(user).getSampleLibrary());

        List<FieldKey> fieldKeys = new LinkedList<FieldKey>();

        for (String name : columnNames.split(",\\s*"))
            fieldKeys.add(FieldKey.fromString(name));

        return qHelper.select(extraFilter, fieldKeys);
    }
}
