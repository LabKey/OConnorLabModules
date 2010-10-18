package org.labkey.genotyping.sequences;

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.writer.FastaGenerator;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Oct 16, 2010
 * Time: 12:08:40 AM
 */
public abstract class FastqGenerator implements FastaGenerator<FastqWriter.FastqEntry>
{
    private final ResultSet _rs;

    public FastqGenerator(ResultSet rs)
    {
        _rs = rs;
    }

    abstract public String getHeader(ResultSet rs) throws SQLException;
    abstract public String getSequence(ResultSet rs) throws SQLException;
    abstract public String getQuality(ResultSet rs) throws SQLException;

    @Override
    public boolean hasNext()
    {
        try
        {
            boolean hasNext = _rs.next();

            if (!hasNext)
                _rs.close();

            return hasNext;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    public FastqWriter.FastqEntry next()
    {
        return new FastqWriter.FastqEntry() {
            @Override
            public String getHeader()
            {
                try
                {
                    return FastqGenerator.this.getHeader(_rs);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }

            @Override
            public String getSequence()
            {
                try
                {
                    return FastqGenerator.this.getSequence(_rs);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }

            @Override
            public String getQuality()
            {
                try
                {
                    return FastqGenerator.this.getQuality(_rs);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
        };
    }

    @Override
    public void remove()
    {
        throw new UnsupportedOperationException();
    }
}
