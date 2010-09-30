package org.labkey.genotyping.sequences;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ResultSetIterator;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
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
        int dictionaryId = getCurrentDictionary(c);

        GenotypingFolderSettings settings = GenotypingManager.get().getSettings(c);
        QueryHelper qHelper = new QueryHelper(c, user, settings.getSequencesQuery());

        SimpleFilter viewFilter = qHelper.getViewFilter();
        viewFilter.addCondition("file_active", 1);
        TableInfo source = qHelper.getTableInfo();

        ResultSet rs = null;

        try
        {
            rs = Table.select(source, Table.ALL_COLUMNS, viewFilter, null);
            ResultSetIterator iter = ResultSetIterator.get(rs);

            TableInfo destination = GenotypingSchema.get().getSequencesTable();

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

                inMap.put("dictionary", dictionaryId);
                Table.insert(user, destination, inMap);
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    public void writeFasta(Container c, User user, @Nullable String sequencesViewName, File destination) throws SQLException, IOException
    {
        ResultSet rs = null;

        try
        {
            rs = selectSequences(c, user, getCurrentDictionary(c), sequencesViewName, "AlleleName,Sequence");

            FastaWriter fw = new FastaWriter(new ResultSetFastaGenerator(rs) {
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


    public int getCurrentDictionary(Container c) throws SQLException
    {
        Integer max = Table.executeSingleton(GenotypingSchema.get().getSchema(),
                "SELECT MAX(RowId) FROM " + GenotypingSchema.get().getDictionariesTable() + " WHERE Container = ?",
                new Object[]{c}, Integer.class);

        if (null == max)
            throw new IllegalStateException("You must load reference sequences before initiating an analysis");

        return max;
    }


    public Map<String, Integer> getSequences(Container c, User user, int dictionary, String sequencesViewName) throws SQLException
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

    private ResultSet selectSequences(Container c, User user, int dictionary, String sequencesViewName, String columnNames) throws SQLException
    {
        // First, make sure that dictionary exists in this container
        SimpleFilter filter = new SimpleFilter("RowId", dictionary);
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
        QueryHelper qHelper = new QueryHelper(c, user, "sequences", "sequences", sequencesViewName);
        SimpleFilter viewFilter = qHelper.getViewFilter();
        viewFilter.addCondition("Dictionary", dictionary);
        TableInfo ti = GenotypingSchema.get().getSequencesTable();
        return Table.select(ti, ti.getColumns(columnNames), viewFilter, new Sort("RowId"));
    }
}
