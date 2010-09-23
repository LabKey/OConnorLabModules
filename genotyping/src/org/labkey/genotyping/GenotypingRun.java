package org.labkey.genotyping;

/**
 * User: adam
 * Date: Sep 22, 2010
 * Time: 7:42:10 AM
 */
public class GenotypingRun
{
    private int _run;
    private int _sampleLibrary;

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
