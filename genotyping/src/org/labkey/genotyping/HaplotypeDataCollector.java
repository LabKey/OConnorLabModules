/*
 * Copyright (c) 2012-2018 LabKey Corporation
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

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AbstractTempDirDataCollector;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: 10/16/12
 */
public class HaplotypeDataCollector<ContextType extends AssayRunUploadContext<HaplotypeAssayProvider>> extends AbstractTempDirDataCollector<ContextType>
{
    private Map<String, String> _reshowMap;

    @Override
    public HttpView getView(ContextType context)
    {
        // if reshowing on error, get the data param out of the context for the JSP to use
        HttpServletRequest request = context.getRequest();
        _reshowMap = new HashMap<>();
        _reshowMap.put(HaplotypeAssayProvider.DATA_PROPERTY_NAME, request.getParameter(HaplotypeAssayProvider.DATA_PROPERTY_NAME));
        for (String propName : HaplotypeAssayProvider.getColumnMappingProperties(context.getProtocol()).keySet())
        {
            _reshowMap.put(propName, request.getParameter(propName));
        }

        return new JspView<>("/org/labkey/genotyping/view/importHaplotypeAssignments.jsp", new HaplotypeProtocolBean(this, context.getProtocol()));
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
    public Map<String, FileObject> createData(ContextType context) throws IOException, ExperimentException
    {
        ExpProtocol protocol = context.getProtocol();
        String data = context.getRequest().getParameter(HaplotypeAssayProvider.DATA_PROPERTY_NAME);
        if (data == null || data.trim().isEmpty())
        {
            throw new ExperimentException("Data contained zero data rows");
        }

        // verify that all of the column header mapping values are present
        List<String> errorColHeaders = new ArrayList<>();

        for (Map.Entry<String, HaplotypeColumnMappingProperty> property : HaplotypeAssayProvider.getColumnMappingProperties(protocol).entrySet())
        {
            String value = context.getRequest().getParameter(property.getKey());
            if (property.getValue().isRequired() && (value == null || value.equals("")))
                errorColHeaders.add(property.getValue().getLabel());
        }
        if (errorColHeaders.size() > 0)
        {
            throw new ExperimentException("Column header mapping missing for: " + StringUtils.join(errorColHeaders, ", "));
        }

        // NOTE: We use a 'tmp' file extension so that DataLoaderService will sniff the file type by parsing the file's header.
        FileObject dir = getFileTargetDir(context);
        FileObject file = createFile(protocol, dir, "tmp");
        ByteArrayInputStream bIn = new ByteArrayInputStream(data.getBytes(context.getRequest().getCharacterEncoding()));

        writeFile(bIn, file.getPath().toFile());
        return Collections.singletonMap(PRIMARY_FILE, file);
    }

    public String getReshowValue(String key)
    {
        return _reshowMap.get(key);
    }
}
