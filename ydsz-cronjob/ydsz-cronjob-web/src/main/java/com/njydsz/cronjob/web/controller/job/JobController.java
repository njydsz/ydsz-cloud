package com.njydsz.cronjob.web.controller.job;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.audit.event.DataExportAuditEvent;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.util.date.DateUtils;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;
import com.njydsz.common.safe.idempotent.annotation.IdempotentExempt;
import com.njydsz.common.safe.annotation.SensitiveLevel;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.cronjob.domain.dto.BatchResultDTO;
import com.njydsz.cronjob.domain.dto.job.JobBatchDTO;
import com.njydsz.cronjob.domain.dto.job.JobBatchUpdateDTO;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
import com.njydsz.cronjob.domain.query.JobLogQuery;
import com.njydsz.cronjob.domain.query.JobQuery;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;
import com.njydsz.cronjob.server.vo.JobExportVO;
import com.njydsz.cronjob.server.vo.JobLogExportVO;

/**
 * 任务调度 Controller
 *
 * <p>分布式任务调度中心对外 REST 接口，承担任务的全生命周期管理：
 *
 * <ul>
 *   <li>任务 CRUD：新增 / 更新 / 删除 / 详情 / 分页查询
 *   <li>任务状态：暂停 / 恢复 / 立即触发
 *   <li>批量操作：批量暂停 / 恢复 / 触发 / 删除
 *   <li>Cron 工具：Cron 表达式校验 + 下次触发时间预览
 *   <li>执行日志：分页查询任务执行历史
 *   <li>集群管理：从 DB 重新加载任务到调度器
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作均通过 {@link Idempotent} 防止重复提交（5s TTL）
 *   <li>权限码通过 {@link AuthApiPermission} 细粒度控制（CRONJOB_JOB_*）
 *   <li>高危操作（立即触发）通过 {@link RateLimit} 限流（IP 维度 3 次/分钟）
 *   <li>操作通过 {@link Audit} 注解异步落库审计日志
 * </ul>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-cronjob-web (本 Controller)
 *                                           ↓
 *                                   ydsz-cronjob-server (JobService)
 *                                           ↓
 *                                   ydsz-cronjob-infra (MyBatis-Plus Mapper)
 * </pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ApiVersion("26.10.01")
@Tag(name = "任务调度", description = "任务 CRUD、暂停/恢复、立即触发、Cron 校验、批量操作")
@Slf4j
@RestController
@RequestMapping("/cronjob")
@RequiredArgsConstructor
@Validated
public class JobController {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  /** 任务调度服务 */
  private final JobService jobService;

  /** 事件发布器 */
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 新增任务
   *
   * <p>将任务定义持久化到 DB，并立即注册到内存调度器中。返回新任务的 ID（雪花算法）。 Cron 表达式需通过 {@link #validateCron} 预校验，避免保存后无法启动。
   *
   * @param dto 任务创建请求体（含 handler/cron/scheduleType 等）
   * @return 新任务 ID（用于后续查询 / 更新）
   */
  @Operation(summary = "新增任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_CREATE)
  @Idempotent(key = "ydsz:cronjob:JobController:create:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'create'")
  @RateLimit(resource = "cronjob.job.create", threshold = 50)
  @PostMapping
  public YdszResponse<String> create(@Valid @RequestBody JobPostDTO dto) {
    return YdszResponse.success(jobService.create(dto));
  }

  /**
   * 更新任务
   *
   * <p>更新任务定义并热加载到调度器（无需重启）。如果任务正在执行中， 不会中断当前执行，下一次触发将使用新配置。
   *
   * @param dto 任务更新请求体（必须含 id，其余字段与 create 一致）
   * @return 统一响应结果
   */
  @Operation(summary = "更新任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobController:update:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'update'")
  @RateLimit(resource = "cronjob.job.update", threshold = 50)
  @PutMapping
  public YdszResponse<Void> update(@Valid @RequestBody JobPutDTO dto) {
    jobService.update(dto);
    return YdszResponse.success();
  }

  /**
   * P1-B1+B2: Cron 表达式校验 + 下次触发时间预览。
   *
   * <p>在保存任务前验证 Cron 表达式合法性， 并返回下次 N 次触发时间，帮助用户确认调度频率正确。
   *
   * <p>支持标准 6 位 Spring Cron 表达式（秒 分 时 日 月 周），使用 {@link CronExpression} 解析。 校验失败时返回 {@code
   * valid=false} + 错误信息，不会抛出异常。
   *
   * @param expr Cron 表达式
   * @param count 预览次数（默认 5，最大不超过 100）
   * @return 统一响应结果，包含 {@code valid} / {@code nextFireTimes} / {@code error}
   */
  @Operation(summary = "Cron 表达式校验 + 触发时间预览")
  @GetMapping("/cron/validate")
  public YdszResponse<Map<String, Object>> validateCron(
      @RequestParam String expr, @RequestParam(defaultValue = "5") int count) {
    Map<String, Object> result = new HashMap<>(COLLECTION_CAPACITY);
    try {
      CronExpression cron = CronExpression.parse(expr);
      result.put("valid", true);
      List<String> nextFireTimes = new ArrayList<>(COLLECTION_CAPACITY);
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
      result.put("error", "Cron 表达式非法");
      log.warn("[Cron] 表达式校验失败: expr={}, err={}", expr, e.getMessage());
    }
    return YdszResponse.success(result);
  }

  /**
   * P1-B5: 批量删除任务。
   *
   * <p>逐个软删除并注销调度器，返回成功处理的数量（跳过不存在的 ID）。 单次批量上限 100 条，超过会抛业务异常。
   *
   * <p><b>需要二次身份验证：</b>批量删除定时任务属于极敏感批量操作，需管理员输入当前登录密码确认身份后方可执行。
   *
   * @param dto 批量操作请求（含任务 ID 列表）
   * @return 成功处理数量
   */
  @Operation(summary = "批量删除任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
  @Idempotent(key = "ydsz:cronjob:JobController:batchDelete:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchDelete'")
  @RateLimit(resource = "cronjob.job.batchDelete", threshold = 50)
  @PostMapping("/batch/delete")
  public YdszResponse<BatchResultDTO<String>> batchDelete(@RequestBody @Valid JobBatchDTO dto) {
    return YdszResponse.success(jobService.batchDelete(dto.getJobIds()));
  }

  /**
   * 删除任务
   *
   * <p>软删除（status 置为 DELETED）+ 调度器注销。如果任务正在执行，会等待执行完成后再删除。 关联的 DAG 关系（{@code
   * ydsz_job_relation}）由数据库外键级联删除。
   *
   * @param id 任务 ID
   * @return 统一响应结果
   */
  @Operation(summary = "删除任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_DELETE)
  @Idempotent(key = "ydsz:cronjob:JobController:delete:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'delete'")
  @RateLimit(resource = "cronjob.job.delete", threshold = 50)
  @DeleteMapping("/{id}")
  public YdszResponse<Void> delete(@PathVariable String id) {
    jobService.delete(id);
    return YdszResponse.success();
  }

  /**
   * 暂停任务
   *
   * <p>将任务状态置为 PAUSED，调度器不再触发该任务的下一次执行，但当前正在执行的任务会继续完成。 可通过 {@link #resume} 恢复。
   *
   * @param id 任务 ID
   * @return 统一响应结果
   */
  @Operation(summary = "暂停任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_PAUSE)
  @Idempotent(key = "ydsz:cronjob:JobController:pause:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'pause'")
  @RateLimit(resource = "cronjob.job.pause", threshold = 50)
  @PostMapping("/{id}/pause")
  public YdszResponse<Void> pause(@PathVariable String id) {
    jobService.pause(id);
    return YdszResponse.success();
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
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'resume'")
  @RateLimit(resource = "cronjob.job.resume", threshold = 50)
  @PostMapping("/{id}/resume")
  public YdszResponse<Void> resume(@PathVariable String id) {
    jobService.resume(id);
    return YdszResponse.success();
  }

  /**
   * 立即执行一次
   *
   * <p>同步从任务队列拉取执行（不等下一个调度周期）。返回执行日志 ID，前端可 通过该 ID 跳转到日志详情或订阅 SSE 实时日志流。
   *
   * <p>注意：
   *
   * <ul>
   *   <li>该接口被 {@link IdempotentExempt} 豁免，因手动触发本身允许重复
   *   <li>但通过 {@link RateLimit} 限流（IP 维度 3 次/分钟）防止脚本误调用
   *   <li>{@code holdLock=true} 时抢占分布式锁，多实例部署下避免与定时触发并发
   * </ul>
   *
   * @param id 任务 ID
   * @param holdLock 是否抢占分布式锁（默认 false，与历史行为兼容； 多实例部署下建议传 true 避免与定时触发并发执行）
   * @return 执行日志 ID（用于追踪本次触发）
   */
  @Operation(summary = "立即执行一次")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_TRIGGER)
  @RateLimit(
      resource = "cronjob.job.trigger",
      threshold = 3,
      windowMillis = 60000,
      dimension = RateLimitDimension.IP,
      message = "手动触发过于频繁，请稍后重试")
  @IdempotentExempt("定时触发接口，无需幂等")
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'trigger'")
  @PostMapping("/{id}/trigger")
  public YdszResponse<String> trigger(
      @PathVariable String id, @RequestParam(defaultValue = "false") boolean holdLock) {
    return YdszResponse.success(jobService.trigger(id, holdLock));
  }

  /**
   * 批量暂停任务
   *
   * <p>同时暂停多个任务（业务高峰期常用于紧急停止某批任务）。 与单任务 {@link #pause} 行为一致，但减少了 HTTP 请求次数。
   *
   * @param dto 批量操作请求（含任务 ID 列表，上限 100）
   * @return 成功处理数量
   */
  @Operation(summary = "批量暂停任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobController:batchPause:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchPause'")
  @RateLimit(resource = "cronjob.job.batchPause", threshold = 50)
  @PostMapping("/batch/pause")
  public YdszResponse<BatchResultDTO<String>> batchPause(@RequestBody @Valid JobBatchDTO dto) {
    return YdszResponse.success(jobService.batchPause(dto.getJobIds()));
  }

  /**
   * 批量恢复任务
   *
   * <p>同时恢复多个被暂停的任务到 NORMAL 状态。 恢复后各任务按自身 cron 表达式独立排程，不会因为批量而同步触发。
   *
   * @param dto 批量操作请求（含任务 ID 列表，上限 100）
   * @return 成功处理数量
   */
  @Operation(summary = "批量恢复任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobController:batchResume:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchResume'")
  @RateLimit(resource = "cronjob.job.batchResume", threshold = 50)
  @PostMapping("/batch/resume")
  public YdszResponse<BatchResultDTO<String>> batchResume(@RequestBody @Valid JobBatchDTO dto) {
    return YdszResponse.success(jobService.batchResume(dto.getJobIds()));
  }

  /**
   * 批量触发任务
   *
   * <p>对多个任务同时发起立即执行（不等待调度周期）。 与单任务 {@link #trigger} 行为一致，被 {@link IdempotentExempt} 豁免
   * （手动触发本身允许重复），但通过 {@link RateLimit} 限流防止误用。
   *
   * @param dto 批量操作请求（含任务 ID 列表，上限 100）
   * @return 成功处理数量
   */
  @Operation(summary = "批量触发任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_TRIGGER)
  @IdempotentExempt("定时触发接口，无需幂等")
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchTrigger'")
  @RateLimit(resource = "cronjob.job.batchTrigger", threshold = 50)
  @PostMapping("/batch/trigger")
  public YdszResponse<BatchResultDTO<String>> batchTrigger(@RequestBody @Valid JobBatchDTO dto) {
    return YdszResponse.success(jobService.batchTrigger(dto.getJobIds()));
  }

  /**
   * P1-13: 批量修改任务分组。
   *
   * <p>将多个任务同时移动到目标分组。分组变更不影响调度器注册，无需重新调度。
   *
   * @param dto 批量修改请求（含任务 ID 列表 + 目标分组）
   * @return 批量操作结果
   */
  @Operation(summary = "批量修改任务分组")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobController:batchUpdateGroup:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'batchUpdateGroup'")
  @RateLimit(resource = "cronjob.job.batchUpdateGroup", threshold = 50)
  @PostMapping("/batch/updateGroup")
  public YdszResponse<BatchResultDTO<String>> batchUpdateGroup(@RequestBody @Valid JobBatchUpdateDTO dto) {
    return YdszResponse.success(jobService.batchUpdateGroup(dto.getJobIds(), dto.getJobGroup()));
  }

  /**
   * P1-13: 批量修改 Cron 表达式。
   *
   * <p>将多个 CRON 类型任务的 Cron 表达式同时更新。自动校验合法性并重新注册调度器。
   * 非 CRON 类型任务会被跳过（标记为失败）。
   *
   * @param dto 批量修改请求（含任务 ID 列表 + 新 Cron 表达式）
   * @return 批量操作结果
   */
  @Operation(summary = "批量修改 Cron 表达式")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Idempotent(key = "ydsz:cronjob:JobController:batchUpdateCron:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'batchUpdateCron'")
  @RateLimit(resource = "cronjob.job.batchUpdateCron", threshold = 50)
  @PostMapping("/batch/updateCron")
  public YdszResponse<BatchResultDTO<String>> batchUpdateCron(@RequestBody @Valid JobBatchUpdateDTO dto) {
    return YdszResponse.success(jobService.batchUpdateCron(dto.getJobIds(), dto.getCronExpression()));
  }

  /**
   * 任务详情
   *
   * <p>按 ID 查询任务的完整定义（不含运行态信息，运行态见 {@code /log/page}）。 返回的 VO 包含创建人/修改人姓名（由 NameAssembler 装配）。
   *
   * @param id 任务 ID
   * @return 任务详情 VO
   */
  @Operation(summary = "任务详情")
  @GetMapping("/{id}")
  public YdszResponse<JobVO> getById(@PathVariable String id) {
    return YdszResponse.success(jobService.getById(id));
  }

  /**
   * 分页查询任务
   *
   * <p>支持按关键字（任务名/JOB_KEY/Handler 模糊匹配）和状态/分组过滤。 数据按 ID 倒序，最新创建的任务排在前面。返回的 VO 经过姓名装配。
   *
   * @param query 分页查询参数（含 pageNum/pageSize/过滤条件）
   * @return 任务分页数据
   */
  @Operation(summary = "分页查询任务")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<JobVO>>> page(@Valid JobQuery query) {
    return YdszResponse.success(jobService.page(query));
  }

  /**
   * 分页查询任务执行日志
   *
   * <p>展示所有执行记录（成功/失败/超时/阻塞），按 trigger_time 倒序。 单条日志的详细堆栈/输出见 {@code /log/{id}} 接口。
   *
   * @param query 分页查询参数（含 pageNum/pageSize/过滤条件）
   * @return 执行日志分页数据
   */
  @Operation(summary = "分页查询任务执行日志")
  @GetMapping("/log/page")
  public YdszResponse<PageResponse<List<JobLogVO>>> pageLog(@Valid JobLogQuery query) {
    return YdszResponse.success(jobService.pageLog(query));
  }

  /**
   * 导出任务列表（Excel）
   *
   * <p>根据当前过滤（keyword/status/group）条件导出任务列表，自动处理多页分页查询并聚合全部数据后一次性写入。
   * SuperFastExcelWriter 每次 {@code doWrite} 输出完整 xlsx，禁止多次调用，因此先聚合再写入。
   * 文件名为 {@code jobs_yyyyMMddHHmmss.xlsx}。
   *
   * <p>Excel 文件名、表头、列宽通过 {@link JobExportVO} 上的 {@code @ExcelProperty} 注解定义。
   *
   * @param keyword 关键字过滤（同 {@link #page}）
   * @param status 状态过滤（同 {@link #page}）
   * @param group 分组过滤（同 {@link #page}）
   */
  @Operation(summary = "导出任务列表（Excel）")
  @GetMapping("/export")
  public void exportJobs(@RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status, @RequestParam(required = false) String group,
      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
    String fileName = "jobs_" + DateUtils.formatNow("yyyyMMddHHmmss") + ".xlsx";
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + fileName.replaceAll("[^\\x20-\\x7E]", "_") + "\"; "
            + "filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20"));

    final int pageSize = 200;
    List<JobExportVO> rows = new ArrayList<>();
    int pageNum = 1;
    while (true) {
      JobQuery query = JobQuery.builder()
          .pageNum(pageNum)
          .pageSize(pageSize)
          .keyword(keyword)
          .status(status)
          .group(group)
          .build();
      PageResponse<List<JobVO>> page = jobService.page(query);
      if (page == null || page.getData() == null || page.getData().isEmpty()) {
        break;
      }
      for (JobVO vo : page.getData()) {
        rows.add(toJobExportVO(vo));
      }
      if (pageNum * pageSize >= page.getTotal()) {
        break;
      }
      pageNum++;
    }
    try (ExcelWriter writer = ExcelFacade.write(response.getOutputStream(), JobExportVO.class)
        .sheet("Jobs")) {
      writer.doWrite(rows);
    }
    publishDataExportAudit("任务管理", "job", rows.size());
  }

  /**
   * 导出任务执行日志（Excel）
   *
   * <p>根据当前过滤（jobKey/status）条件导出执行日志，自动处理多页分页查询并聚合全部数据后一次性写入。
   * SuperFastExcelWriter 每次 {@code doWrite} 输出完整 xlsx，禁止多次调用，因此先聚合再写入。
   * 文件名为 {@code job_logs_yyyyMMddHHmmss.xlsx}。
   *
   * @param jobKey 任务 JOB_KEY 过滤（同 {@link #pageLog}）
   * @param status 状态过滤（同 {@link #pageLog}）
   */
  @Operation(summary = "导出执行日志（Excel）")
  @GetMapping("/log/export")
  public void exportJobLogs(@RequestParam(required = false) String jobKey,
      @RequestParam(required = false) String status,
      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
    String fileName = "job_logs_" + DateUtils.formatNow("yyyyMMddHHmmss") + ".xlsx";
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + fileName.replaceAll("[^\\x20-\\x7E]", "_") + "\"; "
            + "filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20"));

    final int pageSize = 200;
    List<JobLogExportVO> rows = new ArrayList<>();
    int pageNum = 1;
    while (true) {
      JobLogQuery query = JobLogQuery.builder()
          .pageNum(pageNum)
          .pageSize(pageSize)
          .jobKey(jobKey)
          .status(status)
          .build();
      PageResponse<List<JobLogVO>> page = jobService.pageLog(query);
      if (page == null || page.getData() == null || page.getData().isEmpty()) {
        break;
      }
      for (JobLogVO vo : page.getData()) {
        rows.add(toJobLogExportVO(vo));
      }
      if (pageNum * pageSize >= page.getTotal()) {
        break;
      }
      pageNum++;
    }
    try (ExcelWriter writer = ExcelFacade.write(response.getOutputStream(), JobLogExportVO.class)
        .sheet("JobLogs")) {
      writer.doWrite(rows);
    }
    publishDataExportAudit("任务管理", "job_log", rows.size());
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 发布数据导出审计事件。
   *
   * <p>事件发布不阻塞业务链路，异常仅记录日志。
   *
   * @param exportModule 导出模块名
   * @param bizType 业务类型
   * @param rowCount 导出行数
   */
  private void publishDataExportAudit(String exportModule, String bizType, int rowCount) {
    try {
      DataExportAuditEvent event = DataExportAuditEvent.builder()
          .userId(AuthContextUtils.getUserId())
          .username(AuthContextUtils.getUsername())
          .exportModule(exportModule)
          .bizType(bizType)
          .rowCount(rowCount)
          .traceId(TracerUtils.getTraceId())
          .clientIp(RequestContext.getClientIp())
          .tenantId(AuthContextUtils.getTenantIdOrDefault())
          .exportedAt(System.currentTimeMillis())
          .build();
      eventPublisher.publishEvent(event);
      log.debug("[Audit] 数据导出事件已发布: module={}, bizType={}, rowCount={}",
          exportModule, bizType, rowCount);
    } catch (Exception e) {
      log.warn("[Audit] 发布数据导出事件异常: exportModule={}, reason={}", exportModule, e.getMessage());
    }
  }

  // ==================== 私有转换方法（domain VO → excel VO） ====================

  /**
   * 将 {@link JobVO} 转换为 Excel 导出行。
   *
   * @param vo 任务列表领域 VO
   * @return Excel 导出行
   */
  private JobExportVO toJobExportVO(JobVO vo) {
    JobExportVO export = new JobExportVO();
    export.setId(vo.getId());
    export.setJobName(vo.getJobName());
    export.setJobGroup(vo.getJobGroup());
    export.setStatus(vo.getStatus());
    export.setJobKey(vo.getJobKey());
    export.setHandler(vo.getHandler());
    export.setCronExpression(vo.getCronExpression());
    export.setScheduleType(vo.getScheduleType());
    export.setNextFireTime(vo.getNextFireTime() != null ? vo.getNextFireTime().toString() : null);
    export.setLastFireTime(vo.getLastFireTime() != null ? vo.getLastFireTime().toString() : null);
    export.setFireCount(vo.getFireCount());
    export.setSuccessCount(vo.getSuccessCount());
    export.setFailCount(vo.getFailCount());
    export.setCreatedBy(vo.getCreatedBy());
    return export;
  }

  /**
   * 将 {@link JobLogVO} 转换为 Excel 导出行。
   *
   * @param vo 任务执行日志领域 VO
   * @return Excel 导出行
   */
  private JobLogExportVO toJobLogExportVO(JobLogVO vo) {
    JobLogExportVO export = new JobLogExportVO();
    export.setId(vo.getId());
    export.setJobKey(vo.getJobKey());
    export.setStartTime(vo.getStartTime() != null ? vo.getStartTime().toString() : null);
    export.setEndTime(vo.getEndTime() != null ? vo.getEndTime().toString() : null);
    export.setDurationMs(vo.getDurationMs());
    export.setStatus(vo.getStatus());
    export.setTriggerType(vo.getTriggerType());
    export.setExecNodeId(vo.getExecNodeId());
    export.setShardIndex(vo.getShardIndex());
    export.setSlow(vo.getSlow() != null && vo.getSlow() == 1);
    export.setCreatedAt(vo.getCreatedAt() != null ? vo.getCreatedAt().toString() : null);
    if (vo.getErrorMessage() != null && vo.getErrorMessage().length() > 200) {
      export.setErrorMessage(vo.getErrorMessage().substring(0, 200) + "...");
    } else {
      export.setErrorMessage(vo.getErrorMessage());
    }
    return export;
  }

  /**
   * 重新加载全部任务定义
   *
   * <p>从数据库 ydsz_job 表重新加载全部任务到内存调度器。 典型场景：① 多实例部署时强制全集群对齐；② 调度器异常重启后人工恢复； ③ 任务被外部直接修改 DB 后强制重载。
   *
   * <p>注意：当前正在执行的任务不会被打断，新加载的任务会按 cron 表达式排程。
   *
   * @return 统一响应结果，含 {@code message: ok}
   */
  @Operation(summary = "重新加载所有任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_RELOAD)
  @Idempotent(key = "ydsz:cronjob:JobController:reload:lock", ttlSeconds = 5)
  @Audit(
      module = "任务管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'postmapping'")
  @RateLimit(resource = "cronjob.job.reload", threshold = 50)
  @PostMapping("/reload")
  public YdszResponse<Map<String, Object>> reload() {
    jobService.loadOnStartup();
    return YdszResponse.success(Map.of("message", "ok"));
  }
}
