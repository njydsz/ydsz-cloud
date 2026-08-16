package com.njydsz.nextwiki.server.task;

import com.njydsz.nextwiki.server.service.ColdDataArchivalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 冷数据归档定时任务。
 *
 * <p>每天凌晨扫描冷数据并执行归档，将长期未访问的文件迁移至低成本存储。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColdDataArchivalTask {

    private final ColdDataArchivalService coldDataArchivalService;

    /**
     * 执行冷数据归档扫描。
     *
     * <p>每天凌晨 2:00 执行，扫描超过阈值未访问的文件并归档。
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void performArchival() {
        log.info("[ColdDataArchivalTask] 开始冷数据归档扫描");
        try {
            int count = coldDataArchivalService.scanAndArchive();
            log.info("[ColdDataArchivalTask] 冷数据归档完成, archived={}", count);
        } catch (Exception e) {
            log.error("[ColdDataArchivalTask] 冷数据归档失败", e);
        }
    }
}
