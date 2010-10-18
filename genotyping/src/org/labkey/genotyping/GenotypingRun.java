package org.labkey.genotyping;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;

import java.io.File;
import java.sql.SQLException;
import java.util.Date;

/**
 * User: adam
 * Date: Oct 16, 2010
 * Time: 5:08:23 PM
 */
public class GenotypingRun
{
    private int _rowId;
    private Container _container;
    private int _createdBy;
    private Date _created;
    private String _path;
    private String _fileName;
    private Integer _metaDataId = null;
    private int _status = Status.NotSubmitted.getStatusId();

    public GenotypingRun()
    {
        assert MemTracker.put(this);
    }

    public GenotypingRun(Container c, File readsFile, @Nullable MetaDataRun metaDataRun) throws SQLException
    {
        this();
        setContainer(c);
        setPath(readsFile.getParent());
        setFileName(readsFile.getName());

        if (null != metaDataRun)
        {
            setMetaDataId(metaDataRun.getRun());
            setRowId(metaDataRun.getRun());
        }
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

    public void setMetaDataId(@Nullable Integer metaDataId)
    {
        _metaDataId = metaDataId;
    }

    public @Nullable Integer getMetaDataId()
    {
        return _metaDataId;
    }

    public @Nullable MetaDataRun getMetaDataRun(User user)
    {
        if (null != _metaDataId)
            return GenotypingManager.get().getMetaDataRun(_container, user, _metaDataId);
        else
            return null;
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

    public String getFileName()
    {
        return _fileName;
    }

    public void setFileName(String fileName)
    {
        _fileName = fileName;
    }

    public int getStatus()
    {
        return _status;
    }

    public void setStatus(int status)
    {
        _status = status;
    }

    public Status getStatusEnum()
    {
        return Status.getStatus(_status);
    }

    public void setStatusEnum(Status statusEnum)
    {
        _status = statusEnum.getStatusId();
    }
}
