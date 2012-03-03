package org.labkey.genotyping;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;

import java.util.Map;

/**
* User: adam
* Date: 3/2/12
* Time: 9:59 AM
*/
class NonValidatingGenotypingFolderSettings implements GenotypingFolderSettings
{
    private final Map<String, String> _map;
    protected final Container _c;

    NonValidatingGenotypingFolderSettings(Container c)
    {
        _map = PropertyManager.getProperties(c.getId(), GenotypingManager.FOLDER_CATEGORY);
        _c = c;
    }

    @Override
    public @Nullable String getSequencesQuery()
    {
        return getQuery(GenotypingManager.Setting.ReferenceSequencesQuery);
    }

    @Override
    public @Nullable String getRunsQuery()
    {
        return getQuery(GenotypingManager.Setting.RunsQuery);
    }

    @Override
    public @Nullable String getSamplesQuery()
    {
        return getQuery(GenotypingManager.Setting.SamplesQuery);
    }

    protected @Nullable String getQuery(GenotypingManager.Setting setting)
    {
        return _map.get(setting.getKey());
    }
}
