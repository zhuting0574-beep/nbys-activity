CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
  token_hash char(64) NOT NULL,
  user_id int NOT NULL,
  expires_at datetime NOT NULL,
  revoked_at datetime DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (token_hash),
  KEY idx_refresh_user (user_id),
  KEY idx_refresh_expiry (expires_at, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
