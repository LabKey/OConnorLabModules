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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.util.Collections;
import java.util.List;

/**
 * User: adam
 * Date: Oct 7, 2010
 * Time: 10:47:28 AM
 */
public class GenotypingWebPart extends JspView
{
    public static final WebPartFactory FACTORY = new BaseWebPartFactory("Genotyping Overview")
    {
        public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
        {
            return new GenotypingWebPart();
        }

        @Override
        public List<String> getLegacyNames()
        {
            return Collections.singletonList("Genotyping");
        }
    };

    public GenotypingWebPart()
    {
        super("/org/labkey/genotyping/view/overview.jsp");
        setTitle("Genotyping Overview");
    }
}
