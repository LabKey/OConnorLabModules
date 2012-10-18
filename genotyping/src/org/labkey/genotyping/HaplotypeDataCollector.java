package org.labkey.genotyping;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AbstractTempDirDataCollector;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * User: cnathe
 * Date: 10/16/12
 */
public class HaplotypeDataCollector<ContextType extends AssayRunUploadContext<HaplotypeAssayProvider>> extends AbstractTempDirDataCollector<ContextType>
{
    @Override
    public HttpView getView(ContextType context) throws ExperimentException
    {
        return new JspView<HaplotypeDataCollector>("/org/labkey/genotyping/view/importHaplotypeAssignments.jsp", this);
    }

    @Override
    public String getShortName()
    {
        return "haplotypeAssignmentDataProvider";
    }

    @Override
    public String getDescription(ContextType context)
    {
        return "";
    }

    @NotNull
    @Override
    public Map<String, File> createData(ContextType context) throws IOException, ExperimentException
    {
        ExpProtocol protocol = context.getProtocol();
        String data = context.getRequest().getParameter(HaplotypeAssayProvider.DATA_PROPERTY_NAME);
        if (data == null)
        {
            return null;
        }
        if (data.equals(""))
        {
            throw new ExperimentException("Data contained zero data rows");
        }

        // TODO: reshow with data on error

        // verify that all of the column header mapping values are present
        for (Pair<String, String> property : HaplotypeAssayProvider.COLUMN_HEADER_MAPPING_PROPERTIES)
        {
            String value = context.getRequest().getParameter(property.first);
            if (value == null || value.equals(""))
                throw new ExperimentException("Column header mapping missing for " + property.second);
        }

        // NOTE: We use a 'tmp' file extension so that DataLoaderService will sniff the file type by parsing the file's header.
        File dir = getFileTargetDir(context);
        File file = createFile(protocol, dir, "tmp");
        ByteArrayInputStream bIn = new ByteArrayInputStream(data.getBytes(context.getRequest().getCharacterEncoding()));

        writeFile(bIn, file);
        return Collections.singletonMap(PRIMARY_FILE, file);
    }
}
