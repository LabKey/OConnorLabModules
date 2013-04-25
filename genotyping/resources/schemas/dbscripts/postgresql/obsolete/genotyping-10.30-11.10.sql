/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

/* genotyping-10.30-10.31.sql */

-- Add ParentId column -- when matches are combined or altered this is set to the new match's row id
-- We then filter out combined matches (ParentId IS NOT NULL) from normal analysis views.
ALTER TABLE genotyping.Matches
    ADD ParentId INT NULL;

/* genotyping-10.31-10.32.sql */

ALTER TABLE genotyping.Sequences
    ALTER COLUMN Comments TYPE VARCHAR(4000);

-- Link reads directly to sample ids; SampleId replaces the Mid column, though we'll leave Mid in place for one release
ALTER TABLE genotyping.Reads ADD COLUMN SampleId INT NULL;

-- Populate the new SampleId column based on the previously stored MIDs
SELECT core.executeJavaUpgradeCode('populateReadsSampleIds');