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

/* genotyping-12.10-12.11.sql */

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

/* genotyping-12.11-12.12.sql */

ALTER TABLE Genotyping.SequenceFiles
  add ReadCount integer
;

/* genotyping-12.12-12.13.sql */

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

/* genotyping-12.13-12.14.sql */

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
GO
SET IDENTITY_INSERT genotyping.Runs ON;
GO
INSERT INTO genotyping.Runs (RowId, MetaDataId, Container, CreatedBy, Created, Path, FileName, Status, Platform)
  SELECT RowId, MetaDataId, Container, CreatedBy, Created, Path, FileName, Status, Platform FROM genotyping.Runs2;
GO
SET IDENTITY_INSERT genotyping.Runs OFF;

-- drop the original (now empty) table
DROP TABLE genotyping.Runs2;

ALTER TABLE genotyping.Runs ADD CONSTRAINT UNIQUE_Runs UNIQUE (MetaDataId, Container)
ALTER TABLE genotyping.Reads ADD CONSTRAINT FK_Reads_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);
ALTER TABLE genotyping.Analyses ADD CONSTRAINT FK_Analyses_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);
ALTER TABLE genotyping.SequenceFiles ADD CONSTRAINT FK_SequenceFiles_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId);