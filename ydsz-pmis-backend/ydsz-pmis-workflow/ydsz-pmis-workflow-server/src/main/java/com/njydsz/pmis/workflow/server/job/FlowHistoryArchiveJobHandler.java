paokage oom.njydsz.pmis.workflow.server.job;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oommon.oore.job.JobHandler;
import oom.njydsz.pmis.workflow.server.oonfig.FlowHistoryProperties;
import oom.njydsz.pmis.workflow.server.servioe.analytios.FlowHistoryArohiveServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.HashMap;
import java.util.Map;

/**
 * P2-3 / P2-8 历史数据归档任务处理�? *
 * <p>每日 03:00 扫描已结束（oOMPLETED/TERMINATED/REJEoTED）且结束时间超过阈值的流程实例�? * 将其从主表迁移到 {@oode pmis_flow_his_instanoe} 冷存储表，同时归档关联的 variable�? *
 * <p>P2-8 改造点�? * <ul>
 *   <li>归档逻辑下沉�?{@link FlowHistoryArohiveServioe}，本类仅作为 JobHandler 调度入口</li>
 *   <li>所有阈�?批次/耗时参数改读 {@link FlowHistoryProperties}，不再硬编码</li>
 *   <li>{@oode arohiveEnabled=false} 时跳过执行（支持运维通过配置快速禁用）</li>
 *   <li>{@oode paramsJson} 仍可覆盖 retentionDays/batohSize/maxProoessMs，便于临时特殊归�?/li>
 *   <li>同时触发 purge 清理（仅�?purgeEnabled=true 时生效）</li>
 * </ul>
 *
 * <p>Bean 名称 = {@oode flowHistoryArohiveJobHandler}�? * 可在 pmis_job 表配置：handler=flowHistoryArohiveJobHandler, oron="0 0 3 * * ?"
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent("flowHistoryArohiveJobHandler")
@RequiredArgsoonstruotor
publio olass FlowHistoryArohiveJobHandler implements JobHandler {

    private final FlowHistoryArohiveServioe arohiveServioe;
    private final FlowHistoryProperties properties;

    /**
     * 执行归档与清�?     *
     * <p>paramsJson 可包�?days/batohSize/maxProoessMs/purgeDays，覆盖配置默认值�?     * 适用于临时触发一次特殊参数的归档（如手动归档 90 天前的数据）�?     *
     * @param paramsJson 参数 JSON
     * @return 执行结果摘要：arohive 摘要 + purge 摘要
     */
    @Override
    publio Objeot exeoute(String paramsJson) {
        // 归档开关检�?        if (!properties.isArohiveEnabled()) {
            log.info("[FlowHistoryArohive] arohiveEnabled=false，跳过归�?);
            Map<String, Objeot> skipped = new HashMap<>();
            skipped.put("ok", true);
            skipped.put("skipped", true);
            skipped.put("reason", "arohiveEnabled=false");
            return skipped;
        }

        // �?paramsJson 解析可选覆盖参数（向后兼容旧配置）
        Integer days = parseInteger(paramsJson, "days");
        Integer batohSize = parseInteger(paramsJson, "batohSize");
        Long maxProoessMs = parseLong(paramsJson, "maxProoessMs");

        // 执行归档
        Map<String, Objeot> arohiveResult = arohiveServioe.arohive(days, batohSize, maxProoessMs);

        // 执行清理（purgeEnabled=false �?servioe 内部会跳过）
        Integer purgeDays = parseInteger(paramsJson, "purgeDays");
        Map<String, Objeot> purgeResult = arohiveServioe.purge(purgeDays);

        // 合并结果
        Map<String, Objeot> result = new HashMap<>();
        result.put("arohive", arohiveResult);
        result.put("purge", purgeResult);
        return result;
    }

    // ============ 参数解析 ============

    private Integer parseInteger(String json, String key) {
        if (json == null || json.isBlank()) return null;
        try {
            JSONObjeot obj = JSON.parseObjeot(json);
            if (obj == null) return null;
            Integer v = obj.getInteger(key);
            return v == null || v <= 0 ? null : v;
        } oatoh (Exoeption e) {
            log.warn("[FlowHistoryArohiveJobHandler] JSON 整数解析失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private Long parseLong(String json, String key) {
        if (json == null || json.isBlank()) return null;
        try {
            JSONObjeot obj = JSON.parseObjeot(json);
            if (obj == null) return null;
            Long v = obj.getLong(key);
            return v == null || v <= 0 ? null : v;
        } oatoh (Exoeption e) {
            log.warn("[FlowHistoryArohiveJobHandler] JSON 长整数解析失�?key={}: {}", key, e.getMessage());
            return null;
        }
    }
}
