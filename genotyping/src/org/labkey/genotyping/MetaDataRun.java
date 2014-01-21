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

import org.labkey.api.data.Container;
import org.labkey.api.util.MemTracker;

/**
 * User: adam
 * Date: Sep 22, 2010
 * Time: 7:42:10 AM
 */
public class MetaDataRun
{
    private int _run;
    private int _sampleLibrary;
    private Container _container;

    public MetaDataRun()
    {
        MemTracker.getInstance().put(this);
    }

    public int getRun()
    {
        return _run;
    }

    public void setRun(int run)
    {
        _run = run;
    }

    public int getSampleLibrary()
    {
        return _sampleLibrary;
    }

    public void setSampleLibrary(int sampleLibrary)
    {
        _sampleLibrary = sampleLibrary;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

// Methods below are used to translate to/from column names in lists

    public int getRun_sample_library()
    {
        return _sampleLibrary;
    }

    public void setRun_sample_library(int run)
    {
        _sampleLibrary = run;
    }

    public int getRun_num()
    {
        return _run;
    }

    public void setRun_num(int run)
    {
        _run = run;
    }
}
