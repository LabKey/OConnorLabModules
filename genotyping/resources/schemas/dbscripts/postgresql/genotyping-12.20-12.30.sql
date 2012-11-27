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

/* genotyping-12.20-12.21.sql */

CREATE TABLE genotyping.Animal
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    LabAnimalId VARCHAR(100),
    CustomerAnimalId VARCHAR(100),
    Lsid LsidType NOT NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,

    CONSTRAINT PK_Animal PRIMARY KEY (RowId),
    CONSTRAINT UQ_Animal_LabAnimalId UNIQUE (Container, LabAnimalId),
    CONSTRAINT FK_Animal_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_Animal_Lsid FOREIGN KEY (Lsid) REFERENCES exp.Object(ObjectURI)
);

CREATE INDEX IDX_Animal_Container ON genotyping.Animal(Container);

CREATE TABLE genotyping.Haplotype
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(50),
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,

    CONSTRAINT PK_Haplotype PRIMARY KEY (RowId),
    CONSTRAINT FK_Haplotype_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE INDEX IDX_Haplotype_Container ON genotyping.Haplotype(Container);

CREATE TABLE genotyping.AnimalHaplotypeAssignment
(
    RowId SERIAL,
    AnimalId INT NOT NULL,
    HaplotypeId INT NOT NULL,
    RunId INT NOT NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,

    CONSTRAINT PK_AnimalHaplotypeAssignment PRIMARY KEY (RowId),
    CONSTRAINT UQ_AnimalHaplotypeAssignment UNIQUE (AnimalId, HaplotypeId, RunId),
    CONSTRAINT FK_AnimalHaplotypeAssignment_Animal FOREIGN KEY (AnimalId) REFERENCES genotyping.Animal(RowId),
    CONSTRAINT FK_AnimalHaplotypeAssignment_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun(RowId),
    CONSTRAINT FK_AnimalHaplotypeAssignment_Haplotype FOREIGN KEY (HaplotypeId) REFERENCES genotyping.Haplotype(RowId)
);

CREATE INDEX IDX_AnimalHaplotypeAssignment_Run ON genotyping.AnimalHaplotypeAssignment(RunId);
CREATE INDEX IDX_AnimalHaplotypeAssignment_Haplotype ON genotyping.AnimalHaplotypeAssignment(HaplotypeId);

/* genotyping-12.21-12.22.sql */

CREATE TABLE genotyping.AnimalAnalysis
(
    RowId SERIAL,
    AnimalId INT NOT NULL,
    RunId INT NOT NULL,
    TotalReads INT,
    IdentifiedReads INT,
    Enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,

    CONSTRAINT PK_AnimalAnalysis PRIMARY KEY (RowId),
    CONSTRAINT UQ_AnimalAnalysis UNIQUE (AnimalId, RunId),
    CONSTRAINT FK_AnimalAnalysis_Animal FOREIGN KEY (AnimalId) REFERENCES genotyping.Animal(RowId),
    CONSTRAINT FK_AnimalAnalysis_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun(RowId)
);

CREATE INDEX IDX_AnimalAnalysis_Run ON genotyping.AnimalAnalysis(RunId);
CREATE INDEX IDX_AnimalAnalysis_Animal ON genotyping.AnimalAnalysis(AnimalId);

-- change AnimalHaplotypeAssignment to use AnimalAnalysisId instead of AnimalId and RunId
DELETE FROM genotyping.AnimalHaplotypeAssignment;
DROP INDEX genotyping.IDX_AnimalHaplotypeAssignment_Run;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP CONSTRAINT UQ_AnimalHaplotypeAssignment;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP COLUMN AnimalId;
ALTER TABLE genotyping.AnimalHaplotypeAssignment DROP COLUMN RunId;
ALTER TABLE genotyping.AnimalHaplotypeAssignment ADD COLUMN AnimalAnalysisId INT NOT NULL;
ALTER TABLE genotyping.AnimalHaplotypeAssignment ADD CONSTRAINT FK_AnimalHaplotypeAssignment_AnimalAnalysis FOREIGN KEY (AnimalAnalysisId) REFERENCES genotyping.AnimalAnalysis(RowId);

-- drop not null contraint on Animal table for case where table not configured to be extensible
ALTER TABLE genotyping.Animal ALTER COLUMN Lsid DROP NOT NULL;

-- add Lsid column to Haplotype to allow it to be extensible
ALTER TABLE genotyping.Haplotype ADD COLUMN Lsid LsidType;
ALTER TABLE genotyping.Haplotype ADD CONSTRAINT FK_Haplotype_Lsid FOREIGN KEY (Lsid) REFERENCES exp.Object(ObjectURI);

-- add Type column to Haplotype to store 'Mamu-A' or 'Mamu-B'
ALTER TABLE genotyping.Haplotype ADD COLUMN Type VARCHAR(20);

/* genotyping-12.22-12.23.sql */

-- rename column
ALTER TABLE genotyping.Animal RENAME COLUMN CustomerAnimalId TO ClientAnimalId;