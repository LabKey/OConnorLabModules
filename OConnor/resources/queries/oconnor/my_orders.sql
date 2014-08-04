/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
--find all orders placed by current user
--filter display items using xml and custom views

SELECT p.key,
p.container,
p.created,
p.modified,
p.createdBy,
p.modifiedBy,
p.order_number,
p.item,
p.item_unit,
p.placed_by,
p.item_number,
p.quantity,
p.price,
ROUND(p.price*p.quantity,2) as total_price,
p.grant_number,
p.vendor,
p.address,
p.confirmation_number,
p.status,
p.ordered_by,
p.ordered_date,
p.received_by,
p.received_date,
p.received_location,
p.invoice_number,
p.invoiced_date,
p.invoiced_by,
p.comment,
p.keyword
FROM purchases p
WHERE
p.createdBy=userid()