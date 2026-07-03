ALTER TABLE users
  ADD COLUMN must_change_password tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否必须修改临时密码' AFTER password_hash,
  ADD COLUMN temp_password_expires_at datetime DEFAULT NULL COMMENT '临时密码过期时间' AFTER must_change_password;
