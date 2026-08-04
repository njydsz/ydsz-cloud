package com.remisoft.workflow.server.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.remisoft.workflow.domain.entity.FlowRunTask;

import lombok.extern.slf4j.Slf4j;

/**
 * 待办智能排序服务。
 * <p>按 SLA/优先级/催办次数等智能排序。
 *
 * @author remi-team
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
    public List<FlowRunTask> smartSort(List<FlowRunTask> tasks) {
        if (tasks == null || tasks.size() <= 1) {
            return tasks;
        }
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream()
                .sorted(Comparator.comparingInt(
                        (FlowRunTask t) -> -calculateScore(t, now)) // 降序
                        .thenComparing(FlowRunTask::getCreatedAt)) // 同分按创建时间
                .collect(Collectors.toList());
    }

    /**
     * 计算单个任务的智能排序分
     */
    public int calculateScore(FlowRunTask task, LocalDateTime now) {
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
