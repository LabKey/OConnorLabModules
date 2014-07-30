/*
 * Copyright (c) 2014 LabKey Corporation
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

CREATE TABLE OConnorExperiments.ExperimentType
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(255)
);

ALTER TABLE OConnorExperiments.ExperimentType ADD CONSTRAINT PK_ExperimentType PRIMARY KEY (RowId);
ALTER TABLE OConnorExperiments.ExperimentType ADD CONSTRAINT UQ_ExperimentType_Container_Name UNIQUE (Container, Name);
ALTER TABLE OConnorExperiments.ExperimentType ADD
	CONSTRAINT FK_ExperimentType_Container FOREIGN KEY
	(Container) REFERENCES core.Containers (EntityId);

INSERT INTO OConnorExperiments.ExperimentType (Name, Container) SELECT DISTINCT e.ExperimentType, c.Parent FROM
  OConnorExperiments.Experiments e, core.Containers c WHERE e.Container = c.EntityId;

ALTER TABLE OConnorExperiments.Experiments ADD ExperimentTypeId INT
GO

UPDATE OConnorExperiments.Experiments SET ExperimentTypeId =
  (SELECT t.RowId FROM OConnorExperiments.ExperimentType t, core.Containers c
    WHERE t.Container = c.Parent AND Experiments.Container = c.EntityId and Experiments.ExperimentType = t.Name);

ALTER TABLE OConnorExperiments.Experiments DROP COLUMN ExperimentType;

ALTER TABLE OConnorExperiments.Experiments ADD
	CONSTRAINT FK_Experiments_ExperimentTypeId FOREIGN KEY
	(ExperimentTypeId) REFERENCES OConnorExperiments.ExperimentType (RowId);
