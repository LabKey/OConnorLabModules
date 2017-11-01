/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.NotFoundException;

import java.sql.SQLException;
import java.util.HashMap;
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

    public Results selectSamples(Container c, User user, GenotypingRun run, String columnNames, String action) throws SQLException
    {
        QueryHelper qHelper = validateSamplesQuery(c, user, run, action);
        MetaDataRun metaDataRun = validateRun(user, run, action);

        SimpleFilter extraFilter = new SimpleFilter(FieldKey.fromParts(GenotypingQueryHelper.LIBRARY_NUMBER), metaDataRun.getSampleLibrary());

        List<FieldKey> fieldKeys = new LinkedList<>();

        for (String name : columnNames.split(",\\s*"))
            fieldKeys.add(FieldKey.fromString(name));

        return qHelper.select(fieldKeys, extraFilter);
    }

    public Results selectSamples(Container c, User user, GenotypingRun run, String action) throws SQLException
    {
        QueryHelper qHelper = validateSamplesQuery(c, user, run, action);
        MetaDataRun metaDataRun = validateRun(user, run, action);
        return qHelper.select(qHelper.getTableInfo().getDefaultVisibleColumns(), null);
    }

    public Map<Integer, Object> getSampleIdsFromSamplesList(Container c, User user, GenotypingRun run, String action) throws SQLException
    {
        QueryHelper qHelper = validateSamplesQuery(c, user, run, action);
        MetaDataRun metaDataRun = validateRun(user, run, action);
        Map<Integer, Object> sampleIds = new HashMap<>();

        Results results = qHelper.select(qHelper.getTableInfo().getDefaultVisibleColumns(), null);
        ColumnInfo keyColumn = qHelper.getTableInfo().getColumn("Key");
        if (null == keyColumn)
            throw new IllegalStateException("SampleManager: Expected Key column to be able to get sample.");
        FieldKey fieldKey = FieldKey.fromString(keyColumn.getAlias());

        while(results.next())
        {
            Map<FieldKey, Object> fieldKeyRowMap = results.getFieldKeyRowMap();
            Integer sampleIdFromSamplesList = (Integer) fieldKeyRowMap.get(fieldKey);
            sampleIds.put(sampleIdFromSamplesList, null);
        }
        results.close();
        return sampleIds;
    }


    private QueryHelper validateSamplesQuery(Container c, User user, GenotypingRun run, String action)
    {
        ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(c, user, action);
        QueryHelper qHelper = new GenotypingQueryHelper(c, user, settings.getSamplesQuery());

        TableInfo ti = qHelper.getTableInfo();
        GenotypingQueryHelper.validateSamplesQuery(ti);

        return qHelper;
    }

    private MetaDataRun validateRun(User user, GenotypingRun run, String action)
    {
        //Issue 15663: Avoid NullPointerException if the metadataRun is not found
        if (null == run)
            throw new NotFoundException("No run was provided");

        MetaDataRun metaDataRun = run.getMetaDataRun(user, action);
        if (null == metaDataRun)
            throw new NotFoundException("Could not find run with MetaDataId: " + run.getMetaDataId());

        return metaDataRun;
    }

    public static class SampleIdFinder
    {
        private final Set<String> _sampleKeyColumns;
        private final Map<SampleKey, Integer> _map;
        private final Map<Integer, SampleKey> _sampleIdMap;
        private static final String SELECT_COLUMNS = MID5_COLUMN_NAME + "/mid_name, " + MID3_COLUMN_NAME + "/mid_name, " + AMPLICON_COLUMN_NAME + ", " + KEY_COLUMN_NAME;
        private static final String SELECT_COLUMNS_WITHOUT_LOOKUP = MID5_COLUMN_NAME + ", " + MID3_COLUMN_NAME + ", " + AMPLICON_COLUMN_NAME + ", " + KEY_COLUMN_NAME;
        private static final int SELECT_COLUMN_COUNT = 4;

        public SampleIdFinder(GenotypingRun run, User user, Set<String> sampleKeyColumns, String action) throws SQLException
        {
            _sampleKeyColumns = sampleKeyColumns;
            _map = new LinkedHashMap<>();
            _sampleIdMap = new LinkedHashMap<>();

            Results rs = null;

            try
            {
                if(null == run)
                    throw new IllegalArgumentException("Genotyping run was null.");

                // Create the [5' MID, 3' MID, Amplicon] -> sample id mapping for this run
                rs = SampleManager.get().selectSamples(run.getContainer(), user, run, SELECT_COLUMNS, action);

                Map<FieldKey, ColumnInfo> map = rs.getFieldMap();

                // Check that samples query includes all the necessary columns... fail with a decent error message if it doesn't
                if (map.size() < SELECT_COLUMN_COUNT)
                {
                    //Do not require fivemid and threemid fields be a valid barcode identifiers. If it is, link it. Else, import as text.
                    rs = SampleManager.get().selectSamples(run.getContainer(), user, run, SELECT_COLUMNS_WITHOUT_LOOKUP, action);

                    map = rs.getFieldMap();

                    if (map.size() < SELECT_COLUMN_COUNT)
                    {
                        String actual = StringUtils.join(map.keySet(), ", ").toLowerCase();
                        int diff = SELECT_COLUMN_COUNT - map.size();
                        String message = "Samples query returned " + map.size() + " columns instead of " + SELECT_COLUMN_COUNT + ". Expected \"" + SELECT_COLUMNS + "\" but \"" + actual + "\" was returned. You need to add or rename " + (1 == diff ? "a column." : diff + " columns.");
                        throw new IllegalStateException(message);
                    }
                }

                while (rs.next())
                {
                    // Use getObject() to allow null values
                    SampleKey key = getSampleKey(rs.getObject(1), rs.getObject(2), rs.getObject(3));

                    //Resolve the sampleId for non-unique key where one or all three values are null.
                    if(run.getPlatform().equals(GenotypingManager.SEQUENCE_PLATFORMS.ILLUMINA.toString())
                            && (key._mid3 == null || key._mid5 == null || key._amplicon == null))
                    {
                        _sampleIdMap.put(rs.getInt(4), key);
                        continue;
                    }

                    Integer previousId = _map.put(key, rs.getInt(4));

                    if (null != previousId)
                        throw new IllegalStateException("The sample list is mis-configured for an entry with key " + key + " that maps to more than one sample in the library.  Please correct your sample list and retry the job.");
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }


        private SampleKey getSampleKey(Object mid5, Object mid3, Object amplicon)
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

        public boolean isValidSampleKey(int key)
        {
            return (_map.containsValue(key) || _sampleIdMap.containsKey(key));
        }
    }

    private static class SampleKey
    {
        private final Object _mid5;
        private final Object _mid3;
        private final Object _amplicon;

        private SampleKey(Object mid5, Object mid3, Object amplicon)
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
                    ", amplicon=" + (null != _amplicon ? "'" + _amplicon + "'" : null) +
                    "}";
        }
    }
}
