UPDATE users
SET callsign=username
WHERE callsign IS NULL OR TRIM(callsign)='';

CREATE TABLE IF NOT EXISTS batch_task_configs (
  task_key varchar(60) NOT NULL,
  task_name varchar(120) NOT NULL,
  enabled tinyint(1) NOT NULL DEFAULT 1,
  cycle varchar(10) NOT NULL DEFAULT 'daily' COMMENT 'daily/weekly/monthly/yearly',
  start_at datetime NOT NULL,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(task_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO batch_task_configs(task_key,task_name,enabled,cycle,start_at) VALUES
('price-refresh','每日商品价格刷新',1,'daily','2026-01-01 05:00:00'),
('shop-stock-plan','商店库存每日计划',1,'daily','2026-01-01 05:00:00'),
('shop-stock-replenish','商店随机补货',1,'daily','2026-01-01 05:00:15'),
('buffer-liquidation','缓冲区自动出售',1,'daily','2026-01-01 05:00:00'),
('expired-season-cleanup','赛季到期自动清理',1,'daily','2026-01-01 00:00:05');

UPDATE activity_plans SET vote_mode='legacy' WHERE vote_mode IS NULL OR vote_mode='';
