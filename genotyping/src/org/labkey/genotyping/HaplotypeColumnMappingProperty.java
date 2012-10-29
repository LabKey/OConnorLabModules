package org.labkey.genotyping;

/**
 * User: cnathe
 * Date: 10/29/12
 */
public class HaplotypeColumnMappingProperty
{
    String _name;
    String _label;
    boolean _required;

    public HaplotypeColumnMappingProperty(String name, String label, boolean required)
    {
        _name = name;
        _label = label;
        _required = required;
    }

    public String getName()
    {
        return _name;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean isRequired()
    {
        return _required;
    }
}
