-- Incremental schema for the rebuilt Spring Boot/Vue implementation.
-- Only adds fields/tables. It intentionally does not drop existing columns.

ALTER TABLE activities
  ADD COLUMN record_type varchar(20) NOT NULL DEFAULT 'activity' COMMENT 'activity=正式活动, plan=活动策划镜像' AFTER id,
  ADD COLUMN activity_type varchar(20) NOT NULL DEFAULT '周常' COMMENT '周常/本地活动/外地活动' AFTER name,
  ADD COLUMN banner_url varchar(500) DEFAULT NULL COMMENT '活动banner图' AFTER name,
  ADD COLUMN venue_id int DEFAULT NULL COMMENT '关联场地ID' AFTER location;

ALTER TABLE activity_plans
  ADD COLUMN banner_url varchar(500) DEFAULT NULL COMMENT '策划banner图' AFTER name;

ALTER TABLE plan_date_options
  ADD COLUMN remark varchar(200) DEFAULT NULL COMMENT '日期备注' AFTER date;

ALTER TABLE users
  ADD COLUMN avatar_url varchar(500) DEFAULT NULL COMMENT '用户头像' AFTER callsign,
  ADD COLUMN phone varchar(20) DEFAULT NULL COMMENT '手机号' AFTER avatar_url,
  ADD COLUMN id_card varchar(30) DEFAULT NULL COMMENT '身份证号' AFTER phone;

ALTER TABLE venues
  ADD COLUMN image_url varchar(500) DEFAULT NULL COMMENT '场地图片' AFTER address;

ALTER TABLE activity_launcher_rentals
  ADD COLUMN status varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/cancelled' AFTER user_id,
  ADD COLUMN confirmed_at datetime DEFAULT NULL AFTER rented_at;

CREATE TABLE IF NOT EXISTS activity_launcher_options (
  id int NOT NULL AUTO_INCREMENT,
  activity_id int NOT NULL,
  launcher_id int NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_launcher_option (activity_id, launcher_id),
  KEY idx_launcher_option_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS role_permissions (
  id int NOT NULL AUTO_INCREMENT,
  role varchar(20) NOT NULL,
  permission_code varchar(80) NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permission (role, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_notifications (
  id int NOT NULL AUTO_INCREMENT,
  user_id int NOT NULL,
  type varchar(40) NOT NULL,
  title varchar(120) NOT NULL,
  content varchar(500) NOT NULL,
  related_id int DEFAULT NULL,
  read_at datetime DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_read (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
