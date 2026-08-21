CREATE TABLE IF NOT EXISTS escape_shop_stock_tasks (
  business_date date NOT NULL,
  draw_hit tinyint(1) NOT NULL,
  scheduled_at datetime DEFAULT NULL,
  status varchar(20) NOT NULL COMMENT 'planned/skipped/completed',
  selected_count int NOT NULL DEFAULT 0,
  result_json longtext,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  executed_at datetime DEFAULT NULL,
  PRIMARY KEY (business_date),
  KEY idx_escape_shop_stock_task_due (status, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Collapse legacy duplicate item links before the application adds the unique index.
UPDATE escape_orders o
JOIN escape_shop_products duplicate_product ON duplicate_product.id=o.product_id
JOIN (
  SELECT item_id,MIN(id) canonical_id
  FROM escape_shop_products
  WHERE item_id IS NOT NULL
  GROUP BY item_id
) canonical ON canonical.item_id=duplicate_product.item_id
SET o.product_id=canonical.canonical_id
WHERE duplicate_product.id<>canonical.canonical_id;

UPDATE escape_shop_products canonical_product
JOIN (
  SELECT item_id,MIN(id) canonical_id,SUM(stock) total_stock,MAX(enabled) any_enabled
  FROM escape_shop_products
  WHERE item_id IS NOT NULL
  GROUP BY item_id
) merged ON merged.canonical_id=canonical_product.id
JOIN escape_items item ON item.id=merged.item_id
SET canonical_product.stock=LEAST(item.stock_quantity,merged.total_stock),
    canonical_product.enabled=merged.any_enabled,
    canonical_product.version=canonical_product.version+1;

DELETE duplicate_product
FROM escape_shop_products duplicate_product
JOIN (
  SELECT item_id,MIN(id) canonical_id
  FROM escape_shop_products
  WHERE item_id IS NOT NULL
  GROUP BY item_id
) canonical ON canonical.item_id=duplicate_product.item_id
WHERE duplicate_product.id<>canonical.canonical_id;
