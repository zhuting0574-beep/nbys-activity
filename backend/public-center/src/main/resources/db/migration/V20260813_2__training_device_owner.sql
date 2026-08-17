ALTER TABLE training_devices
  ADD COLUMN IF NOT EXISTS owner_user_id BIGINT NULL AFTER calibration_status,
  ADD INDEX idx_training_device_owner_heartbeat (owner_user_id, last_heartbeat_at);
