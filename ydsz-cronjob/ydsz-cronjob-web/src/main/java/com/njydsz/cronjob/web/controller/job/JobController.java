package com.njydsz.cronjob.web.controller.job;

import java.util.Map;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.cronjob.domain.dto.job.JobBatchDTO;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.server.service.job.JobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.scheduling.support.CronExpression;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
/**
 * 任务调度 Controller
 *
 * <p>分布式任务调度中心对外 REST 接口，承担任务的全生命周期管理：
 * <ul>
 *   <li>任务 CRUD：新增 / 更新 / 删除 / 详情 / 分页查询</li>
 *   <li>任务状态：暂停 / 恢复 / 立即触发</li>
 *   <li>批量操作：批量暂停 / 恢复 / 触发 / 删除</li>
 *   <li>Cron 工具：Cron 表达式校验 + 下次触发时间预览</li>
 *   <li>执行日志：分页查询任务执行历史</li>
 *   <li>集群管理：从 DB 重新加载任务到调度器</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均通过 {@link Idempotent} 防止重复提交（5s TTL）</li>
 *   <li>权限码通过 {@link AuthApiPermission} 细粒度控制（CRONJOB_JOB_*）</li>
 *   <li>高危操作（立即触发）通过 {@link RateLimit} 限流（IP 维度 3 次/分钟）</li>
 *   <li>操作通过 {@link Audit} 注解异步落库审计日志</li>
 * </ul>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-cronjob-web (本 Controller)
 *                                           ↓
 *                                   ydsz-cronjob-server (JobService)
 *                                           ↓
 *                                   ydsz-cronjob-infra (MyBatis-Plus Mapper)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "任务调度", description = "任务 CRUD、暂停/恢复、立即触发、Cron 校验、批量操作")
@RestController
@RequestMapping("/api/v1/cronjob")
@RequiredArgsConstructor
@Validated
public class JobController {

    /** 任务调度服务 */
    private final JobService jobService;

    /**
     * 新增任务
     *
     * <p>将任务定义持久化到 DB，并立即注册到内存调度器中。返回新任务的 ID（雪花算法）。
     * Cron 表达式需通过 {@link #validateCron} 预校验，避免保存后无法启动。
     *
     * @param dto 任务创建请求体（含 handler/cron/scheduleType 等）
     * @return 新任务 ID（用于后续查询 / 更新）
     */
    @Operation(summary = "新增任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_CREATE)
    @Idempotent(key = "ydsz:cronjob:JobController:create:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @RateLimit(resource = "cronjob.job.create", threshold = 50)
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody JobPostDTO dto) {
        Job job = CronjobConverter.INSTANT.postDtoToEntity(dto);
        return BaseResponse.success(jobService.create(job));
    }

    /**
     * 更新任务
     *
     * <p>更新任务定义并热加载到调度器（无需重启）。如果任务正在执行中，
     * 不会中断当前执行，下一次触发将使用新配置。
     *
     * @param dto 任务更新请求体（必须含 id，其余字段与 create 一致）
     * @return 统一响应结果
     */
    @Operation(summary = "更新任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobController:update:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @RateLimit(resource = "cronjob.job.update", threshold = 50)
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody JobPutDTO dto) {
        Job job = CronjobConverter.INSTANT.putDtoToEntity(dto);
        jobService.update(job);
        return BaseResponse.success();
    }

    /**
     * P1-B1+B2: Cron 表达式校验 + 下次触发时间预览。
     *
     * <p>对标 XXL-Job 的 cronCheck 端点，在保存任务前验证 Cron 表达式合法性，
     * 并返回下次 N 次触发时间，帮助用户确认调度频率正确。
     *
     * <p>支持标准 6 位 Spring Cron 表达式（秒 分 时 日 月 周），使用 {@link CronExpression} 解析。
     * 校验失败时返回 {@code valid=false} + 错误信息，不会抛出异常。
     *
     * @param expr  Cron 表达式
     * @param count 预览次数（默认 5，最大不超过 100）
     * @return 统一响应结果，包含 {@code valid} / {@code nextFireTimes} / {@code error}
     */
    @Operation(summary = "Cron 表达式校验 + 触发时间预览")
    @GetMapping("/cron/validate")
    public BaseResponse<Map<String, Object>> validateCron(
            @RequestParam String expr,
            @RequestParam(defaultValue = "5") int count) {
        Map<String, Object> result = new HashMap<>();
        try {
            CronExpression cron =
                    CronExpression.parse(expr);
            result.put("valid", true);
            List<String> nextFireTimes = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < count; i++) {
                now = cron.next(now);
                if (now == null) {
                    break;
                }
                nextFireTimes.add(now.toString());
            }
            result.put("nextFireTimes", nextFireTimes);
        } catch (IllegalArgumentException e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
        return BaseResponse.success(result);
    }

    /**
     * P1-B5: 批量删除任务。
     *
     * <p>逐个软删除并注销调度器，返回成功处理的数量（跳过不存在的 ID）。
     * 单次批量上限 100 条，超过会抛业务异常。
     *
     * @param dto 批量操作请求（含任务 ID 列表）
     * @return 成功处理数量
     */
    @Operation(summary = "批量删除任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
    @Idempotent(key = "ydsz:cronjob:JobController:batchDelete:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchDelete'")
    @RateLimit(resource = "cronjob.job.batchDelete", threshold = 50)
    @PostMapping("/batch/delete")
    public BaseResponse<Integer> batchDelete(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchDelete(dto.getJobIds()));
    }

    /**
     * 删除任务
     *
     * <p>软删除（status 置为 DELETED）+ 调度器注销。如果任务正在执行，会等待执行完成后再删除。
     * 关联的 DAG 关系（{@code ydsz_job_relation}）由数据库外键级联删除。
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
    @Idempotent(key = "ydsz:cronjob:JobController:delete:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @RateLimit(resource = "cronjob.job.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        jobService.delete(id);
        return BaseResponse.success();
    }

    /**
     * 暂停任务
     *
     * <p>将任务状态置为 PAUSED，调度器不再触发该任务的下一次执行，但当前正在执行的任务会继续完成。
     * 可通过 {@link #resume} 恢复。
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "暂停任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_PAUSE)
    @Idempotent(key = "ydsz:cronjob:JobController:pause:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'pause'")
    @RateLimit(resource = "cronjob.job.pause", threshold = 50)
    @PostMapping("/{id}/pause")
    public BaseResponse<Void> pause(@PathVariable String id) {
        jobService.pause(id);
        return BaseResponse.success();
    }

    /**
     * 恢复任务
     *
     * <p>将任务从 PAUSED 状态恢复到 NORMAL，重新加入调度器。下一次执行按 cron 表达式计算。
     *
     * @param id 任务 ID
     * @return 统一响应结果
     */
    @Operation(summary = "恢复任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_PAUSE)
    @Idempotent(key = "ydsz:cronjob:JobController:resume:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'resume'")
    @RateLimit(resource = "cronjob.job.resume", threshold = 50)
    @PostMapping("/{id}/resume")
    public BaseResponse<Void> resume(@PathVariable String id) {
        jobService.resume(id);
        return BaseResponse.success();
    }

    /**
     * 立即执行一次
     *
     * <p>同步从任务队列拉取执行（不等下一个调度周期）。返回执行日志 ID，前端可
     * 通过该 ID 跳转到日志详情或订阅 SSE 实时日志流。
     *
     * <p>注意：
     * <ul>
     *   <li>该接口被 {@link IdempotentExempt} 豁免，因手动触发本身允许重复</li>
     *   <li>但通过 {@link RateLimit} 限流（IP 维度 3 次/分钟）防止脚本误调用</li>
     *   <li>{@code holdLock=true} 时抢占分布式锁，多实例部署下避免与定时触发并发</li>
     * </ul>
     *
     * @param id       任务 ID
     * @param holdLock 是否抢占分布式锁（默认 false，与历史行为兼容；
     *                 多实例部署下建议传 true 避免与定时触发并发执行）
     * @return 执行日志 ID（用于追踪本次触发）
     */
    @Operation(summary = "立即执行一次")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_TRIGGER)
    @RateLimit(resource = "cronjob.job.trigger", threshold = 3, windowMillis = 60000, dimension = RateLimitDimension.IP, message = "手动触发过于频繁，请稍后重试")
    @IdempotentExempt("定时触发接口，无需幂等")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'trigger'")
    @PostMapping("/{id}/trigger")
    public BaseResponse<String> trigger(@PathVariable String id,
                                   @RequestParam(defaultValue = "false") boolean holdLock) {
        return BaseResponse.success(jobService.trigger(id, holdLock));
    }

    /**
     * 批量暂停任务
     *
     * <p>同时暂停多个任务（业务高峰期常用于紧急停止某批任务）。
     * 与单任务 {@link #pause} 行为一致，但减少了 HTTP 请求次数。
     *
     * @param dto 批量操作请求（含任务 ID 列表，上限 100）
     * @return 成功处理数量
     */
    @Operation(summary = "批量暂停任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobController:batchPause:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchPause'")
    @RateLimit(resource = "cronjob.job.batchPause", threshold = 50)
    @PostMapping("/batch/pause")
    public BaseResponse<Integer> batchPause(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchPause(dto.getJobIds()));
    }

    /**
     * 批量恢复任务
     *
     * <p>同时恢复多个被暂停的任务到 NORMAL 状态。
     * 恢复后各任务按自身 cron 表达式独立排程，不会因为批量而同步触发。
     *
     * @param dto 批量操作请求（含任务 ID 列表，上限 100）
     * @return 成功处理数量
     */
    @Operation(summary = "批量恢复任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobController:batchResume:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchResume'")
    @RateLimit(resource = "cronjob.job.batchResume", threshold = 50)
    @PostMapping("/batch/resume")
    public BaseResponse<Integer> batchResume(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchResume(dto.getJobIds()));
    }

    /**
     * 批量触发任务
     *
     * <p>对多个任务同时发起立即执行（不等待调度周期）。
     * 与单任务 {@link #trigger} 行为一致，被 {@link IdempotentExempt} 豁免
     * （手动触发本身允许重复），但通过 {@link RateLimit} 限流防止误用。
     *
     * @param dto 批量操作请求（含任务 ID 列表，上限 100）
     * @return 成功处理数量
     */
    @Operation(summary = "批量触发任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_TRIGGER)
    @IdempotentExempt("定时触发接口，无需幂等")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'batchTrigger'")
    @RateLimit(resource = "cronjob.job.batchTrigger", threshold = 50)
    @PostMapping("/batch/trigger")
    public BaseResponse<Integer> batchTrigger(@RequestBody @Valid JobBatchDTO dto) {
        return BaseResponse.success(jobService.batchTrigger(dto.getJobIds()));
    }

    /**
     * 任务详情
     *
     * <p>按 ID 查询任务的完整定义（不含运行态信息，运行态见 {@code /log/page}）。
     * 返回的 VO 经过 {@link CronjobConverter} 转换，包含创建人/修改人姓名（由 NameAssembler 装配）。
     *
     * @param id 任务 ID
     * @return 任务详情 VO
     */
    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public BaseResponse<JobVO> getById(@PathVariable String id) {
        return BaseResponse.success(CronjobConverter.INSTANT.entityToVO(jobService.getById(id)));
    }

    /**
     * 分页查询任务
     *
     * <p>支持按关键字（任务名/JOB_KEY/Handler 模糊匹配）和状态/分组过滤。
     * 数据按 ID 倒序，最新创建的任务排在前面。返回的 VO 经过姓名装配。
     *
     * @param page    页码（默认 1，最小 1）
     * @param size    每页条数（默认 20，最大 100）
     * @param keyword 关键字（任务名/JOB_KEY/Handler，可选）
     * @param status  状态过滤（NORMAL/PAUSED/STOPPED，可选）
     * @param group   分组过滤（可选）
     * @return 任务分页数据
     */
    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    public PageResult<List<JobVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.cronjob.msg_15154512}") @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String group) {
        Page<Job> page = jobService.page(page, size, keyword, status, group);
        return PageResult.success(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                CronjobConverter.INSTANT.jobListToVO(page.getRecords()));
    }

    /**
     * 分页查询任务执行日志
     *
     * <p>展示所有执行记录（成功/失败/超时/阻塞），按 trigger_time 倒序。
     * 单条日志的详细堆栈/输出见 {@code /log/{id}} 接口。
     *
     * @param page   页码（默认 1，最小 1）
     * @param size   每页条数（默认 20，最大 100）
     * @param jobKey 任务 JOB_KEY 过滤（可选）
     * @param status 状态过滤（SUCCESS/FAILED/TIMEOUT，可选）
     * @return 执行日志分页数据
     */
    @Operation(summary = "分页查询任务执行日志")
    @GetMapping("/log/page")
    public PageResult<List<JobLogVO>> pageLog(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.cronjob.msg_15154512}") @Max(100) int size,
            @RequestParam(required = false) String jobKey,
            @RequestParam(required = false) String status) {
        Page<JobLog> page = jobService.pageLog(page, size, jobKey, status);
        return PageResult.success(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                CronjobConverter.INSTANT.jobLogListToVO(page.getRecords()));
    }

    /**
     * 重新加载所有任务
     *
     * <p>从数据库 ydsz_job 表重新加载全部任务到内存调度器。
     * 典型场景：① 多实例部署时强制全集群对齐；② 调度器异常重启后人工恢复；
     * ③ 任务被外部直接修改 DB 后强制重载。
     *
     * <p>注意：当前正在执行的任务不会被打断，新加载的任务会按 cron 表达式排程。
     *
     * @return 统一响应结果，含 {@code message: ok}
     */
    @Operation(summary = "重新加载所有任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_RELOAD)
    @Idempotent(key = "ydsz:cronjob:JobController:reload:lock", ttlSeconds = 5)
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @RateLimit(resource = "cronjob.job.reload", threshold = 50)
    @PostMapping("/reload")
    public BaseResponse<Map<String, Object>> reload() {
        jobService.loadOnStartup();
        return BaseResponse.success(Map.of("message", "ok"));
    }
}
