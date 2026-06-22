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
            addColumn("venues", "image_url", "varchar(500) DEFAULT NULL COMMENT '场地图片'", "address");
            addColumn("users", "avatar_url", "varchar(500) DEFAULT NULL COMMENT '用户头像'", "callsign");
            addColumn("activity_launcher_rentals", "status", "varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/cancelled'", "user_id");
            addColumn("activity_launcher_rentals", "confirmed_at", "datetime DEFAULT NULL", "rented_at");
            jdbc.execute("create table if not exists role_permissions (id int not null auto_increment, role varchar(20) not null, permission_code varchar(80) not null, created_at datetime not null default current_timestamp, primary key(id), unique key uk_role_permission(role, permission_code)) engine=InnoDB default charset=utf8mb4");
            jdbc.execute("create table if not exists user_notifications (id int not null auto_increment, user_id int not null, type varchar(40) not null, title varchar(120) not null, content varchar(500) not null, related_id int default null, read_at datetime default null, created_at datetime not null default current_timestamp, primary key(id), key idx_user_read(user_id, read_at)) engine=InnoDB default charset=utf8mb4");
        } catch (Exception e) {
            System.err.println("Database migration skipped: " + e.getMessage());
        }
    }

    private void addColumn(String table, String column, String definition, String after) {
        Integer exists = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?", Integer.class, table, column);
        if (exists != null && exists == 0) {
            jdbc.execute("alter table " + table + " add column " + column + " " + definition + " after " + after);
        }
    }
}
