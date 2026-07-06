ALTER TABLE activities
    ADD COLUMN organizer_ids varchar(500) NOT NULL DEFAULT '' COMMENT '组织人用户ID，逗号分隔' AFTER created_by_id;

ALTER TABLE activity_plans
    ADD COLUMN organizer_ids varchar(500) NOT NULL DEFAULT '' COMMENT '组织人用户ID，逗号分隔' AFTER created_by_id;

UPDATE activities
SET organizer_ids = CAST(created_by_id AS CHAR)
WHERE COALESCE(organizer_ids, '') = '' AND created_by_id IS NOT NULL;

UPDATE activity_plans
SET organizer_ids = CAST(created_by_id AS CHAR)
WHERE COALESCE(organizer_ids, '') = '' AND created_by_id IS NOT NULL;
