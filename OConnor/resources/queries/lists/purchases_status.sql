/* query purchase table and join to auditLog in order to extract the name of the person who initially created a purchase record */

SELECT 
MAX(p.Key) AS Key,
MAX(p.item) AS item,
MAX(p.itemNumber) AS itemNumber,
MAX(p.quantity) AS quantity,
MAX(p.unit) AS unit,
MAX(p.price) AS itemPrice,
(MAX(p.quantity)*MAX(p.price)) AS totalPrice,
MAX(p.grant) AS grant,
MAX(p.vendor) AS vendor,
MAX(p.address) AS address,
MAX(p.confirmationNumber) AS confirmationNumber,
MAX(p.orderDate) AS orderDate,
MAX(p.orderedBy) AS orderedBy,
MAX(p.location) AS receivedLocation,
MAX(p.receivedDate) AS receivedDate,
MAX(p.receivedBy) AS receivedBy,
MAX(p.comment) AS comment,
MAX(p.status) AS status,
MIN(a.createdBy) AS placedBy,
MIN(a.date) AS placeDate,
MAX(p.invoiceNumber) AS invoiceNumber,
MAX(p.invoiceDate) AS invoiceDate,
MAX(p.invoiceBy) AS invoiceBy
FROM purchases p
LEFT JOIN auditLog.audit a
ON p.entityId=a.Key2
--kludge to display the user who created the record in the auditLog for orders entered in labkey. Legacy orders from xdhofs do not have records in labkey and are expempt.
WHERE a.comment='A new list record was inserted' OR p.status=5
GROUP BY p.entityID