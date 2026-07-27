package com.njydsz.cronjob.web.controller.job;

import java.util.List;
import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.domain.entity.job.JobDO;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.service.job.JobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * P1-B4: 任务分组管理 Controller。
 *
 * <p>对标 XXL-Job 的 JobGroupController，支持按分组查询/批量暂停/批量触发/批量恢复，
 * 便于运维按业务域批量管理任务。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "任务分组管理")
@RestController
@RequestMapping("/cronjob/group")
@RequiredArgsConstructor
public class JobGroupController {

    private final JobMapper jobMapper;
    private final JobService jobService;

    /**
     * 按分组分页查询任务列表。
     *
     * @param jobGroup 任务分组
     * @param page     页码（默认 1）
     * @param size     每页条数（默认 20）
     * @return 统一响应结果，包含任务分页数据
     */
    @Operation(summary = "按分组分页查询任务")
    @GetMapping("/{jobGroup}/page")
    public BaseResponse<Page<JobDO>> pageByGroup(
            @PathVariable String jobGroup,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<JobDO> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<JobDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobDO::getJobGroup, jobGroup)
                .eq(JobDO::getDeleted, 0)
                .orderByDesc(JobDO::getCreatedAt);
        return BaseResponse.success(jobMapper.selectPage(pageObj, wrapper));
    }

    /**
     * 批量暂停指定分组的所有任务。
     *
     * @param jobGroup 任务分组
     * @return 统一响应结果，包含成功暂停的数量
     */
    @Operation(summary = "按分组批量暂停任务")
    @Audit(module = "任务分组", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'pauseByGroup'")
    @SentinelRateLimit(resource = "cronjob.jobgroup.pauseByGroup", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:JobGroupController:pauseByGroup:lock", ttlSeconds = 5)
    @PostMapping("/{jobGroup}/pause")
    public BaseResponse<Integer> pauseByGroup(@PathVariable String jobGroup) {
        LambdaQueryWrapper<JobDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobDO::getJobGroup, jobGroup)
                .eq(JobDO::getStatus, "NORMAL")
                .eq(JobDO::getDeleted, 0);
        List<JobDO> jobs = jobMapper.selectList(wrapper);
        List<String> jobIds = jobs.stream().map(JobDO::getId).toList();
        if (jobIds.isEmpty()) {
            return BaseResponse.success(0);
        }
        return BaseResponse.success(jobService.batchPause(jobIds));
    }

    /**
     * 批量恢复指定分组的所有任务。
     *
     * @param jobGroup 任务分组
     * @return 统一响应结果，包含成功恢复的数量
     */
    @Operation(summary = "按分组批量恢复任务")
    @Audit(module = "任务分组", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'resumeByGroup'")
    @SentinelRateLimit(resource = "cronjob.jobgroup.resumeByGroup", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:JobGroupController:resumeByGroup:lock", ttlSeconds = 5)
    @PostMapping("/{jobGroup}/resume")
    public BaseResponse<Integer> resumeByGroup(@PathVariable String jobGroup) {
        LambdaQueryWrapper<JobDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobDO::getJobGroup, jobGroup)
                .eq(JobDO::getStatus, "PAUSED")
                .eq(JobDO::getDeleted, 0);
        List<JobDO> jobs = jobMapper.selectList(wrapper);
        List<String> jobIds = jobs.stream().map(JobDO::getId).toList();
        if (jobIds.isEmpty()) {
            return BaseResponse.success(0);
        }
        return BaseResponse.success(jobService.batchResume(jobIds));
    }

    /**
     * 批量触发指定分组的所有任务。
     *
     * @param jobGroup 任务分组
     * @return 统一响应结果，包含成功触发的数量
     */
    @Operation(summary = "按分组批量触发任务")
    @Audit(module = "任务分组", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'triggerByGroup'")
    @SentinelRateLimit(resource = "cronjob.jobgroup.triggerByGroup", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:JobGroupController:triggerByGroup:lock", ttlSeconds = 5)
    @PostMapping("/{jobGroup}/trigger")
    public BaseResponse<Integer> triggerByGroup(@PathVariable String jobGroup) {
        LambdaQueryWrapper<JobDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobDO::getJobGroup, jobGroup)
                .eq(JobDO::getStatus, "NORMAL")
                .eq(JobDO::getDeleted, 0);
        List<JobDO> jobs = jobMapper.selectList(wrapper);
        List<String> jobIds = jobs.stream().map(JobDO::getId).toList();
        if (jobIds.isEmpty()) {
            return BaseResponse.success(0);
        }
        return BaseResponse.success(jobService.batchTrigger(jobIds));
    }

    /**
     * 查询所有任务分组及每组任务数。
     *
     * @return 统一响应结果，包含分组统计列表
     */
    @Operation(summary = "查询所有任务分组统计")
    @GetMapping("/stats")
    public BaseResponse<List<Map<String, Object>>> groupStats() {
        LambdaQueryWrapper<JobDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobDO::getDeleted, 0).select(JobDO::getJobGroup);
        List<JobDO> all = jobMapper.selectList(wrapper);
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (JobDO job : all) {
            String group = job.getJobGroup() != null ? job.getJobGroup() : "default";
            counts.merge(group, 1, Integer::sum);
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        counts.forEach((group, count) -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("jobGroup", group);
            item.put("jobCount", count);
            result.add(item);
        });
        return BaseResponse.success(result);
    }
}
