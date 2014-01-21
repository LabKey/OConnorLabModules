/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTracker;

import java.io.File;
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
    private String _platform;
    private String _fileName;
    private Integer _metaDataId = null;
    private int _status = Status.NotSubmitted.getStatusId();

    public GenotypingRun()
    {
        MemTracker.getInstance().put(this);
    }

    public GenotypingRun(Container c, File readsFile, @Nullable MetaDataRun metaDataRun, String platform)
    {
        this();
        setContainer(c);
        setPath(FileUtil.getAbsoluteCaseSensitiveFile(readsFile.getParentFile()).getPath());
        setFileName(readsFile.getName());
        setPlatform(platform);

        if (null != metaDataRun)
        {
            setMetaDataId(metaDataRun.getRun());
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

    public @Nullable MetaDataRun getMetaDataRun(User user, String action)
    {
        if (null != _metaDataId)
            return GenotypingManager.get().getMetaDataRun(_container, user, _metaDataId, action);
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

    public String getPlatform()
    {
        return _platform;
    }

    public void setPlatform(String platform)
    {
        _platform = platform;
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
