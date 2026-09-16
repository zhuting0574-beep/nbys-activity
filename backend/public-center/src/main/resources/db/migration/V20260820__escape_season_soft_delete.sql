ALTER TABLE escape_seasons
  ADD COLUMN deleted_at datetime DEFAULT NULL COMMENT '软删除时间' AFTER enabled;
