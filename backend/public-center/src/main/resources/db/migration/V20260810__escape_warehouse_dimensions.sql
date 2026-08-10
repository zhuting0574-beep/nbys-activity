-- 正式仓库规格：缓冲区 16 行 x 48 列，个人仓库 8 行 x 12 列。
-- 旧默认尺寸可以收缩；已有物品或通过商品扩容出的空间不会被截断。

ALTER TABLE escape_user_assets
  MODIFY COLUMN personal_width int NOT NULL DEFAULT 12,
  MODIFY COLUMN personal_height int NOT NULL DEFAULT 8,
  MODIFY COLUMN buffer_width int NOT NULL DEFAULT 48,
  MODIFY COLUMN buffer_height int NOT NULL DEFAULT 16;

UPDATE escape_user_assets asset
LEFT JOIN (
  SELECT inv.user_id,
    MAX(CASE WHEN inv.warehouse_type='personal' THEN inv.pos_x + item.width ELSE 0 END) personal_required_width,
    MAX(CASE WHEN inv.warehouse_type='personal' THEN inv.pos_y + item.height ELSE 0 END) personal_required_height,
    MAX(CASE WHEN inv.warehouse_type='buffer' THEN inv.pos_x + item.width ELSE 0 END) buffer_required_width,
    MAX(CASE WHEN inv.warehouse_type='buffer' THEN inv.pos_y + item.height ELSE 0 END) buffer_required_height
  FROM escape_inventory_instances inv
  JOIN escape_items item ON item.id=inv.item_id
  GROUP BY inv.user_id
) occupied ON occupied.user_id=asset.user_id
SET
  asset.personal_width=GREATEST(12,COALESCE(occupied.personal_required_width,0)),
  asset.personal_height=GREATEST(8,COALESCE(occupied.personal_required_height,0))
WHERE asset.personal_width=6 AND asset.personal_height=10;

UPDATE escape_user_assets asset
LEFT JOIN (
  SELECT inv.user_id,
    MAX(inv.pos_x + item.width) buffer_required_width,
    MAX(inv.pos_y + item.height) buffer_required_height
  FROM escape_inventory_instances inv
  JOIN escape_items item ON item.id=inv.item_id
  WHERE inv.warehouse_type='buffer'
  GROUP BY inv.user_id
) occupied ON occupied.user_id=asset.user_id
SET
  asset.buffer_width=GREATEST(48,COALESCE(occupied.buffer_required_width,0)),
  asset.buffer_height=GREATEST(16,COALESCE(occupied.buffer_required_height,0))
WHERE asset.buffer_width=12 AND asset.buffer_height=30;

UPDATE escape_user_assets asset
LEFT JOIN (
  SELECT inv.user_id,
    MAX(CASE WHEN inv.warehouse_type='personal' THEN inv.pos_x + item.width ELSE 0 END) personal_required_width,
    MAX(CASE WHEN inv.warehouse_type='personal' THEN inv.pos_y + item.height ELSE 0 END) personal_required_height,
    MAX(CASE WHEN inv.warehouse_type='buffer' THEN inv.pos_x + item.width ELSE 0 END) buffer_required_width,
    MAX(CASE WHEN inv.warehouse_type='buffer' THEN inv.pos_y + item.height ELSE 0 END) buffer_required_height
  FROM escape_inventory_instances inv
  JOIN escape_items item ON item.id=inv.item_id
  GROUP BY inv.user_id
) occupied ON occupied.user_id=asset.user_id
SET
  asset.personal_width=GREATEST(asset.personal_width,12,COALESCE(occupied.personal_required_width,0)),
  asset.personal_height=GREATEST(asset.personal_height,8,COALESCE(occupied.personal_required_height,0)),
  asset.buffer_width=GREATEST(48,COALESCE(occupied.buffer_required_width,0)),
  asset.buffer_height=GREATEST(16,COALESCE(occupied.buffer_required_height,0));
