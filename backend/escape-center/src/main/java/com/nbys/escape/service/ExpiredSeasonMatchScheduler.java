package com.nbys.escape.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredSeasonMatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpiredSeasonMatchScheduler.class);
    private final EscapeAdminService adminService;

    public ExpiredSeasonMatchScheduler(EscapeAdminService adminService) {
        this.adminService = adminService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void closeExpiredMatchesOnStartup() {
        closeExpiredMatches();
    }

    @Scheduled(cron = "5 0 0 * * *", zone = "Asia/Shanghai")
    public void closeExpiredMatchesDaily() {
        closeExpiredMatches();
    }

    private void closeExpiredMatches() {
        int closed = adminService.closeExpiredSeasonMatches();
        if (closed > 0) log.info("已自动关闭过期赛季的未结束对局，数量={}", closed);
    }
}
