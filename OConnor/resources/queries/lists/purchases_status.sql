/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/* query purchase table and join to auditLog in order to extract the name of the person who initially created a purchase record */

SELECT 
MAX(p.key) AS key,
MAX(p.item) AS item,
MAX(p.item_number) AS item_number,
MAX(p.quantity) AS quantity,
MAX(p.item_unit) AS item_unit,
MAX(p.price) AS item_price,
(MAX(p.quantity)*MAX(p.price)) AS total_price,
MAX(p.grant_number) AS grant,
MAX(p.vendor) AS vendor,
MAX(p.address) AS address,
MAX(p.confirmation_number) AS confirmation_number,
MAX(p.ordered_date) AS ordered_date,
MAX(p.ordered_by) AS ordered_by,
MAX(p.received_location) AS received_location,
MAX(p.received_date) AS received_date,
MAX(p.received_by) AS received_by,
MAX(p.comment) AS comment,
MAX(p.status) AS status,
MIN(a.createdBy) AS placed_by,
MIN(a.date) AS place_date,
MAX(p.invoice_number) AS invoice_number,
MAX(p.invoiced_date) AS invoice_date,
MAX(p.invoiced_by) AS invoice_by
FROM oconnor.purchases p
LEFT JOIN auditLog.audit a
ON p.container=a.Key2
--kludge to display the user who created the record in the auditLog for orders entered in labkey. Legacy orders from xdhofs do not have records in labkey and are expempt.
WHERE a.comment='A new list record was inserted' OR p.status=5
GROUP BY p.container