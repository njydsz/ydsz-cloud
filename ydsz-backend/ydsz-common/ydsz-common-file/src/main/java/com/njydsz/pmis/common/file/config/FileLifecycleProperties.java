package com.njydsz.common.file.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * File lifecycle configuration properties.
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.file.lifecycle")
public class FileLifecycleProperties {

    private boolean enabled = false;
    private String cron = "0 0 2 * * ?";
    private String bucket;
    private List<LifecycleRule> rules = new ArrayList<>();
    private boolean dryRun = false;

    @Data
    public static class LifecycleRule {
        private String prefix;
        private int maxAgeDays;
        private String action = "delete";
    }
}
