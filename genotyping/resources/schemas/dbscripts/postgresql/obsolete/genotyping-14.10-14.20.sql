/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

/* genotyping-14.10-14.11.sql */

CREATE INDEX idx_animalhaplotypeassignment_animalanalysisid ON genotyping.animalhaplotypeassignment (animalanalysisid);

/* genotyping-14.11-14.12.sql */

CREATE TABLE genotyping.Species
(
  RowId SERIAL,
  Name VARCHAR(45),

  CONSTRAINT PK_Species PRIMARY KEY (RowId)
);

INSERT INTO genotyping.Species (Name) VALUES ('rhesus macaques');

ALTER TABLE genotyping.Animal ADD SpeciesId INT;
UPDATE genotyping.Animal SET SpeciesId = (SELECT RowId FROM genotyping.Species WHERE Name = 'rhesus macaques');
ALTER TABLE genotyping.Animal ALTER COLUMN SpeciesId SET NOT NULL;
ALTER TABLE genotyping.Animal ADD CONSTRAINT FK_Animal_SpeciesId FOREIGN KEY (SpeciesId) REFERENCES genotyping.Species(RowId);

ALTER TABLE genotyping.Haplotype ADD SpeciesId INT;
UPDATE genotyping.Haplotype SET SpeciesId = (SELECT RowId FROM genotyping.Species WHERE Name = 'rhesus macaques');
ALTER TABLE genotyping.Haplotype ALTER COLUMN SpeciesId SET NOT NULL;
ALTER TABLE genotyping.Haplotype ADD CONSTRAINT FK_Haplotype_SpeciesId FOREIGN KEY (SpeciesId) REFERENCES genotyping.Species(RowId);