INSERT INTO attendance_events (
    source_activity_id,
    name,
    event_date,
    location,
    organizer,
    organizer_ids,
    activity_region,
    is_manual,
    created_by_id,
    created_at
)
SELECT
    a.id,
    a.name,
    DATE(a.start_at),
    a.location,
    COALESCE((
        SELECT GROUP_CONCAT(COALESCE(NULLIF(u.callsign, ''), u.username) ORDER BY u.id SEPARATOR '、')
        FROM users u
        WHERE FIND_IN_SET(CAST(u.id AS CHAR), COALESCE(NULLIF(a.organizer_ids, ''), CAST(a.created_by_id AS CHAR), '')) > 0
    ), ''),
    COALESCE(NULLIF(a.organizer_ids, ''), CAST(a.created_by_id AS CHAR), ''),
    a.activity_region,
    0,
    a.created_by_id,
    NOW()
FROM activities a
WHERE a.record_type = 'activity'
  AND a.start_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM attendance_events ev
      WHERE ev.source_activity_id = a.id
  );
