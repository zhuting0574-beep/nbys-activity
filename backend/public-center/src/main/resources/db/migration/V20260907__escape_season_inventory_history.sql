ALTER TABLE escape_seasons
  ADD COLUMN inventory_cleared_at datetime DEFAULT NULL COMMENT '赛季库存归档清空时间' AFTER deleted_at;

-- 上线前已经结束的赛季没有可还原的库存归属，标记为已处理，避免首次启动误清当前库存。
UPDATE escape_seasons
SET inventory_cleared_at = NOW()
WHERE end_date < CURRENT_DATE AND inventory_cleared_at IS NULL;

CREATE TABLE IF NOT EXISTS escape_season_warehouse_snapshots (
  season_id int NOT NULL,
  user_id int NOT NULL,
  personal_width int NOT NULL,
  personal_height int NOT NULL,
  buffer_width int NOT NULL,
  buffer_height int NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (season_id, user_id),
  KEY idx_escape_wh_snapshot_user (user_id, season_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_season_inventory_snapshots (
  id bigint NOT NULL AUTO_INCREMENT,
  season_id int NOT NULL,
  user_id int NOT NULL,
  inventory_instance_id bigint NOT NULL,
  warehouse_type varchar(20) NOT NULL,
  pos_x int NOT NULL,
  pos_y int NOT NULL,
  item_id int DEFAULT NULL,
  item_name_snapshot varchar(100) NOT NULL,
  rarity_snapshot varchar(20) NOT NULL,
  category_snapshot varchar(20) NOT NULL,
  weapon_type_snapshot varchar(20) DEFAULT NULL,
  current_price_snapshot decimal(12,2) NOT NULL,
  width_snapshot int NOT NULL,
  height_snapshot int NOT NULL,
  image_url_snapshot varchar(500) DEFAULT NULL,
  durability_percent int DEFAULT NULL,
  status varchar(20) NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_inventory_snapshot (season_id, inventory_instance_id),
  KEY idx_escape_inventory_snapshot_user (user_id, season_id, warehouse_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
