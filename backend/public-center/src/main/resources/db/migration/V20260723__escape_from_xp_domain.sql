-- 逃离西撇镇独立玩法域。仅新增表，不修改现有活动域数据。

CREATE TABLE IF NOT EXISTS escape_user_access (
  user_id int NOT NULL,
  enabled tinyint(1) NOT NULL DEFAULT 1,
  granted_by int DEFAULT NULL,
  granted_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version int NOT NULL DEFAULT 0,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  KEY idx_escape_access_enabled (enabled, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_seasons (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(80) NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  enabled tinyint(1) NOT NULL DEFAULT 1,
  kill_reward decimal(12,2) NOT NULL DEFAULT 0,
  version int NOT NULL DEFAULT 0,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_escape_season_active (enabled, start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_items (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(100) NOT NULL,
  rarity varchar(20) NOT NULL COMMENT 'extraordinary/epic/fine/normal',
  category varchar(20) NOT NULL COMMENT 'regular/weapon',
  min_price decimal(12,2) NOT NULL,
  max_price decimal(12,2) NOT NULL,
  current_price decimal(12,2) NOT NULL,
  previous_price decimal(12,2) DEFAULT NULL,
  width int NOT NULL DEFAULT 1,
  height int NOT NULL DEFAULT 1,
  image_url varchar(500) DEFAULT NULL,
  enabled tinyint(1) NOT NULL DEFAULT 1,
  deleted_at datetime DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_escape_item_category (enabled, deleted_at, category, rarity),
  CONSTRAINT chk_escape_item_price CHECK (min_price >= 0 AND max_price >= min_price),
  CONSTRAINT chk_escape_item_size CHECK (width > 0 AND height > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_user_assets (
  user_id int NOT NULL,
  cash_balance decimal(14,2) NOT NULL DEFAULT 0,
  personal_width int NOT NULL DEFAULT 6,
  personal_height int NOT NULL DEFAULT 10,
  buffer_width int NOT NULL DEFAULT 12,
  buffer_height int NOT NULL DEFAULT 30,
  version int NOT NULL DEFAULT 0,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT chk_escape_asset_cash CHECK (cash_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_inventory_instances (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id int NOT NULL,
  item_id int NOT NULL,
  warehouse_type varchar(20) NOT NULL COMMENT 'personal/buffer',
  pos_x int NOT NULL,
  pos_y int NOT NULL,
  durability_percent int DEFAULT NULL,
  status varchar(20) NOT NULL DEFAULT 'available' COMMENT 'available/loadout_locked/in_match',
  source_type varchar(30) NOT NULL DEFAULT 'admin',
  source_id bigint DEFAULT NULL,
  acquired_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version int NOT NULL DEFAULT 0,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_escape_inventory_user_wh (user_id, warehouse_type, status),
  KEY idx_escape_inventory_item (item_id),
  CONSTRAINT chk_escape_inventory_position CHECK (pos_x >= 0 AND pos_y >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_shop_products (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(100) NOT NULL,
  product_type varchar(20) NOT NULL COMMENT 'expansion/regular/weapon',
  item_id int DEFAULT NULL,
  price decimal(12,2) NOT NULL,
  stock int NOT NULL,
  warehouse_width int DEFAULT NULL,
  warehouse_height int DEFAULT NULL,
  off_shelf_at datetime DEFAULT NULL,
  enabled tinyint(1) NOT NULL DEFAULT 1,
  version int NOT NULL DEFAULT 0,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_escape_product_online (enabled, off_shelf_at, id),
  CONSTRAINT chk_escape_product_price CHECK (price >= 0),
  CONSTRAINT chk_escape_product_stock CHECK (stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_professions (
  id int NOT NULL AUTO_INCREMENT,
  name varchar(50) NOT NULL,
  health int NOT NULL,
  maintenance_fee decimal(12,2) NOT NULL DEFAULT 0,
  knife_only tinyint(1) NOT NULL DEFAULT 0,
  enabled tinyint(1) NOT NULL DEFAULT 1,
  sort_order int NOT NULL DEFAULT 0,
  version int NOT NULL DEFAULT 0,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_profession_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_weapons (
  id int NOT NULL AUTO_INCREMENT,
  item_id int DEFAULT NULL COMMENT '特殊武器必须绑定武器类物品模板',
  name varchar(80) NOT NULL,
  weapon_type varchar(20) NOT NULL COMMENT 'knife/regular/special',
  usage_fee decimal(12,2) NOT NULL DEFAULT 0,
  durability_loss_percent int NOT NULL DEFAULT 0,
  enabled tinyint(1) NOT NULL DEFAULT 1,
  sort_order int NOT NULL DEFAULT 0,
  version int NOT NULL DEFAULT 0,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_weapon_name_type (name, weapon_type),
  UNIQUE KEY uk_escape_weapon_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_matches (
  id bigint NOT NULL AUTO_INCREMENT,
  season_id int DEFAULT NULL,
  name varchar(100) NOT NULL,
  venue_id int DEFAULT NULL,
  team_count int NOT NULL,
  team_capacity int NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'preparing' COMMENT 'preparing/in_progress/settled/cancelled',
  created_by int NOT NULL,
  started_at datetime DEFAULT NULL,
  settled_at datetime DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_escape_match_status (status, id),
  KEY idx_escape_match_season (season_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_match_participants (
  id bigint NOT NULL AUTO_INCREMENT,
  match_id bigint NOT NULL,
  user_id int NOT NULL,
  team_no int DEFAULT NULL,
  profession_id int DEFAULT NULL,
  weapon_id int DEFAULT NULL,
  special_inventory_id bigint DEFAULT NULL,
  loadout_status varchar(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/locked/in_match/settled',
  `escaped` tinyint(1) DEFAULT NULL,
  kills int NOT NULL DEFAULT 0,
  manual_cash decimal(12,2) NOT NULL DEFAULT 0,
  kill_cash decimal(12,2) NOT NULL DEFAULT 0,
  joined_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_at datetime DEFAULT NULL,
  settled_at datetime DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_match_user (match_id, user_id),
  KEY idx_escape_participant_user (user_id, match_id),
  KEY idx_escape_participant_team (match_id, team_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_cash_ledger (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id int NOT NULL,
  amount decimal(14,2) NOT NULL,
  balance_after decimal(14,2) NOT NULL,
  business_type varchar(30) NOT NULL,
  business_id varchar(80) DEFAULT NULL,
  description varchar(200) NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_cash_business (user_id, business_type, business_id),
  KEY idx_escape_cash_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_orders (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id int NOT NULL,
  product_id int NOT NULL,
  quantity int NOT NULL,
  total_amount decimal(14,2) NOT NULL,
  idempotency_key varchar(80) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'completed',
  version int NOT NULL DEFAULT 0,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_order_idempotency (user_id, idempotency_key),
  KEY idx_escape_order_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_operation_idempotency (
  user_id int NOT NULL,
  operation_type varchar(40) NOT NULL,
  idempotency_key varchar(80) NOT NULL,
  result_reference varchar(100) DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, operation_type, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_price_refresh_runs (
  business_date date NOT NULL,
  refreshed_items int NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_admin_audit_log (
  id bigint NOT NULL AUTO_INCREMENT,
  actor_user_id int NOT NULL,
  permission_code varchar(40) NOT NULL,
  action varchar(50) NOT NULL,
  entity_type varchar(40) NOT NULL,
  entity_id varchar(80) DEFAULT NULL,
  request_snapshot longtext,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_escape_audit_actor (actor_user_id, id),
  KEY idx_escape_audit_entity (entity_type, entity_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_item_grants (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id int NOT NULL,
  item_id int NOT NULL,
  quantity int NOT NULL,
  reason varchar(200) NOT NULL,
  operator_user_id int NOT NULL,
  idempotency_key varchar(80) NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_grant_idempotency (operator_user_id, idempotency_key),
  KEY idx_escape_grant_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 结算是不可变业务凭证：participant 上的字段是查询投影，最终事实以此处为准。
CREATE TABLE IF NOT EXISTS escape_match_settlements (
  id bigint NOT NULL AUTO_INCREMENT,
  match_id bigint NOT NULL,
  season_id int DEFAULT NULL,
  kill_reward_snapshot decimal(12,2) NOT NULL DEFAULT 0,
  settled_by int NOT NULL,
  idempotency_key varchar(80) NOT NULL,
  settled_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_settlement_match (match_id),
  UNIQUE KEY uk_escape_settlement_idempotency (settled_by, idempotency_key),
  KEY idx_escape_settlement_season (season_id, settled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_match_settlement_details (
  id bigint NOT NULL AUTO_INCREMENT,
  settlement_id bigint NOT NULL,
  participant_id bigint NOT NULL,
  user_id int NOT NULL,
  callsign_snapshot varchar(100) NOT NULL,
  team_no int DEFAULT NULL,
  profession_id int DEFAULT NULL,
  profession_name_snapshot varchar(50) DEFAULT NULL,
  weapon_id int DEFAULT NULL,
  weapon_name_snapshot varchar(80) DEFAULT NULL,
  special_inventory_id bigint DEFAULT NULL,
  `escaped` tinyint(1) NOT NULL,
  kills int NOT NULL DEFAULT 0,
  manual_cash decimal(12,2) NOT NULL DEFAULT 0,
  kill_cash decimal(12,2) NOT NULL DEFAULT 0,
  cash_before decimal(14,2) NOT NULL,
  cash_after decimal(14,2) NOT NULL,
  special_weapon_outcome varchar(30) DEFAULT NULL COMMENT 'returned/destroyed/not_used',
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_escape_settlement_participant (settlement_id, participant_id),
  KEY idx_escape_settlement_user (user_id, settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS escape_match_settlement_items (
  id bigint NOT NULL AUTO_INCREMENT,
  settlement_detail_id bigint NOT NULL,
  item_id int NOT NULL,
  item_name_snapshot varchar(100) NOT NULL,
  rarity_snapshot varchar(20) NOT NULL,
  price_snapshot decimal(12,2) NOT NULL,
  quantity int NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_escape_settlement_item_detail (settlement_detail_id, id),
  KEY idx_escape_settlement_item_template (item_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO escape_professions(name, health, maintenance_fee, knife_only, sort_order) VALUES
('跑刀仔', 100, 0, 1, 10),
('拖鞋军', 120, 0, 0, 20),
('正规军', 150, 0, 0, 30),
('重装兵', 200, 0, 0, 40);

INSERT IGNORE INTO escape_weapons(name, weapon_type, usage_fee, sort_order) VALUES
('战术刀', 'knife', 0, 10);

INSERT IGNORE INTO role_permissions(role, permission_code, created_at) VALUES
('escape_admin', 'escape:view', now()),
('escape_admin', 'escape:match', now()),
('escape_admin', 'escape:config', now()),
('escape_admin', 'escape:assets', now()),
('escape_admin', 'escape:settle', now()),
('escape_admin', 'escape:audit', now()),
('escape_admin', 'escape:match:view', now()),
('escape_admin', 'escape:match:create', now()),
('escape_admin', 'escape:match:update', now()),
('escape_admin', 'escape:match:delete', now()),
('escape_admin', 'escape:match:start', now()),
('escape_admin', 'escape:match:settle', now()),
('escape_admin', 'escape:item:view', now()),
('escape_admin', 'escape:item:create', now()),
('escape_admin', 'escape:item:update', now()),
('escape_admin', 'escape:item:delete', now()),
('escape_admin', 'escape:shop:view', now()),
('escape_admin', 'escape:shop:create', now()),
('escape_admin', 'escape:shop:update', now()),
('escape_admin', 'escape:shop:delete', now()),
('escape_admin', 'escape:season:view', now()),
('escape_admin', 'escape:season:create', now()),
('escape_admin', 'escape:season:update', now()),
('escape_admin', 'escape:season:delete', now()),
('escape_admin', 'escape:class:view', now()),
('escape_admin', 'escape:class:create', now()),
('escape_admin', 'escape:class:update', now()),
('escape_admin', 'escape:class:delete', now()),
('escape_admin', 'escape:weapon:view', now()),
('escape_admin', 'escape:weapon:create', now()),
('escape_admin', 'escape:weapon:update', now()),
('escape_admin', 'escape:weapon:delete', now()),
('escape_admin', 'escape:userAsset:view', now()),
('escape_admin', 'escape:userAsset:adjust', now()),
('escape_admin', 'escape:itemGrant:create', now());
