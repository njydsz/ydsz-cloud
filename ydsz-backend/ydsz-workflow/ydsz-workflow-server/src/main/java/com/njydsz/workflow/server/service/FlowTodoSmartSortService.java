package com.njydsz.workflow.server.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.njydsz.workflow.domain.entity.FlowRunTaskDO;

import lombok.extern.slf4j.Slf4j;

/**
 * P1-5: 待办智能优先级排序服务
 *
 * <p>对标钉钉/飞书"智能待办排序"能力。综合以下维度计算排序分：
 * <ul>
 *   <li>基础优先级（node.ext.priority，1-100，默认 50）</li>
 *   <li>SLA 紧急度（距 dueAt 的时间越短，加分越高；已超期直接置顶）</li>
 *   <li>等待时长（创建时间越久，适当加分）</li>
 *   <li>催办次数（被催办过的任务适当提前）</li>
 * </ul>
 *
 * <p>排序公式：
 * <pre>
 *   score = priority * 100
 *         + slaBonus（max 3000）
 *         + waitHours * 10（max 500）
 *         + reminderCount * 100（max 500）
 * </pre>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
public class FlowTodoSmartSortService {

    /** SLA 超期加分上限 */
    private static final int SLA_OVERDUE_BONUS = 3000;
    /** SLA 即将超期加分上限（24h 内） */
    private static final int SLA_URGENT_BONUS = 2000;
    /** 等待时长加分：每小时 10 分，上限 500 */
    private static final int WAIT_HOUR_SCORE = 10;
    private static final int WAIT_MAX_BONUS = 500;
    /** 催办次数加分：每次 100 分，上限 500 */
    private static final int REMINDER_SCORE = 100;
    private static final int REMINDER_MAX_BONUS = 500;

    /**
     * 对待办列表进行智能排序
     *
     * @param tasks 待办任务列表
     * @return 排序后的列表（分值高的在前）
     */
    public List<FlowRunTaskDO> smartSort(List<FlowRunTaskDO> tasks) {
        if (tasks == null || tasks.size() <= 1) {
            return tasks;
        }
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream()
                .sorted(Comparator.comparingInt(
                        (FlowRunTaskDO t) -> -calculateScore(t, now)) // 降序
                        .thenComparing(FlowRunTaskDO::getCreatedAt)) // 同分按创建时间
                .collect(Collectors.toList());
    }

    /**
     * 计算单个任务的智能排序分
     */
    public int calculateScore(FlowRunTaskDO task, LocalDateTime now) {
        int score = 0;

        // 1. 基础优先级
        int priority = task.getPriority() != null ? task.getPriority() : 50;
        score += priority * 100;

        // 2. SLA 紧急度
        if (task.getDueAt() != null) {
            if (now.isAfter(task.getDueAt())) {
                // 已超期：直接加最高分
                score += SLA_OVERDUE_BONUS;
            } else {
                long hoursLeft = Duration.between(now, task.getDueAt()).toHours();
                if (hoursLeft <= 24) {
                    // 24h 内即将超期：按剩余时间反比加分
                    int bonus = (int) ((24 - hoursLeft) * (SLA_URGENT_BONUS / 24.0));
                    score += Math.min(bonus, SLA_URGENT_BONUS);
                }
            }
        }

        // 3. 等待时长
        if (task.getCreatedAt() != null) {
            long waitHours = Duration.between(task.getCreatedAt(), now).toHours();
            int waitBonus = (int) Math.min(waitHours * WAIT_HOUR_SCORE, WAIT_MAX_BONUS);
            score += waitBonus;
        }

        // 4. 催办次数
        int reminderCount = task.getReminderCount() != null ? task.getReminderCount() : 0;
        score += Math.min(reminderCount * REMINDER_SCORE, REMINDER_MAX_BONUS);

        return score;
    }
}
