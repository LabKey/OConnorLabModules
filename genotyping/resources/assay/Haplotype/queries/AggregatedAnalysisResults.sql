/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
SELECT Data.AnimalId AS AnimalId ,
SUM(Data.TotalReads) AS "Total Reads",
SUM(Data.IdentifiedReads) AS "Total Identified Reads",
AVG(Data.PercentUnknown) AS "Total % Unknown",
MIN(cht.ConcatenatedHaplotypes) AS "Concatenated Haplotypes",
TRUE as "Enabled",
GROUP_CONCAT(DISTINCT Data.mamuAHaplotype1, ',') AS "Mamu-A Haplotype 1",
GROUP_CONCAT(DISTINCT Data.mamuAHaplotype2, ',') AS "Mamu-A Haplotype 2",
GROUP_CONCAT(DISTINCT Data.mamuBHaplotype1, ',') AS "Mamu-B Haplotype 1",
GROUP_CONCAT(DISTINCT Data.mamuBHaplotype2, ',') AS "Mamu-B Haplotype 2"

FROM Data
JOIN (
  SELECT aa.AnimalId, GROUP_CONCAT(DISTINCT h.Name, ',') AS ConcatenatedHaplotypes
  FROM genotyping.AnimalHaplotypeAssignment AS aha
  JOIN genotyping.Haplotype AS h ON aha.HaplotypeId = h.RowId
  JOIN genotyping.AnimalAnalysis AS aa ON aha.AnimalAnalysisId= aa.RowId
  GROUP BY aa.AnimalId
  ) AS cht
ON Data.AnimalId = cht.AnimalId

WHERE Data.Enabled = TRUE
GROUP BY Data.AnimalId