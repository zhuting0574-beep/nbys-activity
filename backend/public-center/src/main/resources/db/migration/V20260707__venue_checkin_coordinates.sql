ALTER TABLE venues
  ADD COLUMN longitude decimal(10,7) DEFAULT NULL COMMENT '签到经度' AFTER address,
  ADD COLUMN latitude decimal(10,7) DEFAULT NULL COMMENT '签到纬度' AFTER longitude;
