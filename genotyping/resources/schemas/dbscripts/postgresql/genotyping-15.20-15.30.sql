/*
 * Copyright (c) 2015 LabKey Corporation
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

/* genotyping-15.20-15.21.sql */

ALTER TABLE Genotyping.SequenceFiles
    ADD column PoolNum INT NULL;

/* genotyping-15.21-15.22.sql */

ALTER TABLE genotyping.runs
    DROP CONSTRAINT UNIQUE_Runs;

ALTER TABLE genotyping.runs
    ADD CONSTRAINT UNIQUE_Runs UNIQUE (rowid, container, metadataid);