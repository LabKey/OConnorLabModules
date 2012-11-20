-- query to display which animal IDs have multiple "active" haplotype assignments
-- NOTE: the query checks the enabled state of both the Run and the Result records
SELECT * FROM (
  SELECT
  RunId.Protocol.RowId AS ProtocolId,
  AnimalId.LabAnimalId AS LabAnimalId,
  GROUP_CONCAT(RunId.RowId) AS RunIds,
  COUNT(AnimalId) AS NumActiveAssignments
  FROM Data
  WHERE Enabled=TRUE AND RunId.enabled=TRUE
  GROUP BY RunId.Protocol.RowId, AnimalId.LabAnimalId) AS x
WHERE x.NumActiveAssignments > 1