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
  add column Platform varchar(200)
;

--all existing runs will be 454
UPDATE genotyping.Runs set Platform = 'LS454';

CREATE TABLE genotyping.SequenceFiles (
  RowId SERIAL,
  Run INTEGER NOT NULL,
  DataId INTEGER NOT NULL,
  SampleId INTEGER,
  CONSTRAINT FK_SequenceFiles_Runs FOREIGN KEY (Run) REFERENCES genotyping.Runs (RowId),
  CONSTRAINT FK_SequenceFiles_DataId FOREIGN KEY (DataId) REFERENCES exp.Data (RowId),
  CONSTRAINT PK_SequenceFiles PRIMARY KEY (rowid)
);

/* genotyping-12.11-12.12.sql */

ALTER TABLE Genotyping.SequenceFiles
  add column ReadCount integer
;

/* genotyping-12.12-12.13.sql */

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

/* genotyping-12.13-12.14.sql */

--convert the rowid of runs table from int to serial
CREATE SEQUENCE genotyping.runs_rowid_seq
  INCREMENT 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 OWNED BY genotyping.runs.rowid;

select setval('genotyping.runs_rowid_seq', (select max(rowid)+1 from genotyping.runs), false);

ALTER TABLE genotyping.runs ALTER COLUMN rowid
  SET DEFAULT nextval('genotyping.runs_rowid_seq'::regclass);

ALTER TABLE genotyping.runs ADD CONSTRAINT UNIQUE_Runs UNIQUE (container, metadataid);