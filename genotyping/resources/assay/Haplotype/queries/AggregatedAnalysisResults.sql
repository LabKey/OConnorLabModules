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
    CASE WHEN iht.AssignmentCount > 2 THEN TRUE ELSE FALSE END AS InconsistentAssignments,
    iht.AssignmentCount,
    -- We don't know the names of all of the haplotype types (A, B, DR, STR, etc), so we have to select *
    iht.*
FROM
    genotyping.Animal AS a

        JOIN (
        -- Get all the haplotype assignments, pivoted by type (A, B, DR, STR, etc)
        SELECT
            HiddenAnimalId @Hidden,
            MIN(Haplotype1) AS Haplotype1,
            MIN(Haplotype2) AS Haplotype2,
            MIN(AssignmentCount) AS AssignmentCount @Hidden,
                Type
        FROM (
                 SELECT
                     aa.AnimalId AS HiddenAnimalId @Hidden, -- Hide so that we only get a single AnimalId column
                         MIN(CASE WHEN aha.DiploidNumber = 1 THEN h.Name ELSE NULL END) AS Haplotype1,
                     MIN(CASE WHEN aha.DiploidNumber = 2 THEN h.Name ELSE NULL END) AS Haplotype2,
                     COUNT (DISTINCT h.Name) AS AssignmentCount @Hidden,
                         h.Type AS Type
                 FROM genotyping.AnimalHaplotypeAssignment AS aha
                          JOIN genotyping.Haplotype AS h ON aha.HaplotypeId = h.RowId
                          JOIN genotyping.AnimalAnalysis AS aa ON aha.AnimalAnalysisId= aa.RowId
                 WHERE aa.Enabled = TRUE
                 GROUP BY aa.AnimalId, h.Type
             ) x
        GROUP BY HiddenAnimalId, Type
            PIVOT Haplotype1, Haplotype2 BY Type
    ) iht
             ON a.RowId = iht.HiddenAnimalId

        JOIN (
        -- Get the read counts
        SELECT
            AnimalId,
            SUM(TotalReads) AS TotalReads,
            SUM(IdentifiedReads) AS TotalIdentifiedReads,
            CAST(CASE WHEN SUM(TotalReads) = 0 THEN NULL ELSE ((1.0 - CAST(SUM(IdentifiedReads) AS DOUBLE) / CAST(SUM(TotalReads) AS DOUBLE)) * 100.0) END AS DOUBLE) AS TotalPercentUnknown
        FROM
            Data
        WHERE Enabled = TRUE
        GROUP BY AnimalId
    ) Summary
             ON a.RowId = Summary.AnimalId
