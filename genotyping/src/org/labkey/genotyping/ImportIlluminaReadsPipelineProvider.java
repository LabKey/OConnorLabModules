/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.labkey.api.module.Module;

/**
 * User: bbimber
 * Date: Apr 19, 2012
 * Time: 8:42:36 PM
 */
public class ImportIlluminaReadsPipelineProvider extends ReadsPipelineProvider
{
    public static final String NAME = "Import Illumina Reads";

    public ImportIlluminaReadsPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule, GenotypingManager.SEQUENCE_PLATFORMS.ILLUMINA.name());
    }
}
