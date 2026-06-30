package com.nbys.activity.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
            addColumn("activities", "banner_source", "varchar(20) NOT NULL DEFAULT 'venue' COMMENT 'custom=用户上传, venue=跟随场地默认图'", "banner_url");
            addColumn("activities", "venue_id", "int DEFAULT NULL COMMENT '关联场地ID'", "location");
            addColumn("activity_plans", "banner_url", "varchar(500) DEFAULT NULL COMMENT '策划banner图'", "name");
            addColumn("plan_date_options", "remark", "varchar(200) DEFAULT NULL COMMENT '日期备注'", "date");
            addColumn("venues", "image_url", "varchar(500) DEFAULT NULL COMMENT '场地图片'", "address");
            addColumn("users", "avatar_url", "varchar(500) DEFAULT NULL COMMENT '用户头像'", "callsign");
            addColumn("users", "phone", "varchar(20) DEFAULT NULL COMMENT '手机号'", "avatar_url");
            addColumn("users", "id_card", "varchar(30) DEFAULT NULL COMMENT '身份证号'", "phone");
            addColumn("activity_launcher_rentals", "status", "varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/cancelled'", "user_id");
            addColumn("activity_launcher_rentals", "confirmed_at", "datetime DEFAULT NULL", "rented_at");
            jdbc.execute("create table if not exists activity_launcher_options (id int not null auto_increment, activity_id int not null, launcher_id int not null, created_at datetime not null default current_timestamp, primary key(id), unique key uk_activity_launcher_option(activity_id, launcher_id), key idx_launcher_option_activity(activity_id)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists role_permissions (id int not null auto_increment, role varchar(20) not null, permission_code varchar(80) not null, created_at datetime not null default current_timestamp, primary key(id), unique key uk_role_permission(role, permission_code)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists user_notifications (id int not null auto_increment, user_id int not null, type varchar(40) not null, title varchar(120) not null, content varchar(500) not null, related_id int default null, read_at datetime default null, created_at datetime not null default current_timestamp, primary key(id), key idx_user_read(user_id, read_at)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists system_settings (setting_key varchar(80) not null, setting_value varchar(1000) default null, updated_at datetime not null default current_timestamp on update current_timestamp, primary key(setting_key)) engine=InnoDB default charset=utf8mb4");
            createExtractionTables();
        } catch (Exception e) {
            System.err.println("Database migration skipped: " + e.getMessage());
        }
    }

    private void createExtractionTables() {
        jdbc.execute("create table if not exists extraction_seasons (id int not null auto_increment, name varchar(120) not null, start_date date not null, end_date date not null, active tinyint(1) not null default 1, kill_reward_cash int not null default 0, created_by_id int default null, created_at datetime not null default current_timestamp, primary key(id), key idx_extraction_season_active(active,start_date,end_date)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_item_defs (id int not null auto_increment, name varchar(120) not null, level varchar(20) not null default '普通', item_category varchar(20) not null default '常规物品', min_price int not null default 1, max_price int not null default 1, width int not null default 1, height int not null default 1, photo_filename varchar(500) default null, active tinyint(1) not null default 1, created_by_id int default null, created_at datetime not null default current_timestamp, primary key(id), key idx_extraction_item_active(active), key idx_extraction_item_category(item_category)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_item_prices (id int not null auto_increment, item_def_id int not null, price_date date not null, price int not null, created_at datetime not null default current_timestamp, primary key(id), unique key uq_extraction_item_price_date(item_def_id,price_date), key idx_extraction_price_date(price_date)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_user_profiles (id int not null auto_increment, user_id int not null, cash int not null default 0, storage_rows int not null default 8, storage_cols int not null default 12, runs_count int not null default 0, updated_at datetime not null default current_timestamp on update current_timestamp, primary key(id), unique key uk_extraction_profile_user(user_id)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_inventory_items (id int not null auto_increment, user_id int not null, item_def_id int not null, season_id int default null, location varchar(20) not null default 'buffer', `row` int default null, `col` int default null, sold_at datetime default null, sold_price int default null, quantity int not null default 1, durability_percent int not null default 100, match_participant_id int default null, created_at datetime not null default current_timestamp, primary key(id), key idx_extraction_inv_user(user_id,location,sold_at), key idx_extraction_inv_item(item_def_id), key idx_extraction_inv_season(season_id), key idx_extraction_inv_participant(match_participant_id)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_shop_items (id int not null auto_increment, category varchar(20) not null, name varchar(120) not null, price int not null default 0, stock int not null default 0, shelf_until date default null, storage_rows int default null, storage_cols int default null, item_def_id int default null, active tinyint(1) not null default 1, created_at datetime not null default current_timestamp, primary key(id), key idx_extraction_shop_active(active,shelf_until)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_run_records (id int not null auto_increment, activity_id int not null, user_id int not null, created_at datetime not null default current_timestamp, primary key(id), unique key uq_extraction_run_activity_user(activity_id,user_id), key idx_extraction_run_user(user_id)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_class_rules (id int not null auto_increment, name varchar(80) not null, health int not null default 100, maintenance_fee int not null default 0, sort_order int not null default 0, active tinyint(1) not null default 1, created_at datetime not null default current_timestamp, primary key(id), unique key uk_extraction_class_name(name)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_weapon_rules (id int not null auto_increment, weapon_type varchar(20) not null default 'knife', name varchar(120) not null, item_def_id int default null, usage_fee int not null default 0, durability_cost_percent int not null default 0, active tinyint(1) not null default 1, created_at datetime not null default current_timestamp, primary key(id), key idx_extraction_weapon_type(weapon_type), key idx_extraction_weapon_item(item_def_id)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_matches (id int not null auto_increment, name varchar(160) not null, venue_name varchar(160) default null, squad_count int not null default 1, squad_limit int not null default 5, status varchar(20) not null default 'preparing', created_by_id int default null, created_at datetime not null default current_timestamp, started_at datetime default null, ended_at datetime default null, primary key(id), key idx_extraction_match_status(status,created_at)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_match_participants (id int not null auto_increment, match_id int not null, user_id int not null, squad_no int default null, class_rule_id int default null, weapon_rule_id int default null, weapon_inventory_item_id int default null, locked tinyint(1) not null default 0, paid_cash int not null default 0, evacuated tinyint(1) default null, kills int not null default 0, kill_reward_cash int not null default 0, earned_cash int not null default 0, settled_at datetime default null, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, primary key(id), unique key uq_extraction_match_user(match_id,user_id), key idx_extraction_participant_user(user_id), key idx_extraction_participant_match(match_id)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_ui_settings (id int not null auto_increment, banner_filename varchar(500) default null, updated_at datetime not null default current_timestamp on update current_timestamp, primary key(id)) engine=InnoDB default charset=utf8mb4");
        jdbc.execute("create table if not exists extraction_reset_records (id int not null auto_increment, reset_type varchar(20) not null, requested_by_id int not null, confirmed_by_id int default null, status varchar(20) not null default 'pending', created_at datetime not null default current_timestamp, confirmed_at datetime default null, primary key(id), key idx_extraction_reset_status(reset_type,status,created_at)) engine=InnoDB default charset=utf8mb4");

        addColumn("users", "extraction_authorized", "tinyint(1) NOT NULL DEFAULT 0", "attendance_manager");
        addColumn("users", "extraction_manager", "tinyint(1) NOT NULL DEFAULT 0", "extraction_authorized");
        addColumn("extraction_item_defs", "item_category", "varchar(20) NOT NULL DEFAULT '常规物品'", "level");
        addColumn("extraction_inventory_items", "quantity", "int NOT NULL DEFAULT 1", "sold_price");
        addColumn("extraction_inventory_items", "durability_percent", "int NOT NULL DEFAULT 100", "quantity");
        addColumn("extraction_inventory_items", "match_participant_id", "int DEFAULT NULL", "durability_percent");
        addColumn("extraction_seasons", "kill_reward_cash", "int NOT NULL DEFAULT 0", "active");
        addColumn("extraction_match_participants", "kill_reward_cash", "int NOT NULL DEFAULT 0", "kills");
        addColumn("extraction_shop_items", "item_def_id", "int DEFAULT NULL", "storage_cols");
        addColumn("extraction_seasons", "created_by_id", "int DEFAULT NULL", "end_date");
        addColumn("extraction_item_defs", "created_by_id", "int DEFAULT NULL", "active");
        addColumn("extraction_item_defs", "photo_filename", "varchar(500) DEFAULT NULL", "height");
        addColumn("extraction_user_profiles", "runs_count", "int NOT NULL DEFAULT 0", "storage_cols");
        addColumn("extraction_inventory_items", "sold_at", "datetime DEFAULT NULL", "location");
        addColumn("extraction_inventory_items", "sold_price", "int DEFAULT NULL", "sold_at");
        addColumn("extraction_inventory_items", "season_id", "int DEFAULT NULL", "item_def_id");
        addColumn("extraction_shop_items", "shelf_until", "date DEFAULT NULL", "stock");
        addColumn("extraction_shop_items", "active", "tinyint(1) NOT NULL DEFAULT 1", "item_def_id");
        addColumn("extraction_run_records", "created_at", "datetime NOT NULL DEFAULT current_timestamp", "user_id");
        addColumn("extraction_class_rules", "sort_order", "int NOT NULL DEFAULT 0", "maintenance_fee");
        addColumn("extraction_class_rules", "active", "tinyint(1) NOT NULL DEFAULT 1", "sort_order");
        addColumn("extraction_weapon_rules", "created_at", "datetime NOT NULL DEFAULT current_timestamp", "active");
        addColumn("extraction_matches", "created_by_id", "int DEFAULT NULL", "squad_limit");
        addColumn("extraction_matches", "started_at", "datetime DEFAULT NULL", "created_at");
        addColumn("extraction_matches", "ended_at", "datetime DEFAULT NULL", "started_at");
        addColumn("extraction_match_participants", "locked", "tinyint(1) NOT NULL DEFAULT 0", "weapon_inventory_item_id");
        addColumn("extraction_match_participants", "paid_cash", "int NOT NULL DEFAULT 0", "locked");
        addColumn("extraction_match_participants", "evacuated", "tinyint(1) DEFAULT NULL", "paid_cash");
        addColumn("extraction_match_participants", "kills", "int NOT NULL DEFAULT 0", "evacuated");
        addColumn("extraction_match_participants", "settled_at", "datetime DEFAULT NULL", "kill_reward_cash");
        addColumn("extraction_match_participants", "updated_at", "datetime NOT NULL DEFAULT current_timestamp on update current_timestamp", "created_at");
        addColumn("extraction_ui_settings", "updated_at", "datetime NOT NULL DEFAULT current_timestamp on update current_timestamp", "banner_filename");
        addColumn("extraction_reset_records", "confirmed_by_id", "int DEFAULT NULL", "requested_by_id");
        addColumn("extraction_reset_records", "status", "varchar(20) NOT NULL DEFAULT 'pending'", "confirmed_by_id");
        addColumn("extraction_reset_records", "confirmed_at", "datetime DEFAULT NULL", "status");
    }

    private void addColumn(String table, String column, String definition, String after) {
        Integer exists = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?", Integer.class, table, column);
        if (exists != null && exists == 0) {
            Integer afterExists = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?", Integer.class, table, after);
            String sql = "alter table " + table + " add column " + column + " " + definition;
            if (afterExists != null && afterExists > 0) sql += " after " + after;
            jdbc.execute(sql);
        }
    }
}
