/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

/* genotyping-0.00-10.30.sql */

CREATE SCHEMA genotyping;

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
    AlleleName VARCHAR(100) NOT NULL,
    Initials VARCHAR(45) NULL,
    GenbankId VARCHAR(100) NULL,
    ExptNumber VARCHAR(45) NULL,
    Comments VARCHAR(4000) NULL,
    Locus VARCHAR(45) NULL,
    Species VARCHAR(45) NULL,
    Origin VARCHAR(100) NULL,
    Sequence TEXT NOT NULL,
    PreviousName VARCHAR(100) NULL,
    LastEdit TIMESTAMP NOT NULL,
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
    RowId SERIAL,
    MetaDataId INT NULL,          -- Optional row ID of record with additional meta data about this run
    Container ENTITYID NOT NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    Path VARCHAR(1000) NOT NULL,
    FileName VARCHAR(200) NOT NULL,
    Status INT NOT NULL,
    Platform varchar(200),

    CONSTRAINT PK_Runs PRIMARY KEY (RowId)
);

ALTER TABLE genotyping.runs
  ADD CONSTRAINT UNIQUE_Runs UNIQUE (rowid, container, metadataid);

CREATE TABLE genotyping.Reads
(
    RowId SERIAL,
    Run INT NOT NULL,
    Name VARCHAR(20) NOT NULL,
    Mid INT NULL,    -- NULL == Mid could not be isolated from sequence
    Sequence VARCHAR(8000) NOT NULL,
    Quality VARCHAR(8000) NOT NULL,
    -- Link reads directly to sample ids; SampleId replaces the Mid column, though we'll leave Mid in place for one release
    SampleId INT NULL,

    CONSTRAINT PK_Reads PRIMARY KEY (RowId),
    CONSTRAINT FK_Reads_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId),
    CONSTRAINT UQ_Reads_Name UNIQUE (Name)
);

-- Create clustered index to help with base sort on reads table
CREATE INDEX IDX_ReadsRunRowId ON genotyping.reads (Run, RowId);
ALTER TABLE genotyping.Reads CLUSTER ON IDX_ReadsRunRowId;
CLUSTER genotyping.Reads;

CREATE TABLE genotyping.Analyses
(
    RowId SERIAL,
    Run INT NOT NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
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
    RowId SERIAL,
    Analysis INT NOT NULL,
    SampleId INT NOT NULL,
    Reads INT NOT NULL,
    Percent REAL NOT NULL,
    AverageLength REAL NOT NULL,
    PosReads INT NOT NULL,
    NegReads INT NOT NULL,
    PosExtReads INT NOT NULL,
    NegExtReads INT NOT NULL,

    -- ParentId column -- when matches are combined or altered this is set to the new match's row id
    -- We then filter out combined matches (ParentId IS NOT NULL) from normal analysis views.
    ParentId INT NULL,

    CONSTRAINT PK_Matches PRIMARY KEY (RowId),
    CONSTRAINT FK_Matches_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses(RowId),
    CONSTRAINT FK_Matches_AnalysisSamples FOREIGN KEY (Analysis, SampleId) REFERENCES genotyping.AnalysisSamples(Analysis, SampleId)
);

-- Junction table that links each row of Matches to one or more allele sequences in Sequences table
CREATE TABLE genotyping.AllelesJunction
(
    MatchId INT NOT NULL,
    SequenceId INT NOT NULL,
    Analysis INT NOT NULL,

    CONSTRAINT FK_AllelesJunction_Matches FOREIGN KEY (MatchId) REFERENCES genotyping.Matches(RowId),
    CONSTRAINT FK_AllelesJunction_Reads FOREIGN KEY (SequenceId) REFERENCES genotyping.Sequences(RowId),
    CONSTRAINT FK_AllelesJunction_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses (RowId)
);

CREATE INDEX IX_Analysis ON genotyping.AllelesJunction(Analysis);

-- Junction table that links each row of Matches to one or more rows in Reads table
CREATE TABLE genotyping.ReadsJunction
(
    MatchId INT NOT NULL,
    ReadId INT NOT NULL,

    CONSTRAINT FK_ReadsJunction_Matches FOREIGN KEY (MatchId) REFERENCES genotyping.Matches(RowId),
    CONSTRAINT FK_ReadsJunction_Reads FOREIGN KEY (ReadId) REFERENCES genotyping.Reads(RowId)
);

/* genotyping-11.10-11.20.sql */

-- Add indexes on all junction table columns to speed up deletes.  Create only if they don't exist to accommodate 11.1
-- servers that have already run this script.
CREATE FUNCTION core.ensureIndex(text, text, text, text) RETURNS VOID AS $$
DECLARE
    index_name ALIAS FOR $1;
    schema_name ALIAS FOR $2;
    table_name ALIAS FOR $3;
    column_name ALIAS FOR $4;

    BEGIN
        IF NOT EXISTS(SELECT * FROM pg_indexes WHERE SchemaName = LOWER(schema_name) AND TableName = LOWER(table_name) AND IndexName = LOWER(index_name)) THEN
            EXECUTE 'CREATE INDEX ' || index_name || ' ON ' || schema_name || '.' || table_name || '(' || column_name || ')';
        END IF;
    END;
$$ LANGUAGE plpgsql;

SELECT core.ensureIndex('IX_ReadsJunction_MatchId', 'genotyping', 'ReadsJunction', 'MatchId');
SELECT core.ensureIndex('IX_ReadsJunction_ReadId', 'genotyping', 'ReadsJunction', 'ReadId');
SELECT core.ensureIndex('IX_AllelesJunction_MatchId', 'genotyping', 'AllelesJunction', 'MatchId');
SELECT core.ensureIndex('IX_AllelesJunction_SequenceId', 'genotyping', 'AllelesJunction', 'SequenceId');

DROP FUNCTION core.ensureIndex(text, text, text, text);

CREATE TABLE genotyping.SequenceFiles (
    RowId SERIAL,
    Run INTEGER NOT NULL,
    DataId INTEGER NOT NULL,
    SampleId INTEGER,
    ReadCount INTEGER,
    PoolNum INTEGER NULL,
    
  CONSTRAINT FK_SequenceFiles_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId),
  CONSTRAINT FK_SequenceFiles_DataId FOREIGN KEY (DataId) REFERENCES exp.Data (RowId),
  CONSTRAINT PK_SequenceFiles PRIMARY KEY (rowid)
);

create table genotyping.IlluminaTemplates (
  name varchar(100) not null,
  json varchar(4000),
  editable boolean default true,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,

  constraint PK_illumina_templates PRIMARY KEY (name)
);

insert into genotyping.IlluminaTemplates (name, json, editable)
VALUES
('Default', '{' ||
  '"Header": [["Template",""],["IEMFileVersion","3"],["Assay",""],["Chemistry","Default"]],' ||
  '"Reads": [["151",""], ["151",""]]' ||
  '}', false
);

insert into genotyping.IlluminaTemplates (name, json, editable)
VALUES
('Resequencing', '{' ||
  '"Header": [["Template","Resequencing"],["IEMFileVersion","3"],["Assay","TruSeq DNA/RNA"],["Chemistry","Default"]],' ||
  '"Reads": [["151",""], ["151",""]],' ||
  '"Settings": [["OnlyGenerateFASTQ","1"]]' ||
  '}', true
);

/* genotyping-12.20-12.30.sql */

CREATE TABLE genotyping.Species
(
    RowId SERIAL,
    Name VARCHAR(10),
    FullName VARCHAR(45),

    CONSTRAINT PK_Species PRIMARY KEY (RowId),
    CONSTRAINT Unique_Species_Name UNIQUE (Name),
    CONSTRAINT Unique_Species_FullName UNIQUE (FullName)
);

INSERT INTO genotyping.Species (Name, FullName) VALUES ('mamu', 'Macaca mulatta (rhesus)');
INSERT INTO genotyping.Species (Name, FullName) VALUES ('mane', 'Macaca nemestrina (pigtail)');
INSERT INTO genotyping.Species (Name, FullName) VALUES ('mafa', 'Macaca fascicularis (cynomolgus)');

CREATE TABLE genotyping.Animal
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    LabAnimalId VARCHAR(100),
    ClientAnimalId VARCHAR(100),
    Lsid LsidType,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,
    SpeciesId INT NOT NULL,

    CONSTRAINT PK_Animal PRIMARY KEY (RowId),
    CONSTRAINT UQ_Animal_LabAnimalId UNIQUE (Container, LabAnimalId),
    CONSTRAINT FK_Animal_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_Animal_Lsid FOREIGN KEY (Lsid) REFERENCES exp.Object(ObjectURI),
    CONSTRAINT FK_Animal_SpeciesId FOREIGN KEY (SpeciesId) REFERENCES genotyping.Species(RowId)
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
    Lsid LsidType,
    Type VARCHAR(20),
    SpeciesId INT NOT NULL,

    CONSTRAINT PK_Haplotype PRIMARY KEY (RowId),
    CONSTRAINT FK_Haplotype_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_Haplotype_Lsid FOREIGN KEY (Lsid) REFERENCES exp.Object(ObjectURI),
    CONSTRAINT FK_Haplotype_SpeciesId FOREIGN KEY (SpeciesId) REFERENCES genotyping.Species(RowId)
);

CREATE INDEX IDX_Haplotype_Container ON genotyping.Haplotype(Container);

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

CREATE TABLE genotyping.AnimalHaplotypeAssignment
(
    RowId SERIAL,
    AnimalAnalysisId INT NOT NULL,
    HaplotypeId INT NOT NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,
    DiploidNumber INT NOT NULL,
    DiploidNumberInferred BOOLEAN NOT NULL,

    CONSTRAINT PK_AnimalHaplotypeAssignment PRIMARY KEY (RowId),
    CONSTRAINT FK_AnimalHaplotypeAssignment_Haplotype FOREIGN KEY (HaplotypeId) REFERENCES genotyping.Haplotype(RowId),
    CONSTRAINT FK_AnimalHaplotypeAssignment_AnimalAnalysis FOREIGN KEY (AnimalAnalysisId) REFERENCES genotyping.AnimalAnalysis(RowId)
);

CREATE INDEX IDX_AnimalHaplotypeAssignment_Haplotype ON genotyping.AnimalHaplotypeAssignment(HaplotypeId);
CREATE INDEX idx_animalhaplotypeassignment_animalanalysisid ON genotyping.AnimalHaplotypeAssignment (AnimalAnalysisId);

