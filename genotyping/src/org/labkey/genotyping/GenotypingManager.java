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
import org.labkey.api.security.User;

import java.util.Map;

public class GenotypingManager
{
    private static final Logger LOG = Logger.getLogger(GenotypingManager.class);
    private static final GenotypingManager _instance = new GenotypingManager();

    public static final String PROPERTIES_FILE_NAME = "properties.xml";
    public static final String SAMPLES_FILE_NAME = "samples.txt";
    public static final String READS_FILE_NAME = "reads.txt";
    public static final String MATCHES_FILE_NAME = "matches.txt";
    public static final String SEQUENCES_FILE_NAME = "sequences.fasta";

    private GenotypingManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GenotypingManager get()
    {
        return _instance;
    }

    private static final String FOLDER_CATEGORY = "GenotypingSettings";
    private static final String REFERENCE_SEQUENCES_QUERY = "SequencesQuery";
    private static final String RUNS_QUERY = "RunsQuery";
    private static final String SAMPLES_QUERY = "SamplesQuery";

    public void saveSettings(Container c, GenotypingFolderSettings settings)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(c.getId(), FOLDER_CATEGORY, true);
        map.put(REFERENCE_SEQUENCES_QUERY, settings.getSequencesQuery());
        map.put(RUNS_QUERY, settings.getRunsQuery());
        map.put(SAMPLES_QUERY, settings.getSamplesQuery());
        PropertyManager.saveProperties(map);
    }

    public GenotypingFolderSettings getSettings(final Container c)
    {
        return new GenotypingFolderSettings() {
            private final Map<String, String> map = PropertyManager.getProperties(c.getId(), FOLDER_CATEGORY);

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

    public GenotypingRun getRun(Container c, User user, int runId)
    {
        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);
        QueryHelper qHelper = new QueryHelper(c, user, settings.getRunsQuery());
        return Table.selectObject(qHelper.getTableInfo(), runId, GenotypingRun.class);
    }
}