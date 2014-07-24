/* filters on active grants and calculates a column on the grants list that concatenates the id and title of a grant. this column is displayed in pulldowns where grants are selected */

SELECT grants.id AS id,
grants.title AS title,
grants.fundingSource AS fundingSource,
grants.grantType AS grantType,
grants.department AS department,
grants.expirationDate AS expirationDate,
grants.comments AS comments,
grants.id || ' - ' || grants.title AS grantDescription
FROM oconnor.grants
WHERE oconnor.grants.expirationDate>'NOW()'