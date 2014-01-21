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
import org.labkey.api.util.MemTracker;
import org.labkey.genotyping.sequences.SequenceManager;

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
    private String _fileName;
    private @Nullable String _description;
    private int _sequenceDictionary;
    private @Nullable String _sequencesView;
    private int _status = Status.NotSubmitted.getStatusId();

    public GenotypingAnalysis()
    {
        MemTracker.getInstance().put(this);
    }

    public GenotypingAnalysis(Container c, User user, GenotypingRun run, @Nullable String description, @Nullable String sequencesView)
    {
        this();
        setContainer(c);
        setRun(run.getRowId());
        setDescription(description);
        setSequenceDictionary(SequenceManager.get().getCurrentDictionary(c, user).getRowId());
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

    public String getFileName()
    {
        return _fileName;
    }

    public void setFileName(String fileName)
    {
        _fileName = fileName;
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

    public int getStatus()
    {
        return _status;
    }

    public void setStatus(int status)
    {
        _status = status;
    }
}
