package org.labkey.genotyping;

import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.view.Portal;

import java.util.Arrays;
import java.util.Collections;

/**
 * User: adam
 * Date: Oct 6, 2010
 * Time: 1:52:59 PM
 */
public class GenotypingFolderType extends DefaultFolderType
{
    public GenotypingFolderType(GenotypingModule module)
    {
        super("Genotyping",
                "Manage importing and analyzing next generation sequencing runs.",
            Collections.<Portal.WebPart>emptyList(),
            Arrays.asList(
                Portal.getPortalPart("Lists").createWebPart(),
                Portal.getPortalPart("Query").createWebPart()
            ),
            getDefaultModuleSet(module, getModule("Pipeline")),
            module);
    }
}
