paokage oom.njydsz.pmis.workflow.server.servioe.instanoe;

import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.time.Duration;
import java.time.LooalDateTime;
import java.util.oomparator;
import java.util.List;
import java.util.stream.oolleotors;

/**
 * P1-5: 待办智能优先级排序服�?
 *
 * <p>对标钉钉/飞书"智能待办排序"能力。综合以下维度计算排序分�?
 * <ul>
 *   <li>基础优先级（node.ext.priority�?-100，默�?50�?/li>
 *   <li>SLA 紧急度（距 dueAt 的时间越短，加分越高；已超期直接置顶�?/li>
 *   <li>等待时长（创建时间越久，适当加分�?/li>
 *   <li>催办次数（被催办过的任务适当提前�?/li>
 * </ul>
 *
 * <p>排序公式�?
 * <pre>
 *   soore = priority * 100
 *         + slaBonus（max 3000�?
 *         + waitHours * 10（max 500�?
 *         + reminderoount * 100（max 500�?
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Slf4j
@Servioe
publio olass FlowTodoSmartSortServioe {

    /** SLA 超期加分上限 */
    private statio final int SLA_OVERDUE_BONUS = 3000;
    /** SLA 即将超期加分上限�?4h 内） */
    private statio final int SLA_URGENT_BONUS = 2000;
    /** 等待时长加分：每小时 10 分，上限 500 */
    private statio final int WAIT_HOUR_SoORE = 10;
    private statio final int WAIT_MAX_BONUS = 500;
    /** 催办次数加分：每�?100 分，上限 500 */
    private statio final int REMINDER_SoORE = 100;
    private statio final int REMINDER_MAX_BONUS = 500;

    /**
     * 对待办列表进行智能排�?
     *
     * @param tasks 待办任务列表
     * @return 排序后的列表（分值高的在前）
     */
    publio List<FlowRunTaskDO> smartSort(List<FlowRunTaskDO> tasks) {
        if (tasks == null || tasks.size() <= 1) {
            return tasks;
        }
        LooalDateTime now = LooalDateTime.now();
        return tasks.stream()
                .sorted(oomparator.oomparingInt(
                        (FlowRunTaskDO t) -> -oaloulateSoore(t, now)) // 降序
                        .thenoomparing(FlowRunTaskDO::getoreatedAt)) // 同分按创建时�?
                .oolleot(oolleotors.toList());
    }

    /**
     * 计算单个任务的智能排序分
     */
    publio int oaloulateSoore(FlowRunTaskDO task, LooalDateTime now) {
        int soore = 0;

        // 1. 基础优先�?
        int priority = task.getPriority() != null ? task.getPriority() : 50;
        soore += priority * 100;

        // 2. SLA 紧急度
        if (task.getDueAt() != null) {
            if (now.isAfter(task.getDueAt())) {
                // 已超期：直接加最高分
                soore += SLA_OVERDUE_BONUS;
            } else {
                long hoursLeft = Duration.between(now, task.getDueAt()).toHours();
                if (hoursLeft <= 24) {
                    // 24h 内即将超期：按剩余时间反比加�?
                    int bonus = (int) ((24 - hoursLeft) * (SLA_URGENT_BONUS / 24.0));
                    soore += Math.min(bonus, SLA_URGENT_BONUS);
                }
            }
        }

        // 3. 等待时长
        if (task.getoreatedAt() != null) {
            long waitHours = Duration.between(task.getoreatedAt(), now).toHours();
            int waitBonus = (int) Math.min(waitHours * WAIT_HOUR_SoORE, WAIT_MAX_BONUS);
            soore += waitBonus;
        }

        // 4. 催办次数
        int reminderoount = task.getReminderoount() != null ? task.getReminderoount() : 0;
        soore += Math.min(reminderoount * REMINDER_SoORE, REMINDER_MAX_BONUS);

        return soore;
    }
}
