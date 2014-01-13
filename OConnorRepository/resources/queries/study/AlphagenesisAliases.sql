SELECT
 Id,
 Id as ContractId,
 sourceId,
 'Alphagenesis' as Source,
 Modified
FROM study.Demographics
WHERE sourceId IS NOT NULL
