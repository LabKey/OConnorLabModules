/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ResultSetIterator;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.FastaEntry;
import org.labkey.api.writer.FastaWriter;
import org.labkey.api.writer.ResultSetFastaGenerator;
import org.labkey.genotyping.GenotypingSchema;
import org.labkey.genotyping.QueryHelper;
import org.labkey.genotyping.ValidatingGenotypingFolderSettings;

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
        int dictionaryId = getCurrentDictionary(c, user).getRowId();

        ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(c, user, "loading sequences");
        QueryHelper qHelper = new QueryHelper(c, user, settings.getSequencesQuery());

        SimpleFilter viewFilter = qHelper.getViewFilter();
        TableInfo destination = GenotypingSchema.get().getSequencesTable();
        // If "file_active" column exists then filter on it, #14008
        if (null != destination.getColumn("file_active"))
            viewFilter.addCondition("file_active", 1);
        TableInfo source = qHelper.getTableInfo();

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
            rs = selectSequences(c, user, getCurrentDictionary(c, user), sequencesViewName, "AlleleName,Sequence");

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


    // Throws NotFoundException if references sequences have not been loaded
    public @NotNull SequenceDictionary getCurrentDictionary(Container c, User user)
    {
        return getCurrentDictionary(c, user, true);
    }


    // Throws or returns null, depending on value of throwIfNotLoaded flag
    public SequenceDictionary getCurrentDictionary(Container c, User user, boolean throwIfNotLoaded)
    {
        Integer max = new SqlSelector(GenotypingSchema.get().getSchema(),
            new SQLFragment("SELECT MAX(RowId) FROM " + GenotypingSchema.get().getDictionariesTable() + " WHERE Container = ?", c)).getObject(Integer.class);

        if (null == max)
        {
            if (throwIfNotLoaded)
            {
                // This will throw NotFoundException if the query is not defined yet
                new ValidatingGenotypingFolderSettings(c, user, "creating an analysis").getSequencesQuery();
                // Otherwise, assume sequences haven't been loading yet
                String who = c.hasPermission(user, AdminPermission.class) ? "you" : "an administrator";
                throw new NotFoundException("Before creating an analysis, " + who + " must load reference sequences via the genotyping admin page");
            }
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
        return new TableSelector(GenotypingSchema.get().getDictionariesTable()).getObject(c, id, SequenceDictionary.class);
    }


    public long getCurrentSequenceCount(Container c, User user)
    {
        GenotypingSchema gs = GenotypingSchema.get();
        TableInfo sequences = gs.getSequencesTable();

        SequenceDictionary dictionary = getCurrentDictionary(c, user, false);

        if (null == dictionary)
            return 0;

        SimpleFilter filter = new SimpleFilter("Dictionary", dictionary.getRowId());

        return new TableSelector(sequences, filter, null).getRowCount();
    }


    public int getDictionaryCount(Container c)
    {
        SimpleFilter filter = new SimpleFilter("Container", c);

        return (int)new TableSelector(GenotypingSchema.get().getDictionariesTable(), filter, null).getRowCount();
    }


    public long getSequenceCount(Container c)
    {
        GenotypingSchema gs = GenotypingSchema.get();
        TableInfo sequences = gs.getSequencesTable();

        SQLFragment sql = new SQLFragment("SELECT s.RowId FROM ");
        sql.append(gs.getSequencesTable(), "s");
        sql.append(" INNER JOIN ");
        sql.append(gs.getDictionariesTable(), "d");
        sql.append(" ON s.Dictionary = d.RowId WHERE Container = ?");
        sql.add(c);

        return new SqlSelector(sequences.getSchema(), sql).getRowCount();
    }
}
