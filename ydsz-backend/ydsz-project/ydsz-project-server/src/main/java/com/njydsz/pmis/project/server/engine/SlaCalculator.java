package com.njydsz.project.server.engine;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.njydsz.project.domain.entity.OpsTicketDO;
import com.njydsz.project.domain.enums.OpsTicketPriority;
import com.njydsz.project.domain.enums.OpsTicketStatus;

/**
 * SLA 计算器
 *
 * <p>负责根据工单优先级与创建时间推算响应 / 解决 SLA 截止时间，
 * 并在每次状态变更时刷新超时标记。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SlaCalculator {

    /** 私有构造，工具类不可实例化 */
    private SlaCalculator() {}

    /**
     * 根据优先级推算响应 / 解决截止时间（相对 createdAt 偏移）
     *
     * @param priority  工单优先级
     * @param createdAt 工单创建时间
     * @return SLA 截止时间
     */
    public static SlaDeadline calc(OpsTicketPriority priority, LocalDateTime createdAt) {
        if (priority == null || createdAt == null) {
            return new SlaDeadline(null, null);
        }
        LocalDateTime resp = createdAt.plusMinutes(priority.getResponseMinutes());
        LocalDateTime resv = createdAt.plusMinutes(priority.getResolveMinutes());
        return new SlaDeadline(resp, resv);
    }

    /**
     * 当前时间是否超过响应 SLA
     *
     * @param t   工单
     * @param now 当前时间
     * @return true 表示已超时
     */
    public static boolean isResponseBreached(OpsTicketDO t, LocalDateTime now) {
        if (t == null || t.getResponseDueAt() == null || now == null) return false;
        return now.isAfter(t.getResponseDueAt());
    }

    /**
     * 当前时间是否超过解决 SLA
     *
     * @param t   工单
     * @param now 当前时间
     * @return true 表示已超时
     */
    public static boolean isResolveBreached(OpsTicketDO t, LocalDateTime now) {
        if (t == null || t.getResolveDueAt() == null || now == null) return false;
        return now.isAfter(t.getResolveDueAt());
    }

    /**
     * 距离响应 SLA 的剩余分钟数（负值表示已超时）
     *
     * @param t   工单
     * @param now 当前时间
     * @return 剩余分钟数
     */
    public static long responseRemainMinutes(OpsTicketDO t, LocalDateTime now) {
        if (t == null || t.getResponseDueAt() == null || now == null) return 0L;
        return ChronoUnit.MINUTES.between(now, t.getResponseDueAt());
    }

    /**
     * 距离解决 SLA 的剩余分钟数
     *
     * @param t   工单
     * @param now 当前时间
     * @return 剩余分钟数
     */
    public static long resolveRemainMinutes(OpsTicketDO t, LocalDateTime now) {
        if (t == null || t.getResolveDueAt() == null || now == null) return 0L;
        return ChronoUnit.MINUTES.between(now, t.getResolveDueAt());
    }

    /**
     * 是否已派单（ASSIGNED/IN_PROGRESS/RESOLVED 算作已派）
     *
     * @param t 工单
     * @return true 表示已派单
     */
    public static boolean isAssigned(OpsTicketDO t) {
        if (t == null || t.getStatus() == null) return false;
        OpsTicketStatus s = OpsTicketStatus.fromCode(t.getStatus());
        return s == OpsTicketStatus.ASSIGNED || s == OpsTicketStatus.IN_PROGRESS
                || s == OpsTicketStatus.RESOLVED || s == OpsTicketStatus.CLOSED;
    }

    /**
     * 是否可发起满意度评价（已解决或已关闭）
     *
     * @param t 工单
     * @return true 表示可评价
     */
    public static boolean canEvaluate(OpsTicketDO t) {
        if (t == null || t.getStatus() == null) return false;
        OpsTicketStatus s = OpsTicketStatus.fromCode(t.getStatus());
        return s == OpsTicketStatus.RESOLVED || s == OpsTicketStatus.CLOSED;
    }

    /**
     * 获取SLA告警级别（基于剩余时间比例）。
     * <p>规则：
     * <ul>
     *   <li>已超时 → CRITICAL（红色）</li>
     *   <li>剩余时间 < 总时间的 20% → WARNING（黄色）</li>
     *   <li>剩余时间 < 总时间的 50% → NOTICE（蓝色）</li>
     *   <li>其余 → NORMAL（绿色）</li>
     * </ul>
     *
     * @param t          工单
     * @param now        当前时间
     * @param slaType    SLA类型（RESPONSE/RESOLVE）
     * @return 告警级别字符串
     */
    public static String getSlaAlertLevel(OpsTicketDO t, LocalDateTime now, String slaType) {
        if (t == null || now == null) return "NORMAL";
        LocalDateTime due;
        LocalDateTime created;
        if ("RESOLVE".equalsIgnoreCase(slaType)) {
            due = t.getResolveDueAt();
            created = t.getCreatedAt();
        } else {
            due = t.getResponseDueAt();
            created = t.getCreatedAt();
        }
        if (due == null || created == null) return "NORMAL";
        if (now.isAfter(due)) return "CRITICAL";
        long total = ChronoUnit.MINUTES.between(created, due);
        long remain = ChronoUnit.MINUTES.between(now, due);
        if (total <= 0) return "NORMAL";
        double ratio = (double) remain / total;
        if (ratio < 0.2) return "WARNING";
        if (ratio < 0.5) return "NOTICE";
        return "NORMAL";
    }

    /**
     * 判断工单是否需要升级处理。
     * <p>当响应SLA或解决SLA超时且工单仍未关闭时，需要升级处理。
     *
     * @param t          工单
     * @param now        当前时间
     * @return true 表示需要升级
     */
    public static boolean needsEscalation(OpsTicketDO t, LocalDateTime now) {
        if (t == null || now == null) return false;
        OpsTicketStatus s = OpsTicketStatus.fromCode(t.getStatus());
        if (s == OpsTicketStatus.CLOSED || s == OpsTicketStatus.RESOLVED) return false;
        return isResponseBreached(t, now) || isResolveBreached(t, now);
    }

    /**
     * 获取升级建议。
     * <p>根据超时类型和工单优先级生成升级建议。
     *
     * @param t          工单
     * @param now        当前时间
     * @return 升级建议字符串；无需升级返回 null
     */
    public static String getEscalationSuggestion(OpsTicketDO t, LocalDateTime now) {
        if (!needsEscalation(t, now)) return null;
        StringBuilder sb = new StringBuilder();
        if (isResponseBreached(t, now)) {
            sb.append("响应SLA已超时");
        }
        if (isResolveBreached(t, now)) {
            if (sb.length() > 0) sb.append("，");
            sb.append("解决SLA已超时");
        }
        OpsTicketPriority p = OpsTicketPriority.fromCode(t.getPriority());
        if (p == OpsTicketPriority.P1 || p == OpsTicketPriority.P2) {
            sb.append("，建议立即升级至主管处理");
        } else {
            sb.append("，建议提醒责任人加快处理");
        }
        return sb.toString();
    }

    /**
     * SLA 截止时间
     *
     * @param responseDueAt 首次响应截止
     * @param resolveDueAt  解决截止
     */
    public record SlaDeadline(LocalDateTime responseDueAt, LocalDateTime resolveDueAt) {}
}
