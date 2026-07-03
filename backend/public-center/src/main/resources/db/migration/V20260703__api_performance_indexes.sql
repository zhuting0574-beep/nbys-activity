ALTER TABLE attendance_events
    ADD INDEX idx_attendance_date_region_location (event_date, activity_region, location);

ALTER TABLE attendance_records
    ADD INDEX idx_attendance_event_present (event_id, present);

ALTER TABLE user_notifications
    ADD INDEX idx_notification_user_created (user_id, created_at, id);

ALTER TABLE activity_launcher_rentals
    ADD INDEX idx_rental_launcher_id (launcher_id, id);
