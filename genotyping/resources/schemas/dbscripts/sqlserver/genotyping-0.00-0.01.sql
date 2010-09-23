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

EXEC core.fn_dropifexists '*', 'galaxy', 'SCHEMA', NULL
GO

EXEC sp_addapprole 'galaxy', 'password'
GO

CREATE TABLE galaxy.Matches
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    SampleId VARCHAR(200) NOT NULL,
    Reads INT NOT NULL,
    "Percent" FLOAT NOT NULL,
    Strandedness VARCHAR(5),
    AverageLength FLOAT NOT NULL,
    PosReads INT NOT NULL,
    NegReads INT NOT NULL,
    PosExtReads INT NOT NULL,
    NegExtReads INT NOT NULL,

    CONSTRAINT PK_Matches PRIMARY KEY (RowId)
)

CREATE TABLE galaxy.Alleles
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    Allele VARCHAR(100) NOT NULL,

    CONSTRAINT PK_Alleles PRIMARY KEY (RowId)
)

CREATE TABLE galaxy.Junction
(
    RowId INT NOT NULL,
    AlleleId INT NOT NULL
)

-- TODO: Add FKs for RowId -> Matches, AllelelId > Alleles

CREATE INDEX IX_Junction ON galaxy.Junction(RowId)

CREATE TABLE galaxy.DnaSequences
(
    Container ENTITYID NOT NULL,
    Uid INT NOT NULL,
    AlleleName VARCHAR(45) NOT NULL,
    Initials VARCHAR(45) NULL,
    GenbankId VARCHAR(45) NULL,
    ExptNumber VARCHAR(45) NULL,
    Comments VARCHAR(255) NULL,
    Locus VARCHAR(45) NULL,
    Species VARCHAR(45) NULL,
    Origin VARCHAR(45) NULL,
    Sequence TEXT,
    PreviousName VARCHAR(45) NULL,
    LastEdit TIMESTAMP NOT NULL,
    Version INT NOT NULL,
    ModifiedBy VARCHAR(45) NOT NULL,
    Translation TEXT,
    Type VARCHAR(45) NULL,
    IpdAccession VARCHAR(45) NULL,
    Reference INT NOT NULL,
    RegIon VARCHAR(45) NULL,
    Id INT NOT NULL,
    Variant INT NOT NULL,
    UploadId VARCHAR(45),
    FullLength INT NOT NULL,
    AlleleFamily VARCHAR(45),

    CONSTRAINT PK_DnaSequences PRIMARY KEY (Uid)
)

CREATE TABLE galaxy.UnmatchedSequences
(
    Mid INT NOT NULL,
    Sequence TEXT NOT NULL
)
GO

-- TODO: Add run/analysis run, index
