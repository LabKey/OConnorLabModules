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
package org.labkey.genotyping.sequences;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ResultSetIterator;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.writer.FastaEntry;
import org.labkey.api.writer.FastaWriter;
import org.labkey.api.writer.ResultSetFastaGenerator;
import org.labkey.genotyping.GenotypingFolderSettings;
import org.labkey.genotyping.GenotypingManager;
import org.labkey.genotyping.GenotypingSchema;
import org.labkey.genotyping.QueryHelper;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Sep 24, 2010
 * Time: 3:14:15 PM
 */
public class SequenceManager
{
    private static final SequenceManager _instance = new SequenceManager();

    private SequenceManager()
    {
        // prevent external construction with a private default constructor
    }


    public static SequenceManager get()
    {
        return _instance;
    }


    public void loadSequences(Container c, User user) throws SQLException
    {
        Map<String, Object> dictionary = new HashMap<String, Object>();
        dictionary.put("container", c);
        Table.insert(user, GenotypingSchema.get().getDictionariesTable(), dictionary);
        int dictionaryId = getCurrentDictionary(c).getRowId();

        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);
        QueryHelper qHelper = new QueryHelper(c, user, settings.getSequencesQuery());

        SimpleFilter viewFilter = qHelper.getViewFilter();
        viewFilter.addCondition("file_active", 1);
        TableInfo source = qHelper.getTableInfo();
        TableInfo destination = GenotypingSchema.get().getSequencesTable();

        ResultSet rs = null;

        try
        {
            rs = Table.select(source, Table.ALL_COLUMNS, viewFilter, null);
            ResultSetIterator iter = ResultSetIterator.get(rs);

            while (iter.hasNext())
            {
                Map<String, Object> map = iter.next();
                Map<String, Object> inMap = new HashMap<String, Object>(map.size() * 2);

                // TODO: ResultSetIterator should have a way to map column names
                for (Map.Entry<String, Object> entry : map.entrySet())
                {
                    String key = entry.getKey().replaceAll("_", "");
                    inMap.put(key, entry.getValue());
                }

                // Skip empty sequences.  TODO: remove this check once wisconsin eliminates empty sequences
                if (StringUtils.isBlank((String)inMap.get("sequence")))
                    continue;

                inMap.put("dictionary", dictionaryId);
                Table.insert(user, destination, inMap);
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        destination.getSqlDialect().updateStatistics(destination);
    }


    public void writeFasta(Container c, User user, @Nullable String sequencesViewName, File destination) throws SQLException, IOException
    {
        ResultSet rs = null;

        try
        {
            rs = selectSequences(c, user, getCurrentDictionary(c), sequencesViewName, "AlleleName,Sequence");

            FastaWriter<FastaEntry> fw = new FastaWriter<FastaEntry>(new ResultSetFastaGenerator(rs) {
                @Override
                public String getHeader(ResultSet rs) throws SQLException
                {
                    return rs.getString("AlleleName");
                }

                @Override
                public String getSequence(ResultSet rs) throws SQLException
                {
                    return rs.getString("Sequence");
                }
            });

            fw.write(destination);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    // Throws IllegalStateException if references sequences have not been loaded
    public @NotNull SequenceDictionary getCurrentDictionary(Container c) throws SQLException
    {
        return getCurrentDictionary(c, true);
    }


    // Throws or returns null, depending on value of throwIfNotLoaded flag
    public SequenceDictionary getCurrentDictionary(Container c, boolean throwIfNotLoaded) throws SQLException
    {
        Integer max = Table.executeSingleton(GenotypingSchema.get().getSchema(),
                "SELECT MAX(RowId) FROM " + GenotypingSchema.get().getDictionariesTable() + " WHERE Container = ?",
                new Object[]{c}, Integer.class);

        if (null == max)
        {
            if (throwIfNotLoaded)
                throw new IllegalStateException("You must load reference sequences before initiating an analysis");
            else
                return null;
        }

        return getSequenceDictionary(c, max);
    }


    public Map<String, Integer> getSequences(Container c, User user, SequenceDictionary dictionary, String sequencesViewName) throws SQLException
    {
        ResultSet rs = null;
        HashMap<String, Integer> sequences = new HashMap<String, Integer>();

        try
        {
            rs = selectSequences(c, user, dictionary, sequencesViewName, "AlleleName,RowId");

            while(rs.next())
            {
                Integer previous = sequences.put(rs.getString(1), rs.getInt(2));

                if (null != previous)
                    throw new IllegalStateException("Duplicate allele name: " + rs.getString(1));
            }

            return sequences;
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    private ResultSet selectSequences(Container c, User user, SequenceDictionary dictionary, String sequencesViewName, String columnNames) throws SQLException
    {
        // First, make sure that dictionary exists in this container
        SimpleFilter filter = new SimpleFilter("RowId", dictionary.getRowId());
        filter.addCondition("Container", c);
        TableInfo dictionaries = GenotypingSchema.get().getDictionariesTable();
        ResultSet rs = null;

        try
        {
            rs = Table.select(dictionaries, dictionaries.getColumns("RowId"), filter, null);

            if (!rs.next())
                throw new IllegalStateException("Sequences dictionary does not exist in this folder");

            if (rs.next())
                throw new IllegalStateException("Multiple sequence dictionaries found");
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        // Now select all sequences in this dictionary, applying the specified filter
        GenotypingSchema gs = GenotypingSchema.get();
        QueryHelper qHelper = new QueryHelper(c, user, gs.getSchemaName(), gs.getSequencesTable().getName(), sequencesViewName);
        SimpleFilter viewFilter = qHelper.getViewFilter();
        viewFilter.addCondition("Dictionary", dictionary.getRowId());
        TableInfo ti = GenotypingSchema.get().getSequencesTable();
        return Table.select(ti, ti.getColumns(columnNames), viewFilter, new Sort("RowId"));
    }


    public SequenceDictionary getSequenceDictionary(Container c, int id)
    {
        return Table.selectObject(GenotypingSchema.get().getDictionariesTable(), c, id, SequenceDictionary.class);
    }


    public int getSequenceCount(Container c) throws SQLException
    {
        GenotypingSchema gs = GenotypingSchema.get();
        TableInfo sequences = gs.getSequencesTable();

        SequenceDictionary dictionary = getCurrentDictionary(c, false);

        if (null == dictionary)
            return 0;

        return Table.executeSingleton(sequences.getSchema(), "SELECT CAST(COUNT(*) AS INT) FROM " + sequences + " WHERE Dictionary = ?", new Object[]{dictionary.getRowId()}, Integer.class);
    }
}
