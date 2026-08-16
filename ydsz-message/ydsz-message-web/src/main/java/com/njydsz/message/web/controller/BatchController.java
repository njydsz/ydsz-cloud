package com.njydsz.message.web.controller.batch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.batch.BatchProgressVO;
import com.njydsz.message.domain.dto.batch.BatchSendRequestDTO;
import com.njydsz.message.domain.vo.MsgBatchVO;
import com.njydsz.message.server.service.batch.BatchService;

/**
 * 批量发送（Batch Send）Controller。
 *
 * <p>提供<b>异步批量发送</b>的 HTTP API，是 ydsz-message 处理大量通知的核心入口。
 * 与 {@code MessageController.send}（单条同步发送）不同，批量发送使用独立线程池逐条处理，
 * 适合大批量、低延迟要求的场景（如全员通知、活动推送）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/batch/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>提交批量任务</b>：{@code POST /send} — 提交 (templateCode + receiverList) 的批量任务，立即返回 batchId</li>
 *   <li><b>查询进度</b>：{@code GET /progress/{batchId}} — 轮询批量任务进度（total / success / failed / skipped / progressPercent）</li>
 * </ul>
 *
 * <p><b>同步 vs 异步：</b>由 {@code BatchSendRequestDTO.async} 控制（默认 true 异步）：
 * <ul>
 *   <li><b>异步</b>（推荐）：立即返回 batchId，由 {@code BatchSendExecutor} 线程池逐条处理；适合大批量</li>
 *   <li><b>同步</b>：阻塞等待全部完成后返回；适合小批量（≤ 50 条）且要求严格一致性的场景</li>
 * </ul>
 *
 * <p><b>receiverList 模式：</b>所有接收人共享同一模板（同一 content / subject / vars），
 * 系统按 (userId, contact) 解析每个接收人的发送地址，逐条生成 {@code ydsz_msg_log} 后批量提交到 MQ。
 *
 * <p><b>批次状态机：</b>{@code PENDING → RUNNING → COMPLETED / PARTIAL_FAILED / FAILED}，
 * 由 {@code BatchSendExecutor} 在执行过程中更新。
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>系统维护通知：向全量用户推送维护公告</li>
 *   <li>活动推送：向某标签用户群发活动通知</li>
 *   <li>工资条通知：批量发送工资条详情（敏感数据）</li>
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有批量任务按 {@code tenantId} 隔离，跨租户批次不可见。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口（submit）启用 {@link Idempotent} 5s 防重（避免重复提交同一批）</li>
 *   <li>写接口（submit）启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>写接口（submit）启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_MESSAGE_SEND} 权限码</li>
 *   <li>大批量发送（receiverList &gt; 10000）建议走异步模式 + 进度轮询，避免 HTTP 超时</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.batch.BatchService 批量发送服务
 * @see com.njydsz.message.domain.entity.batch.MsgBatch 批次实体
 */
@Slf4j
@Tag(name = "批量发送", description = "异步批量发送与进度查询")
@RestController
@RequestMapping("/api/v1/message/batch")
@RequiredArgsConstructor
public class BatchController {

    /** 批量发送服务 */
    private final BatchService batchService;

    /**
     * 异步批量发送消息。
     *
     * <p>支持 receiverList 模式（统一模板+接收人列表）。
     * 异步模式（async=true，默认）立即返回 batchId，后台处理；
     * 同步模式（async=false）阻塞等待全部发送完成后返回。
     *
     * @param dto 批量发送请求
     * @return 批次实体（含 batchId 与初始状态）
     */
    @Operation(summary = "异步批量发送消息")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "ydsz:message:BatchController:submitBatch:lock", ttlSeconds = 5)
    @Audit(module = "批量发送", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'submitBatch'")
    @RateLimit(resource = "message.batch.submitBatch", threshold = 50)
    @PostMapping("/send")
    public BaseResponse<MsgBatchVO> submitBatch(@Valid @RequestBody BatchSendRequestDTO dto) {
        if (dto == null) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "批量发送参数为空");
        }
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(batchService.submitBatch(dto)));
    }

    /**
     * 查询批次发送进度。
     *
     * @param batchId 批次 ID
     * @return 进度 VO（含 total/success/failed/skipped/progressPercent）
     */
    @Operation(summary = "查询批次发送进度")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/progress/{batchId}")
    public BaseResponse<BatchProgressVO> getProgress(@PathVariable String batchId) {
        return BaseResponse.success(batchService.getProgress(batchId));
    }
}
