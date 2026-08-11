-- 物品配置库存与战局物品池。

SET @escape_stock_sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE escape_items ADD COLUMN stock_quantity int NOT NULL DEFAULT 0 AFTER image_url',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'escape_items' AND column_name = 'stock_quantity'
);
PREPARE escape_stock_statement FROM @escape_stock_sql;
EXECUTE escape_stock_statement;
DEALLOCATE PREPARE escape_stock_statement;

CREATE TABLE IF NOT EXISTS escape_match_items (
  id bigint NOT NULL AUTO_INCREMENT,
  match_id bigint NOT NULL,
  item_id int NOT NULL,
  allocated_quantity int NOT NULL,
  consumed_quantity int NOT NULL DEFAULT 0,
  returned_quantity int NOT NULL DEFAULT 0,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_match_item (match_id, item_id),
  KEY idx_escape_match_item_template (item_id, match_id),
  CONSTRAINT chk_escape_match_item_quantity CHECK (
    allocated_quantity > 0 AND consumed_quantity >= 0 AND returned_quantity >= 0
    AND consumed_quantity + returned_quantity <= allocated_quantity
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
