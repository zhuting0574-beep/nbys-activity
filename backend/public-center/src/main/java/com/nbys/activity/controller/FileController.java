package com.nbys.activity.controller;

import com.nbys.activity.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
public class FileController {
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping({"/api/admin/files/upload", "/api/h5/files/upload"})
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String suffix = original.contains(".") ? original.substring(original.lastIndexOf(".")) : "";
        String name = UUID.randomUUID().toString().replace("-", "") + suffix;
        Path dir = resolveUploadDir();
        Files.createDirectories(dir);
        File target = dir.resolve(name).toFile();
        file.transferTo(target);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("fileName", name);
        out.put("url", "/uploads/" + name);
        out.put("size", target.length());
        return ApiResponse.ok(out);
    }

    @RequestMapping(value = "/uploads/{fileName:.+}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<FileSystemResource> file(@PathVariable String fileName) throws Exception {
        Path file = resolveUploadDir().resolve(fileName).normalize();
        if (!file.startsWith(resolveUploadDir()) || !Files.exists(file)) return ResponseEntity.notFound().build();
        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(file.toFile()));
    }

    private Path resolveUploadDir() {
        Path path = Paths.get(uploadDir);
        if (!path.isAbsolute()) path = Paths.get(System.getProperty("user.dir")).resolve(path);
        return path.normalize();
    }
}
