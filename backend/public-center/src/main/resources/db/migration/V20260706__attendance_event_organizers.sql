ALTER TABLE attendance_events
    ADD COLUMN organizer_ids varchar(500) NOT NULL DEFAULT '' COMMENT '组织人用户ID，逗号分隔' AFTER organizer;

UPDATE attendance_events ev
JOIN users u ON TRIM(ev.organizer) = COALESCE(NULLIF(u.callsign, ''), u.username)
SET ev.organizer_ids = CAST(u.id AS CHAR)
WHERE COALESCE(ev.organizer_ids, '') = ''
  AND u.disabled = 0
  AND u.is_regular_member = 1;
