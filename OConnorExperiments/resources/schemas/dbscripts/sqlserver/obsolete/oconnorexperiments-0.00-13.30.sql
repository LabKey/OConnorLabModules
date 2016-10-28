/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

/* oconnorexperiments-0.00-13.10.sql */

CREATE SCHEMA oconnorexperiments;
GO

-- Experiments table has 1 row per workbook container
CREATE TABLE OConnorExperiments.Experiments
(
  Container EntityID NOT NULL,
  ModifiedBy USERID NOT NULL,
  Modified TIMESTAMP NOT NULL,

  ExperimentType VARCHAR(255),

  CONSTRAINT PK_Experiments PRIMARY KEY (Container),
  CONSTRAINT FK_Experiments_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityID)
);

-- ParentExperiments multi-value FK
CREATE TABLE OConnorExperiments.ParentExperiments
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Container EntityID NOT NULL,

  ParentExperiment EntityID NOT NULL,

  CONSTRAINT PK_ParentExperiments PRIMARY KEY (RowId),
  CONSTRAINT FK_ParentExperiments_Container FOREIGN KEY (ParentExperiment) REFERENCES OConnorExperiments.Experiments (Container)
);

/* oconnorexperiments-13.10-13.20.sql */

-- Modified column was mistakenly created as TIMESTAMP
ALTER TABLE oconnorexperiments.Experiments
    DROP COLUMN Modified;
GO
ALTER TABLE oconnorexperiments.Experiments
    ADD Modified DATETIME;
GO

UPDATE oconnorexperiments.Experiments SET Modified = CURRENT_TIMESTAMP;
GO

-- drop ParentExperiments.Container FK to allow bulk folder delete
EXEC core.fn_dropifexists 'ParentExperiments', 'OConnorExperiments', 'constraint', 'FK_ParentExperiments_Container';
GO

/* oconnorexperiments-13.20-13.30.sql */

ALTER TABLE OConnorExperiments.Experiments ADD GrantId INT;