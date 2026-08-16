package com.njydsz.cronjob.web.controller.job;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * 任务分组管理 Controller（P1-B4）。
 *
 * <p>对标 XXL-Job / PowerJob 的 JobGroupController，提供按业务域分组的任务批量管理能力。
 * 任务分组（{@code ydsz_job.job_group}）是按业务域/子系统/环境等维度对任务进行归类的逻辑分组，
 * 用于按组统一查询、批量暂停、批量恢复、批量触发，便于运维按业务域批量管理任务。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #pageByGroup} - 按分组分页查询任务列表（按 created_at 倒序）</li>
 *   <li>{@link #pauseByGroup} - 批量暂停指定分组的 NORMAL 状态任务</li>
 *   <li>{@link #resumeByGroup} - 批量恢复指定分组的 PAUSED 状态任务</li>
 *   <li>{@link #triggerByGroup} - 批量立即触发指定分组的 NORMAL 状态任务</li>
 *   <li>{@link #groupStats} - 统计所有分组及每组任务数（用于前端下拉选择器）</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>大促前批量暂停某个非关键业务域的所有任务</li>
 *   <li>故障恢复后批量恢复某个业务域的暂停任务</li>
 *   <li>跨任务批量补数：批量触发某个业务域的所有数据同步任务</li>
 *   <li>分组管理面板：按业务域查看任务分布</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有批量操作均加 {@link Idempotent} 防重（5s TTL），防止双击/重试导致重复操作</li>
 *   <li>所有写操作均加 {@link RateLimit} 限流（50 QPS / IP），防止大规模误操作</li>
 *   <li>所有变更均加 {@link Audit} 异步落库审计日志（按业务域定位操作人）</li>
 *   <li>查询接口加 {@link AuthApiPermission} 权限控制（CRONJOB_JOB_VIEW）</li>
 * </ul>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端分组管理面板
 *     → ydsz-gateway
 *       → ydsz-cronjob-web（本 Controller）
 *         → ydsz-cronjob-server.JobService
 *           → ydsz-cronjob-infra.JobMapper
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "任务分组管理", description = "按业务域分组的任务批量管理：分页、暂停/恢复/触发、统计")
@RestController
@RequestMapping("/api/v1/cronjob/group")
@RequiredArgsConstructor
public class JobGroupController {

    /** 任务 Mapper（直接查询分组维度的任务列表） */
    private final JobMapper jobMapper;
    /** 任务 Service（封装 batchPause/batchResume/batchTrigger 等批量操作） */
    private final JobService jobService;

    /**
     * 按任务分组分页查询任务列表。
     *
     * <p>仅查询 {@code deleted=0} 的任务，按 {@code created_at} 倒序排列。
     * jobGroup 取自 {@code ydsz_job.job_group} 字段，是任务定义时指定的业务域标识
     * （如 {@code ORDER-CENTER}、{@code FINANCE-DAILY} 等）。
     *
     * @param jobGroup 任务分组（{@code ydsz_job.job_group} 精确匹配，必填）
     * @param page     页码（默认 1，从 1 开始）
     * @param size     每页条数（默认 20）
     * @return 统一响应结果，包含任务分页数据（JobVO 含基础任务字段）
     */
    @Operation(summary = "按分组分页查询任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
    @GetMapping("/{jobGroup}/page")
    public PageResponse<List<JobVO>> pageByGroup(
            @PathVariable String jobGroup,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 1. 构建分页对象（MyBatis-Plus Page）
        Page<Job> pageObj = new Page<>(page, size);
        // 2. 构建查询条件：jobGroup 精确匹配 + 未逻辑删除 + 按 created_at 倒序
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getJobGroup, jobGroup)
                .eq(Job::getDeleted, 0)
                .orderByDesc(Job::getCreatedAt);
        // 3. 执行分页查询
        Page<Job> result = jobMapper.selectPage(pageObj, wrapper);
        // 4. 转换为 VO（Entity → VO 含审计字段脱敏等）
        return PageResponses.success(result, CronjobConverter.INSTANT::entityToVO);
    }

    /**
     * 批量暂停指定任务分组下的所有 NORMAL 状态任务。
     *
     * <p>仅作用于当前状态为 {@code NORMAL} 的任务，{@code PAUSED/ERROR/AUTO_PAUSED} 状态的任务不会被影响。
     * 实现路径：查询 → 收集 ID → 调用 {@link JobService#batchPause}（内部走 Redis 分布式锁 + 状态机校验）。
     *
     * <p>典型场景：大促前暂停非关键业务域的全部任务，释放调度器资源。
     *
     * @param jobGroup 任务分组
     * @return 统一响应结果，包含成功暂停的任务数量（int）
     */
    @Operation(summary = "按分组批量暂停任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Audit(module = "任务分组", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'pauseByGroup:' + #jobGroup")
    @RateLimit(resource = "cronjob.jobgroup.pauseByGroup", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:JobGroupController:pauseByGroup:lock", ttlSeconds = 5)
    @PostMapping("/{jobGroup}/pause")
    public BaseResponse<Integer> pauseByGroup(@PathVariable String jobGroup) {
        // 查询 NORMAL 状态且未删除的任务
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getJobGroup, jobGroup)
                .eq(Job::getStatus, "NORMAL")
                .eq(Job::getDeleted, 0);
        List<Job> jobs = jobMapper.selectList(wrapper);
        // 提取 ID 列表（去重 + 防御性 NPE）
        List<String> jobIds = jobs.stream().map(Job::getId).toList();
        if (jobIds.isEmpty()) {
            log.info("[JobGroup] pauseByGroup jobGroup={} 命中 0 个 NORMAL 任务，跳过", jobGroup);
            return BaseResponse.success(0);
        }
        log.info("[JobGroup] pauseByGroup jobGroup={} 命中 {} 个 NORMAL 任务，开始批量暂停", jobGroup, jobIds.size());
        return BaseResponse.success(jobService.batchPause(jobIds));
    }

    /**
     * 批量恢复指定任务分组下的所有 PAUSED 状态任务。
     *
     * <p>仅作用于当前状态为 {@code PAUSED} 的任务，{@code NORMAL/ERROR/AUTO_PAUSED} 状态的任务不会被影响。
     * 实现路径：查询 → 收集 ID → 调用 {@link JobService#batchResume}（内部校验任务定义、重新注册到调度器）。
     *
     * <p>典型场景：故障恢复后批量恢复某个业务域的暂停任务。
     *
     * @param jobGroup 任务分组
     * @return 统一响应结果，包含成功恢复的任务数量
     */
    @Operation(summary = "按分组批量恢复任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Audit(module = "任务分组", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'resumeByGroup:' + #jobGroup")
    @RateLimit(resource = "cronjob.jobgroup.resumeByGroup", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:JobGroupController:resumeByGroup:lock", ttlSeconds = 5)
    @PostMapping("/{jobGroup}/resume")
    public BaseResponse<Integer> resumeByGroup(@PathVariable String jobGroup) {
        // 查询 PAUSED 状态且未删除的任务
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getJobGroup, jobGroup)
                .eq(Job::getStatus, "PAUSED")
                .eq(Job::getDeleted, 0);
        List<Job> jobs = jobMapper.selectList(wrapper);
        List<String> jobIds = jobs.stream().map(Job::getId).toList();
        if (jobIds.isEmpty()) {
            log.info("[JobGroup] resumeByGroup jobGroup={} 命中 0 个 PAUSED 任务，跳过", jobGroup);
            return BaseResponse.success(0);
        }
        log.info("[JobGroup] resumeByGroup jobGroup={} 命中 {} 个 PAUSED 任务，开始批量恢复", jobGroup, jobIds.size());
        return BaseResponse.success(jobService.batchResume(jobIds));
    }

    /**
     * 批量立即触发指定任务分组下的所有 NORMAL 状态任务。
     *
     * <p>仅作用于当前状态为 {@code NORMAL} 的任务，PAUSED 状态的任务不会被触发（避免绕过暂停策略）。
     * 实现路径：查询 → 收集 ID → 调用 {@link JobService#batchTrigger}（同步派发到调度器立即执行）。
     *
     * <p>典型场景：跨任务批量补数；某个业务域的数据同步任务批量重跑。
     *
     * <p>注意：本接口会立即触发任务执行，调用方需评估对下游系统的影响。
     *
     * @param jobGroup 任务分组
     * @return 统一响应结果，包含成功触发的任务数量
     */
    @Operation(summary = "按分组批量触发任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_TRIGGER)
    @Audit(module = "任务分组", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'triggerByGroup:' + #jobGroup")
    @RateLimit(resource = "cronjob.jobgroup.triggerByGroup", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:JobGroupController:triggerByGroup:lock", ttlSeconds = 5)
    @PostMapping("/{jobGroup}/trigger")
    public BaseResponse<Integer> triggerByGroup(@PathVariable String jobGroup) {
        // 查询 NORMAL 状态且未删除的任务（仅 NORMAL 状态可被触发）
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getJobGroup, jobGroup)
                .eq(Job::getStatus, "NORMAL")
                .eq(Job::getDeleted, 0);
        List<Job> jobs = jobMapper.selectList(wrapper);
        List<String> jobIds = jobs.stream().map(Job::getId).toList();
        if (jobIds.isEmpty()) {
            log.info("[JobGroup] triggerByGroup jobGroup={} 命中 0 个 NORMAL 任务，跳过", jobGroup);
            return BaseResponse.success(0);
        }
        log.info("[JobGroup] triggerByGroup jobGroup={} 命中 {} 个 NORMAL 任务，开始批量触发", jobGroup, jobIds.size());
        return BaseResponse.success(jobService.batchTrigger(jobIds));
    }

    /**
     * 查询所有任务分组及每组任务数（用于前端分组选择器/统计面板）。
     *
     * <p>仅统计 {@code deleted=0} 的任务。jobGroup 为空的记录归入 {@code "default"} 分组。
     * 返回的分组按插入顺序（{@link LinkedHashMap}）保留，方便前端稳定展示。
     *
     * <p>典型场景：任务新建/查询表单的"业务域"下拉选择器；分组统计面板。
     *
     * @return 统一响应结果，包含分组统计列表（每项含 {@code jobGroup} / {@code jobCount} 字段）
     */
    @Operation(summary = "查询所有任务分组统计")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
    @GetMapping("/stats")
    public BaseResponse<List<Map<String, Object>>> groupStats() {
        // 1. 仅查询 job_group 字段，减少 IO（select(Job::getJobGroup) 触发 MyBatis-Plus 仅查该列）
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getDeleted, 0).select(Job::getJobGroup);
        List<Job> all = jobMapper.selectList(wrapper);
        // 2. 在内存中按 group 聚合计数（使用 LinkedHashMap 保持插入顺序）
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Job job : all) {
            // jobGroup 为空时归入 default 分组
            String group = job.getJobGroup() != null && !job.getJobGroup().isBlank() ? job.getJobGroup() : "default";
            counts.merge(group, 1, Integer::sum);
        }
        // 3. 转换为前端友好的 List<Map> 格式
        List<Map<String, Object>> result = new ArrayList<>();
        counts.forEach((group, count) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jobGroup", group);
            item.put("jobCount", count);
            result.add(item);
        });
        return BaseResponse.success(result);
    }
}
