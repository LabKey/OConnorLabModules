package org.labkey.genotyping;

import org.jetbrains.annotations.Nullable;
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
    private @Nullable String _description;
    private int _sequenceDictionary;
    private @Nullable String _sequencesView;

    public GenotypingAnalysis()
    {
        assert MemTracker.put(this);
    }

    public GenotypingAnalysis(Container c, GenotypingRun run, @Nullable String description, @Nullable String sequencesView) throws SQLException
    {
        this();
        setContainer(c);
        setRun(run.getRun());
        setDescription(description);
        setSequenceDictionary(SequenceManager.get().getCurrentDictionary(c).getRowId());
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

    @Nullable
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(@Nullable String description)
    {
        _description = description;
    }

    public int getSequenceDictionary()
    {
        return _sequenceDictionary;
    }

    public void setSequenceDictionary(int sequenceDictionary)
    {
        _sequenceDictionary = sequenceDictionary;
    }

    public @Nullable String getSequencesView()
    {
        return _sequencesView;
    }

    public void setSequencesView(@Nullable String sequencesView)
    {
        _sequencesView = sequencesView;
    }
}
