package com.nbys.activity.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
public class DbMigrationRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public DbMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            addColumn("activities", "record_type", "varchar(20) NOT NULL DEFAULT 'activity' COMMENT 'activity=正式活动, plan=活动策划镜像'", "id");
            addColumn("activities", "activity_type", "varchar(20) NOT NULL DEFAULT '周常' COMMENT '周常/本地活动/外地活动'", "name");
            addColumn("activities", "banner_url", "varchar(500) DEFAULT NULL COMMENT '活动banner图'", "name");
            addColumn("activities", "external_miniapp_qr_url", "varchar(500) DEFAULT NULL COMMENT '外部第三方小程序活动二维码'", "banner_url");
            addColumn("activities", "banner_source", "varchar(20) NOT NULL DEFAULT 'venue' COMMENT 'custom=用户上传, venue=跟随场地默认图'", "banner_url");
            addColumn("activities", "venue_id", "int DEFAULT NULL COMMENT '关联场地ID'", "location");
            addColumn("activities", "checkin_methods", "varchar(30) NOT NULL DEFAULT 'location,qr' COMMENT '签到方式：location,qr'", "venue_id");
            addColumn("activities", "checkin_open_value", "int NOT NULL DEFAULT 3 COMMENT '签到提前开放数值，0=活动开始时开放'", "checkin_methods");
            addColumn("activities", "checkin_open_unit", "varchar(10) NOT NULL DEFAULT 'hour' COMMENT '签到提前开放单位：hour/day'", "checkin_open_value");
            addColumn("activities", "organizer_ids", "varchar(500) NOT NULL DEFAULT '' COMMENT '组织人用户ID，逗号分隔'", "created_by_id");
            addColumn("activity_plans", "banner_url", "varchar(500) DEFAULT NULL COMMENT '策划banner图'", "name");
            addColumn("activity_plans", "organizer_ids", "varchar(500) NOT NULL DEFAULT '' COMMENT '组织人用户ID，逗号分隔'", "created_by_id");
            jdbc.update("update activities set organizer_ids=cast(created_by_id as char) where coalesce(organizer_ids,'')='' and created_by_id is not null");
            jdbc.update("update activity_plans set organizer_ids=cast(created_by_id as char) where coalesce(organizer_ids,'')='' and created_by_id is not null");
            addColumn("plan_date_options", "remark", "varchar(200) DEFAULT NULL COMMENT '日期备注'", "date");
            addColumn("venues", "image_url", "varchar(500) DEFAULT NULL COMMENT '场地图片'", "address");
            addColumn("venues", "longitude", "decimal(10,7) DEFAULT NULL COMMENT 'WGS84/GCJ02 longitude for check-in'", "address");
            addColumn("venues", "latitude", "decimal(10,7) DEFAULT NULL COMMENT 'WGS84/GCJ02 latitude for check-in'", "longitude");
            addColumn("users", "avatar_url", "varchar(500) DEFAULT NULL COMMENT '用户头像'", "callsign");
            dropColumn("users", "phone");
            dropColumn("users", "id_card");
            addColumn("users", "must_change_password", "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否必须修改临时密码'", "password_hash");
            addColumn("users", "temp_password_expires_at", "datetime DEFAULT NULL COMMENT '临时密码过期时间'", "must_change_password");
            addColumn("enrollments", "extra_count", "int NOT NULL DEFAULT 0 COMMENT '周常报名额外同行人数，不含本人'", "rent_launcher");
            addColumn("activity_launcher_rentals", "status", "varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/cancelled'", "user_id");
            addColumn("activity_launcher_rentals", "confirmed_at", "datetime DEFAULT NULL", "rented_at");
            addColumn("escape_seasons", "deleted_at", "datetime DEFAULT NULL COMMENT '软删除时间'", "enabled");
            addColumn("attendance_events", "organizer_ids", "varchar(500) NOT NULL DEFAULT '' COMMENT '组织人用户ID，逗号分隔'", "organizer");
            jdbc.update("update attendance_events ev join users u on trim(ev.organizer)=coalesce(nullif(u.callsign,''),u.username) " +
                    "set ev.organizer_ids=cast(u.id as char) where coalesce(ev.organizer_ids,'')='' and u.disabled=0 and u.is_regular_member=1");
            jdbc.execute("create table if not exists activity_launcher_options (id int not null auto_increment, activity_id int not null, launcher_id int not null, created_at datetime not null default current_timestamp, primary key(id), unique key uk_activity_launcher_option(activity_id, launcher_id), key idx_launcher_option_activity(activity_id)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists role_permissions (id int not null auto_increment, role varchar(20) not null, permission_code varchar(80) not null, created_at datetime not null default current_timestamp, primary key(id), unique key uk_role_permission(role, permission_code)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists user_notifications (id int not null auto_increment, user_id int not null, type varchar(40) not null, title varchar(120) not null, content varchar(500) not null, related_id int default null, read_at datetime default null, created_at datetime not null default current_timestamp, primary key(id), key idx_user_read(user_id, read_at)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists system_settings (setting_key varchar(80) not null, setting_value varchar(1000) default null, updated_at datetime not null default current_timestamp on update current_timestamp, primary key(setting_key)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists homepage_carousel_images (id int not null auto_increment, section_key varchar(30) not null, image_url varchar(500) not null, sort_order int not null default 0, active tinyint(1) not null default 1, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, primary key(id), key idx_homepage_carousel_section(section_key,active,sort_order,id)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists auth_refresh_tokens (token_hash char(64) not null, user_id int not null, expires_at datetime not null, revoked_at datetime default null, created_at datetime not null default current_timestamp, last_used_at datetime not null default current_timestamp, primary key(token_hash), key idx_refresh_user(user_id), key idx_refresh_expiry(expires_at,revoked_at)) engine=InnoDB default charset=utf8mb4");
            addIndex("attendance_events", "idx_attendance_date_region_location", "(event_date, activity_region, location)");
            addIndex("attendance_records", "idx_attendance_event_present", "(event_id, present)");
            addIndex("user_notifications", "idx_notification_user_created", "(user_id, created_at, id)");
            addIndex("activity_launcher_rentals", "idx_rental_launcher_id", "(launcher_id, id)");
        } catch (Exception e) {
            System.err.println("Database migration skipped: " + e.getMessage());
        }
        try {
            runSqlResource("db/migration/V20260723__escape_from_xp_domain.sql");
            runSqlResource("db/migration/V20260810__escape_warehouse_dimensions.sql");
            runSqlResource("db/migration/V20260810__escape_match_item_stock.sql");
            runSqlResource("db/migration/V20260821__escape_shop_stock_schedule.sql");
            addColumn("escape_items", "weapon_type", "varchar(20) DEFAULT NULL COMMENT 'knife/regular/special'", "category");
            addColumn("escape_items", "material_type", "varchar(20) NOT NULL DEFAULT 'activity' COMMENT 'activity=活动物资,product=商品物资'", "category");
            jdbc.update("update escape_items i set material_type=case when exists (select 1 from escape_shop_products p where p.item_id=i.id) then 'product' else 'activity' end");
            addColumn("escape_items", "durability_loss_percent", "int DEFAULT NULL COMMENT '特殊武器每局耐久损耗百分比'", "weapon_type");
            addColumn("escape_match_participants", "special_weapon_confirmed", "tinyint(1) NOT NULL DEFAULT 0 COMMENT '后台已确认特殊武器风险'", "special_inventory_id");
            jdbc.update("update escape_items set durability_loss_percent=0 " +
                    "where category='weapon' and weapon_type='special' and durability_loss_percent is null");
            addIndex("escape_items", "idx_escape_item_weapon_type", "(category, weapon_type)");
            addUniqueIndex("escape_shop_products", "uk_escape_shop_product_item", "(item_id)");
            jdbc.update("update escape_weapons set enabled=0 where item_id is not null");
        } catch (Exception e) {
            throw new IllegalStateException("Escape from XP database migration failed", e);
        }
    }

    private void runSqlResource(String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(path));
        populator.setContinueOnError(false);
        populator.setIgnoreFailedDrops(true);
        populator.execute(Objects.requireNonNull(jdbc.getDataSource(), "DataSource unavailable"));
    }

    private void addColumn(String table, String column, String definition, String after) {
        Integer exists = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?", Integer.class, table, column);
        if (exists != null && exists == 0) {
            jdbc.execute("alter table " + table + " add column " + column + " " + definition + " after " + after);
        }
    }

    private void dropColumn(String table, String column) {
        Integer exists = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?", Integer.class, table, column);
        if (exists != null && exists > 0) {
            jdbc.execute("alter table " + table + " drop column " + column);
        }
    }

    private void addIndex(String table, String index, String columns) {
        Integer exists = jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and table_name=? and index_name=?", Integer.class, table, index);
        if (exists != null && exists == 0) jdbc.execute("alter table " + table + " add index " + index + " " + columns);
    }

    private void addUniqueIndex(String table, String index, String columns) {
        Integer exists = jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and table_name=? and index_name=?", Integer.class, table, index);
        if (exists != null && exists == 0) jdbc.execute("alter table " + table + " add unique index " + index + " " + columns);
    }
}
