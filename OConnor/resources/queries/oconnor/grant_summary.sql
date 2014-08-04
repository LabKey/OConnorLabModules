/*
 * Copyright (c) 2014 David O'Connor
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT
g.id,
MAX(g.title) as grant_title,
ROUND(SUM(p.quantity*p.price),2) as total_spent,
ROUND(MAX(g.budget) - SUM(p.quantity*p.price),2) as total_remaining
from grants g
LEFT JOIN purchases p
ON
g.id=p.grant_number
GROUP BY g.id
