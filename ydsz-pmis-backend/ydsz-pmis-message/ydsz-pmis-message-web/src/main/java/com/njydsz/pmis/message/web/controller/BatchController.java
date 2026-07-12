package com.njydsz.pmis.message.web.controller.batch;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.message.domain.dto.batch.BatchProgressVO;
import com.njydsz.pmis.message.domain.dto.batch.BatchSendRequestDTO;
import com.njydsz.pmis.message.domain.entity.batch.MsgBatchDO;
import com.njydsz.pmis.message.server.service.batch.BatchService;
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

/**
 * 批量发送 Controller。
 *
 * <p>提供异步批量发送入口与批次进度查询。异步模式下立即返回 batchId，
 * 后台线程池逐条发送，前端轮询 {@code /progress/{batchId}} 查询进度。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Tag(name = "批量发送", description = "异步批量发送与进度查询")
@RestController
@RequestMapping("/batch")
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
    @Idempotent(key = "batch:submitBatch", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/send")
    public BaseResponse<MsgBatchDO> submitBatch(@Valid @RequestBody BatchSendRequestDTO dto) {
        if (dto == null) {
            return BaseResponse.failed(StandardResultCode.BAD_REQUEST, "批量发送参数为空");
        }
        return BaseResponse.ok(batchService.submitBatch(dto));
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
        return BaseResponse.ok(batchService.getProgress(batchId));
    }
}
