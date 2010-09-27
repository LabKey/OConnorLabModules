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

SELECT core.fn_dropifexists('*', 'galaxy', 'SCHEMA', NULL);
SELECT core.fn_dropifexists('*', 'genotyping', 'SCHEMA', NULL);

CREATE SCHEMA genotyping;

CREATE TABLE genotyping.Analyses
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    SampleIds VARCHAR(2000) NULL,  -- CSV of sample ids; NULL => all samples in library
    Path VARCHAR(1000) NULL,

    CONSTRAINT PK_Analyses PRIMARY KEY (RowId)
);

CREATE TABLE genotyping.Matches
(
    RowId SERIAL,
    Analysis INT NOT NULL,
    SampleId VARCHAR(200) NOT NULL,
    Reads INT NOT NULL,
    Percent REAL NOT NULL,
    Strandedness VARCHAR(5),
    AverageLength REAL NOT NULL,
    PosReads INT NOT NULL,
    NegReads INT NOT NULL,
    PosExtReads INT NOT NULL,
    NegExtReads INT NOT NULL,

    CONSTRAINT PK_Matches PRIMARY KEY (RowId)
);

-- Junction table that links each row of Matches to one or more allele sequences in Sequences table
CREATE TABLE genotyping.AllelesJunction
(
    MatchId INT NOT NULL,
    SequenceId INT NOT NULL
);

CREATE INDEX IX_AllelesJunction ON genotyping.AllelesJunction(MatchId);

-- Junction table that links each row of Matches to one or more rows in Reads table
CREATE TABLE genotyping.ReadsJunction
(
    MatchId INT NOT NULL,
    ReadId INT NOT NULL
);

-- TODO: Replace these with FKs?
CREATE INDEX IX_ReadsJunctionMatchId ON genotyping.ReadsJunction(MatchId);
CREATE INDEX IX_ReadsJunctionReadId ON genotyping.ReadsJunction(ReadId);

CREATE TABLE genotyping.Dictionaries
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,

    CONSTRAINT PK_Dictionaries PRIMARY KEY (RowId)
);

CREATE TABLE genotyping.Sequences
(
    RowId SERIAL,
    Dictionary INT NOT NULL,
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

    CONSTRAINT PK_Sequences PRIMARY KEY (RowId)
);

-- TODO: Other Sequences indexes (e.g., Dictionary, Uid, AlleleName)

CREATE TABLE genotyping.Reads
(
    RowId SERIAL,          -- TODO: Make this a long?!?!?
    Run INT NOT NULL,
    Name VARCHAR(20) NOT NULL,
    Mid INT NULL,    -- NULL == Mid could not be isolated from sequence
    Sequence VARCHAR(8000) NOT NULL,
    Quality VARCHAR(8000) NOT NULL,

    CONSTRAINT PK_Reads PRIMARY KEY (RowId)
);

-- TODO: Add run/analysis run, indexes
