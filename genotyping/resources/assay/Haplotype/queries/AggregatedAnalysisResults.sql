/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
-- query to display analysis results with one animal per row
-- NOTE: this query could probably use some optimization at some point.
SELECT
  Summary.AnimalId,
  Summary.TotalReads,
  Summary.TotalIdentifiedReads,
  Summary.TotalPercentUnknown,
  a.ConcatenatedHaplotypes,
  CASE WHEN ac.AssignmentCount > 2 THEN TRUE ELSE FALSE END AS InconsistentAssignments,
  ac.AssignmentCount,
  -- We don't know the names of all of the haplotype types (A, B, DR, STR, etc), so we have to select *
  iht.*
FROM (
  -- Get the read counts
  SELECT
    Data.AnimalId,
    SUM(Data.TotalReads) AS TotalReads,
    SUM(Data.IdentifiedReads) AS TotalIdentifiedReads,
    CAST(CASE WHEN SUM(Data.TotalReads) = 0 THEN NULL ELSE ((1.0 - CAST(SUM(Data.IdentifiedReads) AS DOUBLE) / CAST(SUM(Data.TotalReads) AS DOUBLE)) * 100.0) END AS DOUBLE) AS TotalPercentUnknown
  FROM
    Data
  WHERE Data.Enabled = TRUE
  GROUP BY Data.AnimalId
) Summary
JOIN genotyping.Animal AS a ON a.RowId = Summary.AnimalId
JOIN (
  -- Get all the haplotype assignments, pivoted by type (A, B, DR, STR, etc)
  SELECT
    aa.AnimalId AS HiddenAnimalId @Hidden, -- Hide so that we only get a single AnimalId column
    MIN(CASE WHEN aha.DiploidNumber = 1 THEN h.Name ELSE NULL END) AS Haplotype1,
    MIN(CASE WHEN aha.DiploidNumber = 2 THEN h.Name ELSE NULL END) AS Haplotype2,
    h.Type AS Type
  FROM genotyping.AnimalHaplotypeAssignment AS aha
  JOIN genotyping.Haplotype AS h ON aha.HaplotypeId = h.RowId
  JOIN genotyping.AnimalAnalysis AS aa ON aha.AnimalAnalysisId= aa.RowId
  WHERE aa.Enabled = TRUE
  GROUP BY aa.AnimalId, h.Type
  PIVOT Haplotype1, Haplotype2 BY Type
  ) iht
ON Summary.AnimalId = iht.HiddenAnimalId
JOIN (
  SELECT
    MAX(AssignmentCount) AS AssignmentCount,
    HiddenAnimalId @Hidden
  FROM (
    SELECT
      aa.AnimalId AS HiddenAnimalId @Hidden, -- Hide so that we only get a single AnimalId column
      COUNT (DISTINCT h.Name) AS AssignmentCount @Hidden
    FROM genotyping.AnimalHaplotypeAssignment AS aha
    JOIN genotyping.Haplotype AS h ON aha.HaplotypeId = h.RowId
    JOIN genotyping.AnimalAnalysis AS aa ON aha.AnimalAnalysisId= aa.RowId
    WHERE aa.Enabled = TRUE
    GROUP BY aa.AnimalId, h.Type
  ) X
  GROUP BY HiddenAnimalId
) ac
ON Summary.AnimalId = ac.HiddenAnimalId
