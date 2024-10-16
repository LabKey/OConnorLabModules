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

CREATE SCHEMA OConnorExperiments;

-- Experiments table has 1 row per workbook container
CREATE TABLE OConnorExperiments.Experiments
(
    Container ENTITYID NOT NULL,
    ModifiedBy USERID NOT NULL,
    Modified TIMESTAMP NOT NULL,

    ExperimentType VARCHAR(255),

    CONSTRAINT PK_Experiments PRIMARY KEY (Container),
    CONSTRAINT FK_Experiments_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityID)
);

-- ParentExperiments multi-value FK
CREATE TABLE OConnorExperiments.ParentExperiments
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,

    ParentExperiment ENTITYID NOT NULL,

    CONSTRAINT PK_ParentExperiments PRIMARY KEY (RowId),
    CONSTRAINT FK_ParentExperiments_Container FOREIGN KEY (ParentExperiment) REFERENCES OConnorExperiments.Experiments (Container)
);

/* oconnorexperiments-13.10-13.20.sql */

-- drop ParentExperiments.Container FK to allow bulk folder delete
SELECT core.fn_dropifexists('ParentExperiments', 'OConnorExperiments', 'constraint', 'FK_ParentExperiments_Container');

/* oconnorexperiments-13.20-13.30.sql */

ALTER TABLE OConnorExperiments.Experiments ADD COLUMN GrantId INT;

/* oconnorexperiments-14.10-14.20.sql */

CREATE TABLE OConnorExperiments.ExperimentType
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(255)
);

ALTER TABLE OConnorExperiments.ExperimentType ADD CONSTRAINT PK_ExperimentType PRIMARY KEY (RowId);
ALTER TABLE OConnorExperiments.ExperimentType ADD CONSTRAINT UQ_ExperimentType_Container_Name UNIQUE (Container, Name);
ALTER TABLE OConnorExperiments.ExperimentType ADD
	CONSTRAINT FK_ExperimentType_Container FOREIGN KEY
	(Container) REFERENCES core.Containers (EntityId);

INSERT INTO OConnorExperiments.ExperimentType (Name, Container) SELECT DISTINCT e.ExperimentType, c.Parent FROM
  OConnorExperiments.Experiments e, core.Containers c WHERE e.Container = c.EntityId;

ALTER TABLE OConnorExperiments.Experiments ADD COLUMN ExperimentTypeId INT;

UPDATE OConnorExperiments.Experiments SET ExperimentTypeId =
  (SELECT t.RowId FROM OConnorExperiments.ExperimentType t, core.Containers c
    WHERE t.Container = c.Parent AND Experiments.Container = c.EntityId and Experiments.ExperimentType = t.Name);

ALTER TABLE OConnorExperiments.Experiments DROP COLUMN ExperimentType;

ALTER TABLE OConnorExperiments.Experiments ADD
	CONSTRAINT FK_Experiments_ExperimentTypeId FOREIGN KEY
	(ExperimentTypeId) REFERENCES OConnorExperiments.ExperimentType (RowId);

CREATE INDEX idx_parent_experiment_container ON oconnorexperiments.parentexperiments(container);
CREATE INDEX idx_parent_experiment_parent_experiment ON oconnorexperiments.parentexperiments(parentexperiment);
CREATE INDEX idx_experiments_experimenttypeid ON oconnorexperiments.experiments(experimenttypeid);
CREATE INDEX idx_experiments_grantid ON oconnorexperiments.experiments(grantid);

/* oconnorexperiments-14.20-14.30.sql */

ALTER TABLE OConnorExperiments.ExperimentType ADD COLUMN Enabled BOOLEAN DEFAULT TRUE;
UPDATE OConnorExperiments.ExperimentType SET Enabled=TRUE;
ALTER TABLE OConnorExperiments.ExperimentType ALTER COLUMN Enabled SET NOT NULL;