ALTER TABLE enrollments
  ADD COLUMN extra_count int NOT NULL DEFAULT 0 COMMENT '周常报名额外同行人数，不含本人' AFTER rent_launcher;
