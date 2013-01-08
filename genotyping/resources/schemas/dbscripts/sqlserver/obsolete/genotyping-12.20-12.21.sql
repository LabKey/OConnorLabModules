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

CREATE TABLE genotyping.Animal
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Container ENTITYID NOT NULL,
    LabAnimalId NVARCHAR(100),
    CustomerAnimalId NVARCHAR(100),
    Lsid LsidType NOT NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified DATETIME NOT NULL,

    CONSTRAINT PK_Animal PRIMARY KEY (RowId),
    CONSTRAINT UQ_Animal_LabAnimalId UNIQUE (Container, LabAnimalId),
    CONSTRAINT FK_Animal_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_Animal_Lsid FOREIGN KEY (Lsid) REFERENCES exp.Object(ObjectURI)
);

CREATE INDEX IDX_Animal_Container ON genotyping.Animal(Container);

CREATE TABLE genotyping.Haplotype
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(50),
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified DATETIME NOT NULL,

    CONSTRAINT PK_Haplotype PRIMARY KEY (RowId),
    CONSTRAINT FK_Haplotype_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE INDEX IDX_Haplotype_Container ON genotyping.Haplotype(Container);

CREATE TABLE genotyping.AnimalHaplotypeAssignment
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    AnimalId INT NOT NULL,
    HaplotypeId INT NOT NULL,
    RunId INT NOT NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified DATETIME NOT NULL,

    CONSTRAINT PK_AnimalHaplotypeAssignment PRIMARY KEY (RowId),
    CONSTRAINT UQ_AnimalHaplotypeAssignment UNIQUE (AnimalId, HaplotypeId, RunId),
    CONSTRAINT FK_AnimalHaplotypeAssignment_Animal FOREIGN KEY (AnimalId) REFERENCES genotyping.Animal(RowId),
    CONSTRAINT FK_AnimalHaplotypeAssignment_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun(RowId),
    CONSTRAINT FK_AnimalHaplotypeAssignment_Haplotype FOREIGN KEY (HaplotypeId) REFERENCES genotyping.Haplotype(RowId)
);

CREATE INDEX IDX_AnimalHaplotypeAssignment_Run ON genotyping.AnimalHaplotypeAssignment(RunId);
CREATE INDEX IDX_AnimalHaplotypeAssignment_Haplotype ON genotyping.AnimalHaplotypeAssignment(HaplotypeId);
