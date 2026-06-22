package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import com.nbys.activity.service.AuthService;
import com.nbys.activity.service.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminBasicController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public AdminBasicController(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @GetMapping("/venues")
    public ApiResponse<List<Map<String, Object>>> venues(@RequestParam(required = false) String name) {
        String q = name == null ? "" : name.trim();
        return ApiResponse.ok(Rows.list(jdbc, "select * from venues where (?='' or name like concat('%',?,'%')) order by id desc", q, q));
    }

    @GetMapping("/venues/options")
    public ApiResponse<List<Map<String, Object>>> venueOptions() {
        return ApiResponse.ok(Rows.list(jdbc, "select id,name,address,image_url from venues order by id desc"));
    }

    @PostMapping("/venues")
    public ApiResponse<Void> createVenue(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "venue:create");
        require(body, "name", "address");
        jdbc.update("insert into venues(name,address,image_url,created_by_id,created_at) values(?,?,?,?,now())",
                body.get("name"), body.get("address"), body.get("image_url"), auth.currentUserId(req));
        return ApiResponse.ok(null);
    }

    @PutMapping("/venues/{id}")
    public ApiResponse<Void> updateVenue(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "venue:update");
        require(body, "name", "address");
        jdbc.update("update venues set name=?, address=?, image_url=? where id=?", body.get("name"), body.get("address"), body.get("image_url"), id);
        String location = text(body.get("address"));
        if (location.isEmpty()) location = text(body.get("name"));
        jdbc.update("update activities set location=? where venue_id=?", location, id);
        jdbc.update("update activities set banner_url=? where venue_id=? and coalesce(banner_source,'venue')<>'custom'",
                body.get("image_url"), id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/venues/{id}")
    public ApiResponse<Void> deleteVenue(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "venue:delete");
        jdbc.update("delete from venues where id=?", id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/game-modes")
    public ApiResponse<List<Map<String, Object>>> gameModes(@RequestParam(required = false) String name) {
        String q = name == null ? "" : name.trim();
        return ApiResponse.ok(Rows.list(jdbc, "select * from game_modes where (?='' or name like concat('%',?,'%')) order by id desc", q, q));
    }

    @GetMapping("/game-modes/options")
    public ApiResponse<List<Map<String, Object>>> gameModeOptions() {
        return ApiResponse.ok(Rows.list(jdbc, "select id,name,suitable_people,rules from game_modes order by id desc"));
    }

    @PostMapping("/game-modes")
    public ApiResponse<Void> createGameMode(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "gameMode:create");
        require(body, "name", "rules", "suitable_people");
        jdbc.update("insert into game_modes(name,suitable_people,rules,created_by_id,created_at) values(?,?,?,?,now())",
                body.get("name"), body.get("suitable_people"), body.get("rules"), auth.currentUserId(req));
        return ApiResponse.ok(null);
    }

    @PutMapping("/game-modes/{id}")
    public ApiResponse<Void> updateGameMode(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        auth.require(req, "gameMode:update");
        require(body, "name", "rules", "suitable_people");
        jdbc.update("update game_modes set name=?, suitable_people=?, rules=? where id=?",
                body.get("name"), body.get("suitable_people"), body.get("rules"), id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/game-modes/{id}")
    public ApiResponse<Void> deleteGameMode(@PathVariable int id, HttpServletRequest req) {
        auth.require(req, "gameMode:delete");
        jdbc.update("delete from game_modes where id=?", id);
        return ApiResponse.ok(null);
    }

    private void require(Map<String, Object> body, String... keys) {
        for (String key : keys) if (body.get(key) == null || String.valueOf(body.get(key)).trim().isEmpty()) throw new IllegalArgumentException(key + "不能为空");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
