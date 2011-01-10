/*
 * Copyright (c) 2010 LabKey Corporation
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

ALTER TABLE genotyping.Sequences
    ALTER COLUMN Comments TYPE VARCHAR(4000);

-- Link reads directly to sample ids; SampleId replaces the Mid column, though we'll leave Mid in place for one release
ALTER TABLE genotyping.Reads ADD COLUMN SampleId INT NULL;

-- Populate the new SampleId column based on the previously stored MIDs
SELECT core.executeJavaUpgradeCode('populateReadsSampleIds');