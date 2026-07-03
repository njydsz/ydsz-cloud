package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.OpsTicketDO;
import com.njydsz.pmis.project.enums.OpsTicketPriority;
import com.njydsz.pmis.project.enums.OpsTicketStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * SLA 计算器
 *
 * <p>负责根据工单优先级与创建时间推算响应 / 解决 SLA 截止时间，
 * 并在每次状态变更时刷新超时标记。
 *
 * @author ydsz-pmis-team
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
     * SLA 截止时间
     *
     * @param responseDueAt 首次响应截止
     * @param resolveDueAt  解决截止
     */
    public record SlaDeadline(LocalDateTime responseDueAt, LocalDateTime resolveDueAt) {}
}
