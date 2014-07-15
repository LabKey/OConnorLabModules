SELECT
COUNT(p.item) as total_orders,
MAX(v.vendor) as vendor,
MAX(p.item) as item,
MAX(p.item_number) as item_number,
MAX(p.item_unit) as item_unit,
MAX(p.key) as record_id,
FROM purchases p
LEFT JOIN vendors v
ON p.vendor = v.vendor
GROUP BY item
ORDER BY total_orders DESC