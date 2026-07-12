package com.njydsz.pmis.workflow.server.job;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.workflow.server.config.FlowHistoryProperties;
import com.njydsz.pmis.workflow.server.service.analytics.FlowHistoryArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * P2-3 / P2-8 历史数据归档任务处理器
 *
 * <p>每日 03:00 扫描已结束（COMPLETED/TERMINATED/REJECTED）且结束时间超过阈值的流程实例，
 * 将其从主表迁移到 {@code pmis_flow_his_instance} 冷存储表，同时归档关联的 variable。
 *
 * <p>P2-8 改造点：
 * <ul>
 *   <li>归档逻辑下沉到 {@link FlowHistoryArchiveService}，本类仅作为 JobHandler 调度入口</li>
 *   <li>所有阈值/批次/耗时参数改读 {@link FlowHistoryProperties}，不再硬编码</li>
 *   <li>{@code archiveEnabled=false} 时跳过执行（支持运维通过配置快速禁用）</li>
 *   <li>{@code paramsJson} 仍可覆盖 retentionDays/batchSize/maxProcessMs，便于临时特殊归档</li>
 *   <li>同时触发 purge 清理（仅当 purgeEnabled=true 时生效）</li>
 * </ul>
 *
 * <p>Bean 名称 = {@code flowHistoryArchiveJobHandler}，
 * 可在 pmis_job 表配置：handler=flowHistoryArchiveJobHandler, cron="0 0 3 * * ?"
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("flowHistoryArchiveJobHandler")
@RequiredArgsConstructor
public class FlowHistoryArchiveJobHandler implements JobHandler {

    private final FlowHistoryArchiveService archiveService;
    private final FlowHistoryProperties properties;

    /**
     * 执行归档与清理
     *
     * <p>paramsJson 可包含 days/batchSize/maxProcessMs/purgeDays，覆盖配置默认值。
     * 适用于临时触发一次特殊参数的归档（如手动归档 90 天前的数据）。
     *
     * @param paramsJson 参数 JSON
     * @return 执行结果摘要：archive 摘要 + purge 摘要
     */
    @Override
    public Object execute(String paramsJson) {
        // 归档开关检查
        if (!properties.isArchiveEnabled()) {
            log.info("[FlowHistoryArchive] archiveEnabled=false，跳过归档");
            Map<String, Object> skipped = new HashMap<>();
            skipped.put("ok", true);
            skipped.put("skipped", true);
            skipped.put("reason", "archiveEnabled=false");
            return skipped;
        }

        // 从 paramsJson 解析可选覆盖参数（向后兼容旧配置）
        Integer days = parseInteger(paramsJson, "days");
        Integer batchSize = parseInteger(paramsJson, "batchSize");
        Long maxProcessMs = parseLong(paramsJson, "maxProcessMs");

        // 执行归档
        Map<String, Object> archiveResult = archiveService.archive(days, batchSize, maxProcessMs);

        // 执行清理（purgeEnabled=false 时 service 内部会跳过）
        Integer purgeDays = parseInteger(paramsJson, "purgeDays");
        Map<String, Object> purgeResult = archiveService.purge(purgeDays);

        // 合并结果
        Map<String, Object> result = new HashMap<>();
        result.put("archive", archiveResult);
        result.put("purge", purgeResult);
        return result;
    }

    // ============ 参数解析 ============

    private Integer parseInteger(String json, String key) {
        if (json == null || json.isBlank()) return null;
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) return null;
            Integer v = obj.getInteger(key);
            return v == null || v <= 0 ? null : v;
        } catch (Exception e) {
            log.warn("[FlowHistoryArchiveJobHandler] JSON 整数解析失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private Long parseLong(String json, String key) {
        if (json == null || json.isBlank()) return null;
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) return null;
            Long v = obj.getLong(key);
            return v == null || v <= 0 ? null : v;
        } catch (Exception e) {
            log.warn("[FlowHistoryArchiveJobHandler] JSON 长整数解析失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }
}
