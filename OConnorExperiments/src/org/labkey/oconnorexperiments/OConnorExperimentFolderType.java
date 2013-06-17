package org.labkey.oconnorexperiments;

import org.labkey.api.files.FileContentService;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;

import java.util.Arrays;

/**
 * User: kevink
 * Date: 6/12/13
 */
public class OConnorExperimentFolderType extends DefaultFolderType
{
    public static final String NAME = "OConnorExperiment";

    public OConnorExperimentFolderType()
    {
        super(NAME,
                "An experiment containing files and notes",
                null,
                Arrays.asList(
                        Portal.getPortalPart("Experiment Field").createWebPart(),
                        createFileWebPart(),
                        createWikiWebPart()
                ),
                getDefaultModuleSet(ModuleLoader.getInstance().getCoreModule(), getModule("Experiment"), getModule(OConnorExperimentsModule.NAME)),
                ModuleLoader.getInstance().getCoreModule());
        setWorkbookType(true);
    }

    @Override
    public String getLabel()
    {
        return "Experiment";
    }

    private static Portal.WebPart createFileWebPart()
    {
        Portal.WebPart result = Portal.getPortalPart("Files").createWebPart(HttpView.BODY);
        result.setProperty("fileSet", FileContentService.PIPELINE_LINK);
        result.setProperty("webpart.title", "Files");
        return result;
    }
    private static Portal.WebPart createWikiWebPart()
    {
        Portal.WebPart result = Portal.getPortalPart("Wiki").createWebPart(HttpView.BODY);
        return result;
    }
}
