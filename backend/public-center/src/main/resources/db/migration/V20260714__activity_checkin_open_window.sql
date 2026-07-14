ALTER TABLE activities
  ADD COLUMN checkin_open_value int NOT NULL DEFAULT 3 COMMENT '签到提前开放数值，0=活动开始时开放' AFTER checkin_methods,
  ADD COLUMN checkin_open_unit varchar(10) NOT NULL DEFAULT 'hour' COMMENT '签到提前开放单位：hour/day' AFTER checkin_open_value;
