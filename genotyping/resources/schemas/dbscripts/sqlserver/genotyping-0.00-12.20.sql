/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

/* genotyping-0.00-11.20.sql */

CREATE SCHEMA genotyping;
GO

CREATE TABLE genotyping.Dictionaries
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,

    CONSTRAINT PK_Dictionaries PRIMARY KEY (RowId)
);

CREATE TABLE genotyping.Sequences
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Dictionary INT NOT NULL,
    Uid INT NOT NULL,
    AlleleName VARCHAR(100) NOT NULL,
    Initials VARCHAR(45) NULL,
    GenbankId VARCHAR(100) NULL,
    ExptNumber VARCHAR(45) NULL,
    Comments VARCHAR(255) NULL,
    Locus VARCHAR(45) NULL,
    Species VARCHAR(45) NULL,
    Origin VARCHAR(100) NULL,
    Sequence TEXT NOT NULL,
    PreviousName VARCHAR(100) NULL,
    LastEdit DATETIME NOT NULL,
    Version INT NOT NULL,
    ModifiedBy VARCHAR(45) NOT NULL,
    Translation TEXT NULL,
    Type VARCHAR(45) NULL,
    IpdAccession VARCHAR(100) NULL,
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
);

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
);

CREATE TABLE genotyping.Reads
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Run INT NOT NULL,
    Name VARCHAR(20) NOT NULL,
    Mid INT NULL,    -- NULL == Mid could not be isolated from sequence
    Sequence VARCHAR(8000) NOT NULL,
    Quality VARCHAR(8000) NOT NULL,

    CONSTRAINT PK_Reads PRIMARY KEY NONCLUSTERED (RowId),
    CONSTRAINT FK_Reads_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId),
    CONSTRAINT UQ_Reads_Name UNIQUE (Name)
);

-- Create clustered index to help with base sort on reads table
CREATE CLUSTERED INDEX IDX_ReadsRunRowId ON genotyping.Reads (Run, RowId);

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
);

CREATE TABLE genotyping.AnalysisSamples
(
    Analysis INT NOT NULL,
    SampleId INT NOT NULL,

    CONSTRAINT PK_AnalysisSamples PRIMARY KEY (Analysis, SampleId),
    CONSTRAINT FK_AnalysisSamples_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses(RowId)
);

CREATE TABLE genotyping.Matches
(
    RowId INT IDENTITY(1, 1) NOT NULL,
    Analysis INT NOT NULL,
    SampleId INT NOT NULL,
    Reads INT NOT NULL,
    "Percent" REAL NOT NULL,
    AverageLength REAL NOT NULL,
    PosReads INT NOT NULL,
    NegReads INT NOT NULL,
    PosExtReads INT NOT NULL,
    NegExtReads INT NOT NULL,

    CONSTRAINT PK_Matches PRIMARY KEY (RowId),
    CONSTRAINT FK_Matches_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses(RowId),
    CONSTRAINT FK_Matches_AnalysisSamples FOREIGN KEY (Analysis, SampleId) REFERENCES genotyping.AnalysisSamples(Analysis, SampleId)
);

-- Junction table that links each row of Matches to one or more allele sequences in Sequences table
CREATE TABLE genotyping.AllelesJunction
(
    MatchId INT NOT NULL,
    SequenceId INT NOT NULL,

    CONSTRAINT FK_AllelesJunction_Matches FOREIGN KEY (MatchId) REFERENCES genotyping.Matches(RowId),
    CONSTRAINT FK_AllelesJunction_Reads FOREIGN KEY (SequenceId) REFERENCES genotyping.Sequences(RowId)
);

-- Junction table that links each row of Matches to one or more rows in Reads table
CREATE TABLE genotyping.ReadsJunction
(
    MatchId INT NOT NULL,
    ReadId INT NOT NULL,

    CONSTRAINT FK_ReadsJunction_Matches FOREIGN KEY (MatchId) REFERENCES genotyping.Matches(RowId),
    CONSTRAINT FK_ReadsJunction_Reads FOREIGN KEY (ReadId) REFERENCES genotyping.Reads(RowId)
);

-- Add ParentId column -- when matches are combined or altered this is set to the new match's row id
-- We then filter out combined matches (ParentId IS NOT NULL) from normal analysis views.
ALTER TABLE genotyping.Matches
    ADD ParentId INT NULL;

ALTER TABLE genotyping.Sequences
    ALTER COLUMN Comments VARCHAR(4000);

-- Link reads directly to sample ids; SampleId replaces the Mid column, though we'll leave Mid in place for one release
ALTER TABLE genotyping.Reads ADD SampleId INT NULL;

-- Add indexes on all junction table columns to speed up deletes.
CREATE INDEX IX_ReadsJunction_MatchId ON genotyping.ReadsJunction (MatchId);
CREATE INDEX IX_ReadsJunction_ReadId ON genotyping.ReadsJunction (ReadId);
CREATE INDEX IX_AllelesJunction_MatchId ON genotyping.AllelesJunction (MatchId);
CREATE INDEX IX_AllelesJunction_SequenceId ON genotyping.AllelesJunction (SequenceId);

/* genotyping-11.20-11.30.sql */

-- Pull the analysis row id into the alleles junction table for performance

ALTER TABLE genotyping.AllelesJunction ADD Analysis INT;
GO

UPDATE genotyping.AllelesJunction SET Analysis = (SELECT Analysis FROM genotyping.Matches WHERE RowId = MatchId);

ALTER TABLE genotyping.AllelesJunction ALTER COLUMN Analysis INT NOT NULL;

CREATE INDEX IX_Analysis ON genotyping.AllelesJunction(Analysis);

ALTER TABLE genotyping.AllelesJunction
    ADD CONSTRAINT FK_AllelesJunction_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses (RowId);

/* genotyping-12.10-12.20.sql */

ALTER TABLE Genotyping.Runs
  add Platform varchar(200)
;
go

--all existing runs will be 454
UPDATE genotyping.Runs set Platform = 'LS454';

CREATE TABLE genotyping.SequenceFiles (
  RowId INT IDENTITY(1, 1),
  Run INTEGER NOT NULL,
  DataId INTEGER NOT NULL,
  SampleId INTEGER,
  CONSTRAINT FK_SequenceFiles_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId),
  CONSTRAINT FK_SequenceFiles_DataId FOREIGN KEY (DataId) REFERENCES exp.Data (RowId),
  CONSTRAINT PK_SequenceFiles PRIMARY KEY (rowid)
);

ALTER TABLE Genotyping.SequenceFiles
  add ReadCount integer
;

create table genotyping.IlluminaTemplates (
  name varchar(100) not null,
  json varchar(4000),
  editable bit default 1,

  CreatedBy USERID,
  Created datetime,
  ModifiedBy USERID,
  Modified datetime,

  constraint PK_illumina_templates PRIMARY KEY (name)
);

insert into genotyping.IlluminaTemplates (name, json, editable)
VALUES
('Default', '{' +
  '"Header": [["Template",""],["IEMFileVersion","3"],["Assay",""],["Chemistry","Default"]],' +
  '"Reads": [["151",""], ["151",""]]' +
  '}', 0
);

insert into genotyping.IlluminaTemplates (name, json, editable)
VALUES
('Resequencing', '{' +
  '"Header": [["Template","Resequencing"],["IEMFileVersion","3"],["Assay","TruSeq DNA/RNA"],["Chemistry","Default"]],' +
  '"Reads": [["151",""], ["151",""]],' +
  '"Settings": [["OnlyGenerateFASTQ","1"]]' +
  '}', 1
);

--convert the rowid of runs table from int to identity

ALTER TABLE genotyping.Reads DROP CONSTRAINT FK_Reads_Runs;
ALTER TABLE genotyping.Analyses DROP CONSTRAINT FK_Analyses_Runs;
ALTER TABLE genotyping.SequenceFiles DROP CONSTRAINT FK_SequenceFiles_Runs;

-- rename new table to old table's name
EXEC sp_rename 'genotyping.Runs','Runs2';
GO
EXEC sp_rename 'genotyping.PK_Runs','PK_Runs2', 'OBJECT';


CREATE TABLE genotyping.Runs
(
    RowId INT IDENTITY (1,1) NOT NULL,
    MetaDataId INT NULL,          -- Must match a row in the runs metadata list (chosen by lab)
    Container ENTITYID NOT NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    Path VARCHAR(1000) NOT NULL,
    FileName VARCHAR(200) NOT NULL,
    Status INT NOT NULL,
    Platform varchar(200)

    CONSTRAINT PK_Runs PRIMARY KEY (RowId)
);

SET IDENTITY_INSERT genotyping.Runs ON;

INSERT INTO genotyping.Runs (RowId, MetaDataId, Container, CreatedBy, Created, Path, FileName, Status, Platform)
  SELECT RowId, MetaDataId, Container, CreatedBy, Created, Path, FileName, Status, Platform FROM genotyping.Runs2;

SET IDENTITY_INSERT genotyping.Runs OFF;

-- drop the original (now empty) table
DROP TABLE genotyping.Runs2;

ALTER TABLE genotyping.Runs ADD CONSTRAINT UNIQUE_Runs UNIQUE (MetaDataId, Container)
ALTER TABLE genotyping.Reads ADD CONSTRAINT FK_Reads_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);
ALTER TABLE genotyping.Analyses ADD CONSTRAINT FK_Analyses_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);
ALTER TABLE genotyping.SequenceFiles ADD CONSTRAINT FK_SequenceFiles_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);
