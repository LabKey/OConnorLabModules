/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/* retreives all fields from purchases list */

SELECT 
purchases.Key as Key,
purchases.item as item,
purchases.itemNumber,
purchases.quantity,
purchases.unit,
purchases.price,
purchases.vendor,
purchases.grant,
purchases.confirmationNumber as confirmationNumber,
purchases.location,
purchases.status,
purchases.fileAttachment,
purchases.orderedBy,
purchases.orderDate,
purchases.receivedBy,
purchases.receivedDate,
purchases.invoiceNumber,
purchases.invoiceDate,
purchases.invoiceBy,
purchases.comment,
purchases.keyword
FROM purchases

