package org.labkey.genotyping.sequences;

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


    public void writeFasta(Container c, User user, String sequencesViewName, File destination) throws SQLException, IOException
    {
        QueryHelper qHelper = new QueryHelper(c, user, "sequences", "dnasequences", sequencesViewName);
        SimpleFilter viewFilter = qHelper.getViewFilter();
        viewFilter.addCondition("Dictionary", getCurrentDictionary(c));
        TableInfo ti = GenotypingSchema.get().getSequencesTable();
        ResultSet rs = Table.select(ti, ti.getColumns("RowId,AlleleName,Sequence"), viewFilter, new Sort("RowId"));

        try
        {
            FastaWriter fw = new FastaWriter(new ResultSetFastaGenerator(rs) {
                @Override
                public String getHeader(ResultSet rs) throws SQLException
                {
                    return rs.getString("RowId") + "|" + rs.getString("AlleleName");
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


    private int getCurrentDictionary(Container c) throws SQLException
    {
        return Table.executeSingleton(GenotypingSchema.get().getSchema(),
                "SELECT CAST(COUNT(*) AS INT) FROM " + GenotypingSchema.get().getDictionariesTable() + " WHERE Container = ?",
                new Object[]{c}, Integer.class);
    }
}
