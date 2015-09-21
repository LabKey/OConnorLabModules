package org.labkey.genotyping;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.FileFilter;

public class ReadsPipelineProvider extends PipelineProvider
{
    String _platform;

    public ReadsPipelineProvider(String name, Module owningModule, String platform)
    {
        super(name, owningModule);
        _platform = platform;
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;

        ActionURL importURL = directory.cloneHref();
        importURL.setAction(GenotypingController.ImportReadsAction.class);
        importURL.addParameter("pipeline", true);    // Distinguish between manual pipeline submission and automated scripts
        importURL.addParameter("platform", _platform);

        String actionId = createActionId(GenotypingController.ImportReadsAction.class, _platform);
        addAction(actionId, importURL, getName(), directory, directory.listFiles(new SampleCSVFilter()), false, false, includeAll);
    }

    private static class SampleCSVFilter implements FileFilter
    {
        public boolean accept(File file)
        {
            return "csv".equalsIgnoreCase(FileUtil.getExtension(file));
        }
    }
}
