/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/* retreives all fields from purchases list */

SELECT 
purchases.Key as key,
purchases.item,
purchases.item_number,
purchases.quantity,
purchases.item_unit,
purchases.price,
purchases.vendor,
purchases.grant_number,
purchases.confirmation_number,
purchases.received_location,
purchases.status,
purchases.ordered_by,
purchases.ordered_date,
purchases.received_by,
purchases.received_date,
purchases.invoice_number,
purchases.invoiced_date,
purchases.invoiced_by,
purchases.comment,
purchases.keyword
FROM oconnor.purchases

