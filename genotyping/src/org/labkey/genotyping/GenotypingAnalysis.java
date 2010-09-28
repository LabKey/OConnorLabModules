package org.labkey.genotyping;

import org.labkey.api.data.Container;
import org.labkey.api.util.MemTracker;
import org.labkey.genotyping.sequences.SequenceManager;

import java.sql.SQLException;
import java.util.Date;

/**
 * User: adam
 * Date: Sep 27, 2010
 * Time: 2:08:55 PM
 */
public class GenotypingAnalysis
{
    private int _rowId;
    private Container _container;
    private int _run;
    private int _createdBy;
    private Date _created;
    private String _path;
    private int _sequenceDictionary;
    private String _sequencesView;

    public GenotypingAnalysis()
    {
        assert MemTracker.put(this);
    }

    public GenotypingAnalysis(Container c, GenotypingRun run, String sequencesView) throws SQLException
    {
        this();
        setContainer(c);
        setRun(run.getRun());
        setSequenceDictionary(SequenceManager.get().getCurrentDictionary(c));
        setSequencesView(sequencesView);
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public int getRun()
    {
        return _run;
    }

    public void setRun(int run)
    {
        _run = run;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public String getPath()
    {
        return _path;
    }

    public void setPath(String path)
    {
        _path = path;
    }

    public int getSequenceDictionary()
    {
        return _sequenceDictionary;
    }

    public void setSequenceDictionary(int sequenceDictionary)
    {
        _sequenceDictionary = sequenceDictionary;
    }

    public String getSequencesView()
    {
        return _sequencesView;
    }

    public void setSequencesView(String sequencesView)
    {
        _sequencesView = sequencesView;
    }
}
