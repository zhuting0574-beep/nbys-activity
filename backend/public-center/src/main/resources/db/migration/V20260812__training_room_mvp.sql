CREATE TABLE IF NOT EXISTS training_rooms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_code VARCHAR(24) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  room_type VARCHAR(20) NOT NULL DEFAULT 'solo',
  owner_user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'open',
  target_count INT NOT NULL DEFAULT 1,
  training_mode VARCHAR(24) NOT NULL DEFAULT 'precision',
  target_hits_json TEXT NOT NULL,
  precision_preset VARCHAR(12) NOT NULL DEFAULT 'A4',
  beep_min_delay DECIMAL(5,2) NOT NULL DEFAULT 2.00,
  beep_max_delay DECIMAL(5,2) NOT NULL DEFAULT 4.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_training_rooms_status (status),
  INDEX idx_training_rooms_owner (owner_user_id)
);

CREATE TABLE IF NOT EXISTS training_room_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  member_role VARCHAR(20) NOT NULL DEFAULT 'member',
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_training_room_member (room_id, user_id),
  CONSTRAINT fk_training_member_room FOREIGN KEY (room_id) REFERENCES training_rooms(id),
  INDEX idx_training_member_user (user_id)
);

CREATE TABLE IF NOT EXISTS training_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_code VARCHAR(40) NOT NULL UNIQUE,
  room_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'running',
  training_mode VARCHAR(24) NOT NULL,
  target_count INT NOT NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  duration_ms BIGINT NULL,
  total_hits INT NOT NULL DEFAULT 0,
  total_score DECIMAL(10,2) NULL,
  average_accuracy DECIMAL(10,2) NULL,
  deleted_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_training_session_room FOREIGN KEY (room_id) REFERENCES training_rooms(id),
  INDEX idx_training_session_user (user_id),
  INDEX idx_training_session_room (room_id),
  INDEX idx_training_session_created (created_at)
);

CREATE TABLE IF NOT EXISTS training_hits (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL UNIQUE,
  session_id BIGINT NOT NULL,
  target_no INT NOT NULL,
  shot_no INT NOT NULL,
  hit_at_ms BIGINT NOT NULL,
  split_ms BIGINT NULL,
  ring_score DECIMAL(5,2) NULL,
  accuracy DECIMAL(6,2) NULL,
  x_ratio DECIMAL(8,6) NULL,
  y_ratio DECIMAL(8,6) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_training_hit_session FOREIGN KEY (session_id) REFERENCES training_sessions(id),
  INDEX idx_training_hit_session_target (session_id, target_no, shot_no)
);

CREATE TABLE IF NOT EXISTS training_devices (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'offline',
  calibration_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  room_id BIGINT NULL,
  target_no INT NULL,
  frame_rate INT NULL,
  last_heartbeat_at DATETIME NULL,
  last_seen_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_training_device_status (status),
  INDEX idx_training_device_room_target (room_id, target_no)
);

CREATE TABLE IF NOT EXISTS training_audits (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  entity_type VARCHAR(30) NOT NULL,
  entity_id BIGINT NOT NULL,
  action VARCHAR(40) NOT NULL,
  reason VARCHAR(500) NULL,
  before_json TEXT NULL,
  after_json TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_training_audit_entity (entity_type, entity_id, created_at),
  INDEX idx_training_audit_actor (actor_user_id, created_at)
);
