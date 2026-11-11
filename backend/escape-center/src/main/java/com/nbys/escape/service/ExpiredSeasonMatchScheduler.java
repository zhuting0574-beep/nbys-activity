package com.nbys.escape.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExpiredSeasonMatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpiredSeasonMatchScheduler.class);
    private final EscapeAdminService adminService;

    public ExpiredSeasonMatchScheduler(EscapeAdminService adminService) {
        this.adminService = adminService;
    }

}
