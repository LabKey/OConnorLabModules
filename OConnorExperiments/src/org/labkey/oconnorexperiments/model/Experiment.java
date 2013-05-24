package org.labkey.oconnorexperiments.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.util.GUID;

/**
 * User: kevink
 * Date: 5/19/13
 */
public class Experiment extends Entity
{
    private String _experimentType;
    private GUID[] _parentExperiments;

    public String getExperimentType()
    {
        return _experimentType;
    }

    public void setExperimentType(String experimentType)
    {
        _experimentType = experimentType;
    }

    public GUID[] getParentExperiments()
    {
        return _parentExperiments;
    }

    public void setParentExperiments(Container[] parentExperiments)
    {
//        GUID[] guids = new GUID[parentExperiments.length];
//        for (Container c : parentExperiments)
//        {
//            if (c.isWorkbook())
//        }
//        _parentExperiments = parentExperiments;
    }

    public void setParentExperiments(GUID[] parentExperiments)
    {
        _parentExperiments = parentExperiments;
    }
}
