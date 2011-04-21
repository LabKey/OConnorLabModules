/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
EXEC core.fn_dropifexists '*', 'genotyping', 'SCHEMA', NULL
GO

EXEC sp_addapprole 'genotyping', 'password'
GO

CREATE TABLE genotyping.Dictionaries
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,

    CONSTRAINT PK_Dictionaries PRIMARY KEY (RowId)
)

CREATE TABLE genotyping.Sequences
(
    RowId INT IDENTITY(1, 1) NOT NULL,
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
    Sequence TEXT NOT NULL,
    PreviousName VARCHAR(45) NULL,
    LastEdit DATETIME NOT NULL,
    Version INT NOT NULL,
    ModifiedBy VARCHAR(45) NOT NULL,
    Translation TEXT NULL,
    Type VARCHAR(45) NULL,
    IpdAccession VARCHAR(45) NULL,
    Reference INT NOT NULL,
    RegIon VARCHAR(45) NULL,
    Id INT NOT NULL,
    Variant INT NOT NULL,
    UploadId VARCHAR(45),
    FullLength INT NOT NULL,
    AlleleFamily VARCHAR(45),

    CONSTRAINT PK_Sequences PRIMARY KEY (RowId),
    CONSTRAINT UQ_AlleleName UNIQUE (Dictionary, AlleleName),
    CONSTRAINT UQ_Uid UNIQUE (Dictionary, Uid),
    CONSTRAINT FK_Sequences_Dictionary FOREIGN KEY (Dictionary) REFERENCES genotyping.Dictionaries(RowId)
)

CREATE TABLE genotyping.Runs
(
    RowId INT NOT NULL,           -- Lab assigns these (must be unique within the server)
    MetaDataId INT NULL,          -- Optional row ID of record with additional meta data about this run
    Container ENTITYID NOT NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    Path VARCHAR(1000) NOT NULL,
    FileName VARCHAR(200) NOT NULL,
    Status INT NOT NULL,

    CONSTRAINT PK_Runs PRIMARY KEY (RowId)
)
GO

CREATE TABLE genotyping.Reads
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Run INT NOT NULL,
    Name VARCHAR(20) NOT NULL,
    Mid INT NULL,    -- NULL == Mid could not be isolated from sequence
    Sequence VARCHAR(8000) NOT NULL,
    Quality VARCHAR(8000) NOT NULL,

    CONSTRAINT PK_Reads PRIMARY KEY NONCLUSTERED (RowId),
    CONSTRAINT FK_Reads_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId)
)

-- Create clustered index to help with base sort on reads table
CREATE CLUSTERED INDEX IDX_ReadsRunRowId ON genotyping.Reads (Run, RowId)
GO

CREATE TABLE genotyping.Analyses
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Run INT NOT NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    Description VARCHAR(8000) NULL,
    Path VARCHAR(1000) NULL,
    FileName VARCHAR(200) NULL,
    Status INT NOT NULL,
    SequenceDictionary INT NOT NULL,
    SequencesView VARCHAR(200) NULL,  -- NULL => default view

    CONSTRAINT PK_Analyses PRIMARY KEY (RowId),
    CONSTRAINT FK_Analyses_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId),
    CONSTRAINT FK_Analyses_Dictionaries FOREIGN KEY (SequenceDictionary) REFERENCES genotyping.Dictionaries(RowId)
)

CREATE TABLE genotyping.Matches
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Analysis INT NOT NULL,
    SampleId VARCHAR(200) NOT NULL,
    Reads INT NOT NULL,
    "Percent" REAL NOT NULL,
    AverageLength REAL NOT NULL,
    PosReads INT NOT NULL,
    NegReads INT NOT NULL,
    PosExtReads INT NOT NULL,
    NegExtReads INT NOT NULL,

    CONSTRAINT PK_Matches PRIMARY KEY (RowId),
    CONSTRAINT FK_Matches_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses(RowId)
)

CREATE TABLE genotyping.AnalysisSamples
(
    Analysis INT NOT NULL,
    SampleId VARCHAR(200) NOT NULL,

    CONSTRAINT FK_AnalysisSamples_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses(RowId)
)

-- Junction table that links each row of Matches to one or more allele sequences in Sequences table
CREATE TABLE genotyping.AllelesJunction
(
    MatchId INT NOT NULL,
    SequenceId INT NOT NULL,

    CONSTRAINT FK_AllelesJunction_Matches FOREIGN KEY (MatchId) REFERENCES genotyping.Matches(RowId),
    CONSTRAINT FK_AllelesJunction_Reads FOREIGN KEY (SequenceId) REFERENCES genotyping.Sequences(RowId)
)

-- Junction table that links each row of Matches to one or more rows in Reads table
CREATE TABLE genotyping.ReadsJunction
(
    MatchId INT NOT NULL,
    ReadId INT NOT NULL,

    CONSTRAINT FK_ReadsJunction_Matches FOREIGN KEY (MatchId) REFERENCES genotyping.Matches(RowId),
    CONSTRAINT FK_ReadsJunction_Reads FOREIGN KEY (ReadId) REFERENCES genotyping.Reads(RowId)
)
GO
