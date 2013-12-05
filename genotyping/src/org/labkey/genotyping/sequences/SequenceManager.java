/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryHelper;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.FastaEntry;
import org.labkey.api.writer.FastaWriter;
import org.labkey.api.writer.ResultSetFastaGenerator;
import org.labkey.genotyping.GenotypingQueryHelper;
import org.labkey.genotyping.GenotypingSchema;
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
    public static final String[] FASTQ_EXTENSIONS = {"fastq", "fq"};

    private SequenceManager()
    {
        // prevent external construction with a private default constructor
    }


    public static SequenceManager get()
    {
        return _instance;
    }


    public void loadSequences(Container c, final User user) throws SQLException
    {
        Map<String, Object> dictionary = new HashMap<>();
        dictionary.put("container", c);
        Table.insert(user, GenotypingSchema.get().getDictionariesTable(), dictionary);
        final int dictionaryId = getCurrentDictionary(c, user).getRowId();

        ValidatingGenotypingFolderSettings settings = new ValidatingGenotypingFolderSettings(c, user, "loading sequences");
        QueryHelper qHelper = new GenotypingQueryHelper(c, user, settings.getSequencesQuery());

        SimpleFilter viewFilter = qHelper.getViewFilter();
        final TableInfo destination = GenotypingSchema.get().getSequencesTable();
        // If "file_active" column exists then filter on it, #14008
        if (null != destination.getColumn("file_active"))
            viewFilter.addCondition(FieldKey.fromParts("file_active"), 1);
        TableInfo source = qHelper.getTableInfo();

        new TableSelector(source, viewFilter, null).forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
        {
            @Override
            public void exec(Map<String, Object> map) throws SQLException
            {
                Map<String, Object> inMap = new HashMap<>(map.size() * 2);

                // General way to map column names would be nice...
                for (Map.Entry<String, Object> entry : map.entrySet())
                {
                    String key = entry.getKey().replaceAll("_", "");
                    inMap.put(key, entry.getValue());
                }

                // Skip empty sequences.  TODO: remove this check once wisconsin eliminates empty sequences
                if (StringUtils.isBlank((String)inMap.get("sequence")))
                    return;

                inMap.put("dictionary", dictionaryId);
                Table.insert(user, destination, inMap);
            }
        });

        destination.getSqlDialect().updateStatistics(destination);
    }


    public void writeFasta(Container c, User user, @Nullable String sequencesViewName, File destination) throws SQLException, IOException
    {
        ResultSet rs = null;

        try
        {
            rs = selectSequences(c, user, getCurrentDictionary(c, user), sequencesViewName, "AlleleName,Sequence");

            FastaWriter<FastaEntry> fw = new FastaWriter<>(new ResultSetFastaGenerator(rs) {
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
        HashMap<String, Integer> sequences = new HashMap<>();

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
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("RowId"), dictionary.getRowId());
        TableInfo dictionaries = GenotypingSchema.get().getDictionariesTable();
        Integer dictionaryId = new TableSelector(dictionaries, dictionaries.getColumns("RowId"), filter, null).getObject(Integer.class);

        if (null == dictionaryId)
            throw new IllegalStateException("Sequences dictionary does not exist in this folder");

        // Now select all sequences in this dictionary, applying the specified filter
        GenotypingSchema gs = GenotypingSchema.get();
        QueryHelper qHelper = new QueryHelper(c, user, gs.getSchemaName(), gs.getSequencesTable().getName(), sequencesViewName);
        SimpleFilter viewFilter = qHelper.getViewFilter();
        viewFilter.addCondition(FieldKey.fromParts("Dictionary"), dictionary.getRowId());
        TableInfo ti = GenotypingSchema.get().getSequencesTable();

        return new TableSelector(ti, ti.getColumns(columnNames), viewFilter, new Sort("RowId")).getResultSet();
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

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Dictionary"), dictionary.getRowId());

        return new TableSelector(sequences, filter, null).getRowCount();
    }


    public int getDictionaryCount(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);

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
