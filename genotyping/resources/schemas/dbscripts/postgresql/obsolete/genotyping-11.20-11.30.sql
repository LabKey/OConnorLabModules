/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

-- Pull the analysis row id into the alleles junction table for performance

ALTER TABLE genotyping.AllelesJunction ADD COLUMN Analysis INTEGER;

UPDATE genotyping.AllelesJunction SET Analysis = (SELECT Analysis FROM genotyping.Matches WHERE RowId = MatchId);

ALTER TABLE genotyping.AllelesJunction ALTER COLUMN Analysis SET NOT NULL;

CREATE INDEX IX_Analysis ON genotyping.AllelesJunction(Analysis);

ALTER TABLE genotyping.AllelesJunction
    ADD CONSTRAINT FK_AllelesJunction_Analyses FOREIGN KEY (Analysis) REFERENCES genotyping.Analyses (RowId);
