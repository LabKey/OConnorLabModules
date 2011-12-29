/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Oct 26, 2010
 * Time: 1:49:15 PM
 */
public class SampleManager
{
    private static final SampleManager INSTANCE = new SampleManager();

    public static final String MID5_COLUMN_NAME = "fivemid";
    public static final String MID3_COLUMN_NAME = "threemid";
    public static final String AMPLICON_COLUMN_NAME = "amplicon";
    public static final String KEY_COLUMN_NAME = "key";

    static final Set<String> POSSIBLE_SAMPLE_KEYS = new CaseInsensitiveHashSet(MID5_COLUMN_NAME, MID3_COLUMN_NAME, AMPLICON_COLUMN_NAME);

    private SampleManager()
    {
    }

    public static SampleManager get()
    {
        return INSTANCE;
    }

    public Results selectSamples(Container c, User user, GenotypingRun run, String columnNames) throws SQLException
    {
        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);
        QueryHelper qHelper = new QueryHelper(c, user, settings.getSamplesQuery());
        SimpleFilter extraFilter = new SimpleFilter("library_number", run.getMetaDataRun(user).getSampleLibrary());

        List<FieldKey> fieldKeys = new LinkedList<FieldKey>();

        for (String name : columnNames.split(",\\s*"))
            fieldKeys.add(FieldKey.fromString(name));

        return qHelper.select(extraFilter, fieldKeys);
    }


    public static class SampleIdFinder
    {
        private final Set<String> _sampleKeyColumns;
        private final Map<SampleKey, Integer> _map;
        private static final String SELECT_COLUMNS = MID5_COLUMN_NAME + "/mid_name, " + MID3_COLUMN_NAME + "/mid_name, " + AMPLICON_COLUMN_NAME + ", " + KEY_COLUMN_NAME;
        private static final int SELECT_COLUMN_COUNT = 4;

        public SampleIdFinder(GenotypingRun run, User user, Set<String> sampleKeyColumns) throws SQLException
        {
            _sampleKeyColumns = sampleKeyColumns;
            _map = new LinkedHashMap<SampleKey, Integer>();

            Results rs = null;

            try
            {
                // Create the [5' MID, 3' MID, Amplicon] -> sample id mapping for this run
                try
                {
                    rs = SampleManager.get().selectSamples(run.getContainer(), user, run, SELECT_COLUMNS);
                }
                catch (NullPointerException e)
                {
                    // An exception could mean samples and meta data runs have not been configured in this container
                    // TODO: selectSamples should return null in this case
                    _map.clear();
                    return;
                }

                Map<FieldKey, ColumnInfo> map = rs.getFieldMap();

                // Check that samples query includes all the necessary columns... fail with a decent error message if it doesn't
                if (map.size() < SELECT_COLUMN_COUNT)
                {
                    String actual = StringUtils.join(map.keySet(), ", ").toLowerCase();
                    int diff = SELECT_COLUMN_COUNT - map.size();
                    String message = "Samples query returned " + map.size() + " columns instead of " + SELECT_COLUMN_COUNT + ". Expected \"" + SELECT_COLUMNS + "\" but \"" + actual + "\" was returned. You need to add or rename " + (1 == diff ? "a column." : diff + " columns.");
                    throw new IllegalStateException(message);
                }

                while (rs.next())
                {
                    // Use getObject() to allow null values
                    SampleKey key = getSampleKey((Integer)rs.getObject(1), (Integer)rs.getObject(2), (String)rs.getObject(3));
                    Integer previousId = _map.put(key, rs.getInt(4));

                    if (null != previousId)
                        throw new IllegalStateException("Ambiguous samples -- " + key + " maps to more than one sample in the library");
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }


        private SampleKey getSampleKey(Integer mid5, Integer mid3, String amplicon)
        {
            return new SampleKey(
                _sampleKeyColumns.contains(MID5_COLUMN_NAME) ? mid5 : null,
                _sampleKeyColumns.contains(MID3_COLUMN_NAME) ? mid3 : null,
                _sampleKeyColumns.contains(AMPLICON_COLUMN_NAME) ? amplicon : null
            );
        }


        public Integer getSampleId(Integer mid5, Integer mid3, String amplicon)
        {
            return _map.get(getSampleKey(mid5, mid3, amplicon));
        }
    }

    private static class SampleKey
    {
        private final Integer _mid5;
        private final Integer _mid3;
        private final String _amplicon;

        private SampleKey(Integer mid5, Integer mid3, String amplicon)
        {
            _mid5 = mid5;
            _mid3 = mid3;
            _amplicon = amplicon;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SampleKey that = (SampleKey) o;

            if (_amplicon != null ? !_amplicon.equals(that._amplicon) : that._amplicon != null) return false;
            if (_mid3 != null ? !_mid3.equals(that._mid3) : that._mid3 != null) return false;
            if (_mid5 != null ? !_mid5.equals(that._mid5) : that._mid5 != null) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _mid5 != null ? _mid5.hashCode() : 0;
            result = 31 * result + (_mid3 != null ? _mid3.hashCode() : 0);
            result = 31 * result + (_amplicon != null ? _amplicon.hashCode() : 0);
            return result;
        }

        @Override
        public String toString()
        {
            return "SampleKey{" +
                    "mid5=" + _mid5 +
                    ", mid3=" + _mid3 +
                    ", amplicon='" + _amplicon + '\'' +
                    '}';
        }
    }
}
