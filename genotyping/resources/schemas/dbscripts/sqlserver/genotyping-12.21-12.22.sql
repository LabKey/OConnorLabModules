/*
 * Copyright (c) 2012 LabKey Corporation
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

CREATE TABLE genotyping.AnimalAnalysis
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    AnimalId INT NOT NULL,
    RunId INT NOT NULL,
    TotalReads INT,
    IdentifiedReads INT,
    Enabled BIT NOT NULL DEFAULT 1,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified DATETIME NOT NULL,

    CONSTRAINT PK_AnimalAnalysis PRIMARY KEY (RowId),
    CONSTRAINT UQ_AnimalAnalysis UNIQUE (AnimalId, RunId),
    CONSTRAINT FK_AnimalAnalysis_Animal FOREIGN KEY (AnimalId) REFERENCES genotyping.Animal(RowId),
    CONSTRAINT FK_AnimalAnalysis_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun(RowId)
);

CREATE INDEX IDX_AnimalAnalysis_Run ON genotyping.AnimalAnalysis(RunId);
CREATE INDEX IDX_AnimalAnalysis_Animal ON genotyping.AnimalAnalysis(AnimalId);

-- change AnimalHaplotypeAssignment to use AnimalAnalysisId instead of AnimalId and RunId
DELETE FROM genotyping.AnimalHaplotypeAssignment;
DROP INDEX IDX_AnimalHaplotypeAssignment_Run ON genotyping.AnimalHaplotypeAssignment;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP CONSTRAINT UQ_AnimalHaplotypeAssignment;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP CONSTRAINT FK_AnimalHaplotypeAssignment_Animal;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP COLUMN AnimalId;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP CONSTRAINT FK_AnimalHaplotypeAssignment_Run;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP COLUMN RunId;
ALTER TABLE genotyping.AnimalHaplotypeAssignment ADD AnimalAnalysisId INT NOT NULL;
ALTER TABLE genotyping.AnimalHaplotypeAssignment ADD CONSTRAINT FK_AnimalHaplotypeAssignment_AnimalAnalysis FOREIGN KEY (AnimalAnalysisId) REFERENCES genotyping.AnimalAnalysis(RowId);

-- drop not null contraint on Animal table for case where table not configured to be extensible
ALTER TABLE genotyping.Animal ALTER COLUMN Lsid LsidType NULL;

-- add Lsid column to Haplotype to allow it to be extensible
ALTER TABLE genotyping.Haplotype ADD Lsid LsidType NULL;
ALTER TABLE genotyping.Haplotype ADD CONSTRAINT FK_Haplotype_Lsid FOREIGN KEY (Lsid) REFERENCES exp.Object(ObjectURI);

-- add Type column to Haplotype to store 'Mamu-A' or 'Mamu-B'
ALTER TABLE genotyping.Haplotype ADD Type NVARCHAR(20);