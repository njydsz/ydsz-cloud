package com.njydsz.pmis.message.server.service.impl.batch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.domain.dto.batch.BatchProgressVO;
import com.njydsz.pmis.message.domain.dto.batch.BatchSendRequestDTO;
import com.njydsz.pmis.message.domain.entity.batch.MsgBatchDO;
import com.njydsz.pmis.message.infra.mapper.batch.MsgBatchMapper;
import com.njydsz.pmis.message.server.service.batch.BatchService;
import com.njydsz.pmis.message.server.service.core.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息批次服务实现。
 *
 * <p>异步批量发送流程：
 * <ol>
 *   <li>{@link #submitBatch} 创建 PENDING 批次记录，返回 batchId</li>
 *   <li>{@link #executeBatch} 异步处理：逐条调用 {@link MessageService#send}，
 *       实时更新 success/failed/skipped 计数</li>
 *   <li>处理完成后更新状态为 COMPLETED / FAILED</li>
 * </ol>
 *
 * <p>支持 receiverList 模式（统一模板+接收人列表展开）和 requests 模式（每条独立请求）。
 * 单批最大 10000 条，超出拒绝。异步处理通过 Spring {@code @Async} 线程池执行。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

    /** 单批最大条数 */
    private static final int MAX_BATCH_SIZE = 10000;

    /** 批次记录 Mapper */
    private final MsgBatchMapper msgBatchMapper;
    /** 消息发送服务（逐条发送） */
    private final MessageService messageService;

    @Override
    public MsgBatchDO submitBatch(BatchSendRequestDTO dto) {
        if (dto == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "批量发送参数不能为空");
        }
        // 构建请求列表
        List<MessageRequest> requests = buildRequests(dto);
        if (requests.isEmpty()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "接收人列表为空");
        }
        if (requests.size() > MAX_BATCH_SIZE) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "单批最大 " + MAX_BATCH_SIZE + " 条，当前 " + requests.size() + " 条");
        }
        // 创建批次记录
        String batchId = StringUtils.hasText(dto.getBatchId())
                ? dto.getBatchId() : SnowflakeIdGenerator.nextIdStr();
        MsgBatchDO batch = new MsgBatchDO();
        batch.setBatchId(batchId);
        batch.setBatchName(dto.getBatchName());
        batch.setChannel(dto.getChannel());
        batch.setTemplateCode(dto.getTemplateCode());
        batch.setBizType(dto.getBizType());
        batch.setTotal(requests.size());
        batch.setSuccess(0);
        batch.setFailed(0);
        batch.setSkipped(0);
        batch.setStatus("PENDING");
        batch.setSenderId(dto.getSenderId());
        batch.setTenantId(TenantContext.getTenantId());
        msgBatchMapper.insert(batch);
        log.info("[Batch] 批次已创建: batchId={} total={} channel={}", batchId, requests.size(), dto.getChannel());

        // 异步执行
        boolean async = dto.getAsync() == null || dto.getAsync();
        if (async) {
            executeBatchAsync(batchId, requests);
        } else {
            executeBatchSync(batchId, requests);
        }
        return batch;
    }

    @Override
    public BatchProgressVO getProgress(String batchId) {
        if (!StringUtils.hasText(batchId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "批次 ID 不能为空");
        }
        MsgBatchDO batch = msgBatchMapper.selectOne(new LambdaQueryWrapper<MsgBatchDO>()
                .eq(MsgBatchDO::getBatchId, batchId)
                .last("LIMIT 1"));
        if (batch == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "批次不存在: " + batchId);
        }
        BatchProgressVO vo = new BatchProgressVO();
        vo.setBatchId(batch.getBatchId());
        vo.setBatchName(batch.getBatchName());
        vo.setChannel(batch.getChannel());
        vo.setTemplateCode(batch.getTemplateCode());
        vo.setTotal(batch.getTotal() == null ? 0 : batch.getTotal());
        vo.setSuccess(batch.getSuccess() == null ? 0 : batch.getSuccess());
        vo.setFailed(batch.getFailed() == null ? 0 : batch.getFailed());
        vo.setSkipped(batch.getSkipped() == null ? 0 : batch.getSkipped());
        int processed = vo.getSuccess() + vo.getFailed() + vo.getSkipped();
        vo.setProcessed(processed);
        vo.setProgressPercent(vo.getTotal() > 0
                ? Math.round(processed * 10000.0 / vo.getTotal()) / 100.0 : 0.0);
        vo.setStatus(batch.getStatus());
        vo.setErrorMessage(batch.getErrorMessage());
        vo.setStartedAt(batch.getStartedAt());
        vo.setCompletedAt(batch.getCompletedAt());
        vo.setCreatedAt(batch.getCreatedAt());
        return vo;
    }

    @Async("messageBatchExecutor")
    public void executeBatchAsync(String batchId, List<MessageRequest> requests) {
        doExecuteBatch(batchId, requests);
    }

    /**
     * 同步执行批次发送（async=false 时使用）。
     */
    private void executeBatchSync(String batchId, List<MessageRequest> requests) {
        doExecuteBatch(batchId, requests);
    }

    @Override
    public void executeBatch(String batchId) {
        // 兼容接口调用，从 DB 恢复请求列表（此处简化，实际场景可通过 JSON 列存储）
        log.warn("[Batch] executeBatch(batchId) 暂不支持从 DB 恢复请求列表，请使用 executeBatchAsync(batchId, requests)");
    }

    /**
     * 执行批次发送核心逻辑。
     *
     * @param batchId  批次 ID
     * @param requests 消息请求列表
     */
    private void doExecuteBatch(String batchId, List<MessageRequest> requests) {
        MsgBatchDO batch = msgBatchMapper.selectOne(new LambdaQueryWrapper<MsgBatchDO>()
                .eq(MsgBatchDO::getBatchId, batchId)
                .last("LIMIT 1"));
        if (batch == null) {
            log.warn("[Batch] 批次不存在: {}", batchId);
            return;
        }
        batch.setStatus("PROCESSING");
        batch.setStartedAt(LocalDateTime.now());
        msgBatchMapper.updateById(batch);

        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (int i = 0; i < requests.size(); i++) {
            MessageRequest req = requests.get(i);
            if (req == null) {
                skipped++;
                continue;
            }
            req.setBizId(batchId);
            try {
                MessageResult result = messageService.send(req);
                if (result != null && result.isSuccess()) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.warn("[Batch] 单条发送失败: batchId={} idx={} err={}", batchId, i, e.getMessage());
                failed++;
            }
            // 每 100 条更新一次进度
            if ((i + 1) % 100 == 0 || i == requests.size() - 1) {
                batch.setSuccess(success);
                batch.setFailed(failed);
                batch.setSkipped(skipped);
                msgBatchMapper.updateById(batch);
            }
        }
        batch.setSuccess(success);
        batch.setFailed(failed);
        batch.setSkipped(skipped);
        batch.setStatus("COMPLETED");
        batch.setCompletedAt(LocalDateTime.now());
        msgBatchMapper.updateById(batch);
        log.info("[Batch] 批次完成: batchId={} total={} success={} failed={} skipped={}",
                batchId, requests.size(), success, failed, skipped);
    }

    /**
     * 从 DTO 构建消息请求列表。
     *
     * <p>优先使用 receiverList 模式（统一模板展开），否则检查是否有直接传入的请求。
     *
     * @param dto 批量发送请求
     * @return 消息请求列表
     */
    private List<MessageRequest> buildRequests(BatchSendRequestDTO dto) {
        List<MessageRequest> requests = new ArrayList<>();
        if (!CollectionUtils.isEmpty(dto.getReceiverList())) {
            for (String receiver : dto.getReceiverList()) {
                if (!StringUtils.hasText(receiver)) {
                    continue;
                }
                MessageRequest req = new MessageRequest();
                req.setChannel(dto.getChannel());
                req.setTemplateCode(dto.getTemplateCode());
                req.setReceiver(receiver.trim());
                req.setParams(dto.getParams());
                req.setBizType(dto.getBizType());
                req.setMessageId(SnowflakeIdGenerator.nextIdStr());
                requests.add(req);
            }
        }
        return requests;
    }
}
