/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

/* genotyping-15.10-15.11.sql */

-- Added in modules15.1 branch - needs special handling if being rolled up into consolidated upgrade script

ALTER TABLE genotyping.AnimalHaplotypeAssignment ADD DiploidNumber INT;
ALTER TABLE genotyping.AnimalHaplotypeAssignment ADD DiploidNumberInferred BOOLEAN;

UPDATE genotyping.AnimalHaplotypeAssignment SET DiploidNumberInferred = true;

-- Misses a few cases where both copies have the same haplotype assignment
UPDATE genotyping.AnimalHaplotypeAssignment SET
  DiploidNumber = 1
WHERE
  RowId IN (
    SELECT
      aha.RowId
    FROM
      genotyping.AnimalHaplotypeAssignment aha
      INNER JOIN genotyping.Haplotype h ON (aha.HaplotypeId = h.RowId)
    WHERE
      h.Name =
      (SELECT MIN(h2.Name)
       FROM
         genotyping.AnimalHaplotypeAssignment aha2
         INNER JOIN genotyping.Haplotype h2 ON (aha2.HaplotypeId = h2.RowId)
       WHERE
         aha2.animalanalysisid = aha.animalanalysisid AND
         h.type = h2.type
      )
  );

UPDATE genotyping.AnimalHaplotypeAssignment SET
  DiploidNumber = 2
WHERE
  DiploidNumber IS NULL OR
  RowId IN (
    SELECT MaxRowId FROM (
                           SELECT
                             MAX(aha.RowId) AS MaxRowId,
                             COUNT(*) AS DupeCount
                           FROM
                             genotyping.AnimalHaplotypeAssignment aha
                             INNER JOIN genotyping.Haplotype h ON (aha.HaplotypeId = h.RowId)
                           WHERE
                             aha.DiploidNumber = 1
                           GROUP BY aha.AnimalAnalysisId, h.Type
                         ) x
    WHERE DupeCount > 1
  );

ALTER TABLE genotyping.AnimalHaplotypeAssignment ALTER COLUMN DiploidNumber SET NOT NULL;
ALTER TABLE genotyping.AnimalHaplotypeAssignment ALTER COLUMN DiploidNumberInferred SET NOT NULL;