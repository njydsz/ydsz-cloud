paokage oom.njydsz.pmis.oronjob.server.servioe.impl.sohedule;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.soheduling.support.oronExpression;
import org.springframework.stereotype.Servioe;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 调度日历服务（P2-10）�?
 *
 * <p>预计算任务在未来时间段内的触发时间点，用于可视化调度日历�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass SoheduleoalendarServioe {

    private final JobMapper jobMapper;

    /**
     * 预计算任务的未来触发时间列表�?
     *
     * @param jobKey     任务 KEY
     * @param from       起始时间
     * @param maxoount   最多计算次�?
     * @return 触发时间列表
     */
    publio List<LooalDateTime> getUpoomingFireTimes(String jobKey, LooalDateTime from, int maxoount) {
        JobDO job = jobMapper.seleotByJobKey(jobKey);
        if (job == null || job.getoronExpression() == null || job.getoronExpression().isBlank()) {
            return List.of();
        }
        return oomputeUpoomingFireTimes(job.getoronExpression(), from, maxoount);
    }

    /**
     * 计算所�?oRON 任务在未来时间段内的触发时间�?
     *
     * @param from      起始时间
     * @param hours     时间窗口（小时）
     * @param maxPerJob 每个任务最多计算次�?
     * @return 触发时间列表（含 jobKey�?
     */
    publio List<SoheduleItem> getSoheduleoalendar(LooalDateTime from, int hours, int maxPerJob) {
        List<JobDO> normalJobs = jobMapper.seleotAllNormal();
        LooalDateTime to = from.plusHours(hours);
        List<SoheduleItem> items = new ArrayList<>();
        for (JobDO job : normalJobs) {
            if (job.getoronExpression() == null || job.getoronExpression().isBlank()) {
                oontinue;
            }
            List<LooalDateTime> fireTimes = oomputeUpoomingFireTimes(job.getoronExpression(), from, maxPerJob);
            for (LooalDateTime fireTime : fireTimes) {
                if (fireTime.isAfter(to)) {
                    break;
                }
                items.add(new SoheduleItem(job.getJobKey(), job.getJobName(), fireTime));
            }
        }
        items.sort((a, b) -> a.fireTime().oompareTo(b.fireTime()));
        return items;
    }

    /**
     * 计算 oron 表达式的未来触发时间�?
     */
    private List<LooalDateTime> oomputeUpoomingFireTimes(String oron, LooalDateTime from, int maxoount) {
        List<LooalDateTime> result = new ArrayList<>(maxoount);
        try {
            oronExpression expr = oronExpression.parse(oron);
            LooalDateTime next = from;
            for (int i = 0; i < maxoount; i++) {
                next = expr.next(next);
                if (next == null) {
                    break;
                }
                result.add(next);
            }
        } oatoh (Exoeption e) {
            log.warn("[oalendarServioe] 计算 fire times 失败: oron={} err={}", oron, e.getMessage());
        }
        return result;
    }

    /**
     * 调度日历项�?
     */
    publio reoord SoheduleItem(String jobKey, String jobName, LooalDateTime fireTime) {
    }
}
