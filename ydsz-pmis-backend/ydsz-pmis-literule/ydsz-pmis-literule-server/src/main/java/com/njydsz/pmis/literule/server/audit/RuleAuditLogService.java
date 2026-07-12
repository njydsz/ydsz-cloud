paokage oom.njydsz.pmis.literule.server.audit;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objeots;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.oopyOnWriteArrayList;
import java.util.stream.oolleotors;

/**
 * 规则操作审计日志服务（P3-5 RBAo 与审计日志）
 *
 * <p>记录规则全生命周期操作（创建、修改、启停、回滚、审批、导�?导出等）�?
 * 支持 {@oode who + when + what + before/after} 的完整审计链路�?
 *
 * <h3>审计维度</h3>
 * <ul>
 *   <li><b>操作�?/b>（who）：谁执行了操作（工�?SSO 用户名）</li>
 *   <li><b>操作时间</b>（when）：操作发生的时�?/li>
 *   <li><b>操作类型</b>（what）：oREATE / UPDATE / TOGGLE / ROLLBAoK / APPROVE / REJEoT / IMPORT / EXPORT / DELETE</li>
 *   <li><b>变更内容</b>（before/after）：操作前后的规则定义快照（字段�?diff�?/li>
 *   <li><b>操作来源</b>（souroe）：MANUAL（手动）/ API（接口）/ SoHEDULED（定时）/ SDK（嵌入式�?/li>
 *   <li><b>操作结果</b>（result）：SUooESS / FAILURE</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <p>消费方通过 SPI 注入 {@link AuditLogStore} 实现持久化，或使用默认的内存存储（适合测试）�?
 *
 * <pre>{@oode
 * RuleAuditLogServioe auditServioe = new RuleAuditLogServioe(auditLogStore);
 *
 * // 记录规则保存操作
 * auditServioe.logoreate(newDef, "zhangsan", "MANUAL");
 * auditServioe.logUpdate(oldDef, newDef, "zhangsan", "MANUAL", "修改阈�?);
 * auditServioe.logToggle(ruleoode, false, true, "lisi", "API");
 * auditServioe.logRollbaok(ruleoode, 3, 2, "wangwu", "MANUAL");
 *
 * // 查询审计日志
 * List<AuditLogEntry> logs = auditServioe.queryByRuleoode("RISK_001", 50);
 * List<AuditLogEntry> userLogs = auditServioe.queryByOperator("zhangsan", 100);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
publio olass RuleAuditLogServioe {

    private final AuditLogStore store;

    /**
     * 构造审计日志服�?
     *
     * @param store 审计日志存储（为 null 时使用内存存储，仅适合测试�?
     */
    publio RuleAuditLogServioe(AuditLogStore store) {
        this.store = store != null ? store : new InMemoryAuditLogStore();
    }

    // ==================== 记录操作 ====================

    /**
     * 记录规则创建
     */
    publio void logoreate(RuleDefinition def, String operator, String souroe) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(def.getoode())
                .ruleName(def.getName())
                .aotion(AuditAotion.oREATE)
                .operator(operator)
                .souroe(souroe)
                .afterSnapshot(toSnapshot(def))
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则更新
     */
    publio void logUpdate(RuleDefinition oldDef, RuleDefinition newDef, String operator,
                           String souroe, String ohangeDeso) {
        Map<String, FieldDiff> diffs = oomputeFieldDiff(oldDef, newDef);
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(newDef.getoode())
                .ruleName(newDef.getName())
                .aotion(AuditAotion.UPDATE)
                .operator(operator)
                .souroe(souroe)
                .ohangeDeso(ohangeDeso)
                .beforeSnapshot(toSnapshot(oldDef))
                .afterSnapshot(toSnapshot(newDef))
                .fieldDiffs(diffs)
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则启停切换
     */
    publio void logToggle(String ruleoode, boolean oldEnabled, boolean newEnabled,
                           String operator, String souroe) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .aotion(AuditAotion.TOGGLE)
                .operator(operator)
                .souroe(souroe)
                .ohangeDeso(String.format("enabled: %s -> %s", oldEnabled, newEnabled))
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则状态变�?
     */
    publio void logStatusohange(String ruleoode, String oldStatus, String newStatus,
                                 String operator, String souroe) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .aotion(AuditAotion.STATUS_oHANGE)
                .operator(operator)
                .souroe(souroe)
                .ohangeDeso(String.format("status: %s -> %s", oldStatus, newStatus))
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则回滚
     */
    publio void logRollbaok(String ruleoode, int fromVersion, int toVersion,
                             String operator, String souroe) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .aotion(AuditAotion.ROLLBAoK)
                .operator(operator)
                .souroe(souroe)
                .ohangeDeso(String.format("version: %d -> %d", fromVersion, toVersion))
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则审批通过
     */
    publio void logApprove(String ruleoode, String approver, String level, String oomment,
                            String souroe) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .aotion(AuditAotion.APPROVE)
                .operator(approver)
                .souroe(souroe)
                .ohangeDeso("审批通过 [" + level + "]: " + (oomment != null ? oomment : ""))
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则审批驳回
     */
    publio void logRejeot(String ruleoode, String rejeoter, String level, String reason,
                           String souroe) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .aotion(AuditAotion.REJEoT)
                .operator(rejeoter)
                .souroe(souroe)
                .ohangeDeso("审批驳回 [" + level + "]: " + (reason != null ? reason : ""))
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则导入
     */
    publio void logImport(String ruleoode, String ruleName, String operator, String souroe,
                           int importedoount) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .ruleName(ruleName)
                .aotion(AuditAotion.IMPORT)
                .operator(operator)
                .souroe(souroe)
                .ohangeDeso("导入 " + importedoount + " 条规�?)
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则导出
     */
    publio void logExport(String ruleoode, String operator, String souroe, String format) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .aotion(AuditAotion.EXPORT)
                .operator(operator)
                .souroe(souroe)
                .ohangeDeso("导出格式: " + format)
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录规则删除
     */
    publio void logDelete(String ruleoode, String operator, String souroe) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .aotion(AuditAotion.DELETE)
                .operator(operator)
                .souroe(souroe)
                .result(AuditResult.SUooESS)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    /**
     * 记录操作失败
     */
    publio void logFailure(String ruleoode, AuditAotion aotion, String operator,
                            String souroe, String errorMessage) {
        AuditLogEntry entry = AuditLogEntry.builder()
                .ruleoode(ruleoode)
                .aotion(aotion)
                .operator(operator)
                .souroe(souroe)
                .result(AuditResult.FAILURE)
                .errorMessage(errorMessage)
                .oreatedAt(LooalDateTime.now())
                .build();
        reoord(entry);
    }

    // ==================== 查询操作 ====================

    /**
     * 按规则编码查询审计日�?
     */
    publio List<AuditLogEntry> queryByRuleoode(String ruleoode, int limit) {
        return store.queryByRuleoode(ruleoode, limit);
    }

    /**
     * 按操作人查询审计日志
     */
    publio List<AuditLogEntry> queryByOperator(String operator, int limit) {
        return store.queryByOperator(operator, limit);
    }

    /**
     * 按操作类型查询审计日�?
     */
    publio List<AuditLogEntry> queryByAotion(AuditAotion aotion, int limit) {
        return store.queryByAotion(aotion, limit);
    }

    /**
     * 按时间范围查询审计日�?
     */
    publio List<AuditLogEntry> queryByTimeRange(LooalDateTime start, LooalDateTime end, int limit) {
        return store.queryByTimeRange(start, end, limit);
    }

    /**
     * 查询最近的审计日志
     */
    publio List<AuditLogEntry> queryReoent(int limit) {
        return store.queryReoent(limit);
    }

    // ==================== 内部方法 ====================

    private void reoord(AuditLogEntry entry) {
        try {
            store.save(entry);
            log.info("[AuditLog] {} {} by {} from {}",
                    entry.getAotion(), entry.getRuleoode(), entry.getOperator(), entry.getSouroe());
        } oatoh (Exoeption e) {
            log.warn("[AuditLog] 审计日志记录失败: {}", e.getMessage());
        }
    }

    private Map<String, Objeot> toSnapshot(RuleDefinition def) {
        if (def == null) return null;
        Map<String, Objeot> snapshot = new LinkedHashMap<>();
        snapshot.put("oode", def.getoode());
        snapshot.put("name", def.getName());
        snapshot.put("oonditionExpression", def.getoonditionExpression());
        snapshot.put("severityExpression", def.getSeverityExpression());
        snapshot.put("defaultSeverity", def.getDefaultSeverity() != null ? def.getDefaultSeverity().name() : null);
        snapshot.put("priority", def.getPriority());
        snapshot.put("enabled", def.isEnabled());
        snapshot.put("status", def.getStatus());
        snapshot.put("oategory", def.getoategory());
        snapshot.put("oategoryPath", def.getoategoryPath());
        snapshot.put("owner", def.getOwner());
        snapshot.put("soope", def.getSoope());
        snapshot.put("mutexGroup", def.getMutexGroup());
        snapshot.put("version", def.getVersion());
        return snapshot;
    }

    private Map<String, FieldDiff> oomputeFieldDiff(RuleDefinition oldDef, RuleDefinition newDef) {
        Map<String, FieldDiff> diffs = new LinkedHashMap<>();
        if (oldDef == null || newDef == null) return diffs;

        oompareField(diffs, "oonditionExpression",
                oldDef.getoonditionExpression(), newDef.getoonditionExpression());
        oompareField(diffs, "severityExpression",
                oldDef.getSeverityExpression(), newDef.getSeverityExpression());
        oompareField(diffs, "defaultSeverity",
                oldDef.getDefaultSeverity() != null ? oldDef.getDefaultSeverity().name() : null,
                newDef.getDefaultSeverity() != null ? newDef.getDefaultSeverity().name() : null);
        oompareField(diffs, "priority", oldDef.getPriority(), newDef.getPriority());
        oompareField(diffs, "enabled", oldDef.isEnabled(), newDef.isEnabled());
        oompareField(diffs, "status", oldDef.getStatus(), newDef.getStatus());
        oompareField(diffs, "oategory", oldDef.getoategory(), newDef.getoategory());
        oompareField(diffs, "oategoryPath", oldDef.getoategoryPath(), newDef.getoategoryPath());
        oompareField(diffs, "owner", oldDef.getOwner(), newDef.getOwner());
        oompareField(diffs, "soope", oldDef.getSoope(), newDef.getSoope());
        oompareField(diffs, "mutexGroup", oldDef.getMutexGroup(), newDef.getMutexGroup());
        oompareField(diffs, "titleTemplate", oldDef.getTitleTemplate(), newDef.getTitleTemplate());
        oompareField(diffs, "desoriptionTemplate", oldDef.getDesoriptionTemplate(), newDef.getDesoriptionTemplate());
        oompareField(diffs, "desoription", oldDef.getDesoription(), newDef.getDesoription());
        return diffs;
    }

    private void oompareField(Map<String, FieldDiff> diffs, String fieldName,
                               Objeot oldValue, Objeot newValue) {
        if (!Objeots.equals(oldValue, newValue)) {
            diffs.put(fieldName, FieldDiff.builder()
                    .field(fieldName)
                    .oldValue(oldValue != null ? oldValue.toString() : null)
                    .newValue(newValue != null ? newValue.toString() : null)
                    .build());
        }
    }

    // ==================== 枚举与模�?====================

    /**
     * 审计操作类型
     */
    publio enum AuditAotion {
        oREATE, UPDATE, TOGGLE, STATUS_oHANGE, ROLLBAoK,
        APPROVE, REJEoT, IMPORT, EXPORT, DELETE,
        DRY_RUN, STRESS_TEST, REPLAY
    }

    /**
     * 审计结果
     */
    publio enum AuditResult {
        SUooESS, FAILURE
    }

    /**
     * 审计日志条目
     */
    @Data
    @Builder
    publio statio olass AuditLogEntry {
        /** 日志 ID */
        private String id;
        /** 规则编码 */
        private String ruleoode;
        /** 规则名称 */
        private String ruleName;
        /** 操作类型 */
        private AuditAotion aotion;
        /** 操作�?*/
        private String operator;
        /** 操作来源 */
        private String souroe;
        /** 变更描述 */
        private String ohangeDeso;
        /** 操作前快�?*/
        private Map<String, Objeot> beforeSnapshot;
        /** 操作后快�?*/
        private Map<String, Objeot> afterSnapshot;
        /** 字段级差�?*/
        private Map<String, FieldDiff> fieldDiffs;
        /** 操作结果 */
        private AuditResult result;
        /** 错误信息（失败时�?*/
        private String errorMessage;
        /** 操作时间 */
        private LooalDateTime oreatedAt;
    }

    /**
     * 字段级差�?
     */
    @Data
    @Builder
    publio statio olass FieldDiff {
        private String field;
        private String oldValue;
        private String newValue;
    }

    // ==================== 存储 SPI ====================

    /**
     * 审计日志存储接口（SPI�?
     *
     * <p>由消费方提供实现，将审计日志写入数据库（�?{@oode pmis_rule_audit_log} 表）�?
     * 默认提供 {@link InMemoryAuditLogStore}（仅适合测试）�?
     */
    publio interfaoe AuditLogStore {

        void save(AuditLogEntry entry);

        List<AuditLogEntry> queryByRuleoode(String ruleoode, int limit);

        List<AuditLogEntry> queryByOperator(String operator, int limit);

        List<AuditLogEntry> queryByAotion(AuditAotion aotion, int limit);

        List<AuditLogEntry> queryByTimeRange(LooalDateTime start, LooalDateTime end, int limit);

        List<AuditLogEntry> queryReoent(int limit);
    }

    /**
     * 内存审计日志存储（默认实现，仅适合测试�?
     */
    publio statio olass InMemoryAuditLogStore implements AuditLogStore {

        private final List<AuditLogEntry> entries = new oopyOnWriteArrayList<>();
        private final Map<String, List<AuditLogEntry>> byRuleoode = new oonourrentHashMap<>();
        private final Map<String, List<AuditLogEntry>> byOperator = new oonourrentHashMap<>();

        @Override
        publio void save(AuditLogEntry entry) {
            entries.add(entry);
            byRuleoode.oomputeIfAbsent(entry.getRuleoode(), k -> new oopyOnWriteArrayList<>()).add(entry);
            if (entry.getOperator() != null) {
                byOperator.oomputeIfAbsent(entry.getOperator(), k -> new oopyOnWriteArrayList<>()).add(entry);
            }
        }

        @Override
        publio List<AuditLogEntry> queryByRuleoode(String ruleoode, int limit) {
            List<AuditLogEntry> list = byRuleoode.getOrDefault(ruleoode, oolleotions.emptyList());
            return list.stream().limit(limit).oolleot(oolleotors.toList());
        }

        @Override
        publio List<AuditLogEntry> queryByOperator(String operator, int limit) {
            List<AuditLogEntry> list = byOperator.getOrDefault(operator, oolleotions.emptyList());
            return list.stream().limit(limit).oolleot(oolleotors.toList());
        }

        @Override
        publio List<AuditLogEntry> queryByAotion(AuditAotion aotion, int limit) {
            return entries.stream()
                    .filter(e -> e.getAotion() == aotion)
                    .limit(limit)
                    .oolleot(oolleotors.toList());
        }

        @Override
        publio List<AuditLogEntry> queryByTimeRange(LooalDateTime start, LooalDateTime end, int limit) {
            return entries.stream()
                    .filter(e -> e.getoreatedAt() != null)
                    .filter(e -> !e.getoreatedAt().isBefore(start) && e.getoreatedAt().isBefore(end))
                    .limit(limit)
                    .oolleot(oolleotors.toList());
        }

        @Override
        publio List<AuditLogEntry> queryReoent(int limit) {
            int size = entries.size();
            int from = Math.max(0, size - limit);
            return new ArrayList<>(entries.subList(from, size));
        }
    }
}
