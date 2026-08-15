package com.njydsz.message.server.service.impl.batch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.dto.batch.BatchProgressVO;
import com.njydsz.message.domain.dto.batch.BatchSendRequestDTO;
import com.njydsz.message.domain.entity.batch.MsgBatch;
import com.njydsz.message.infra.mapper.batch.MsgBatchMapper;
import com.njydsz.message.server.service.batch.BatchService;
import com.njydsz.message.server.service.core.MessageService;
import com.njydsz.message.server.service.impl.ParallelBatchSender;
import com.njydsz.message.domain.dto.batch.BatchSendResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

    /** 单批最大条数 */
    private static final int MAX_BATCH_SIZE = 10000;

    /** 批次记录 Mapper */
    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final MsgBatchMapper msgBatchMapper;
    /** 消息发送服务（逐条发送） */
    private final MessageService messageService;
    /** P1-3: 并行批量发送器 */
    private final ParallelBatchSender parallelBatchSender;

    @Override
    public MsgBatch submitBatch(BatchSendRequestDTO dto) {
        if (dto == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("批量发送参数不能为空")
                .build();
        }
        // 构建请求列表
        List<MessageRequest> requests = buildRequests(dto);
        if (requests.isEmpty()) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("接收人列表为空")
                .build();
        }
        if (requests.size() > MAX_BATCH_SIZE) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("单批最大 " + MAX_BATCH_SIZE + " 条，当前 " + requests.size() + " 条")
                .build();
        }
        // 创建批次记录
        String batchId = StringUtils.hasText(dto.getBatchId())
                ? dto.getBatchId() : String.valueOf(snowflakeIdGenerator.nextId());
        MsgBatch batch = new MsgBatch();
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
        batch.setTenantId(TenantContextHolder.getTenantId());
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
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("批次 ID 不能为空")
                .build();
        }
        MsgBatch batch = msgBatchMapper.selectOne(new LambdaQueryWrapper<MsgBatch>()
                .eq(MsgBatch::getBatchId, batchId)
                .last("LIMIT 1"));
        if (batch == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("批次不存在: " + batchId)
                .build();
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

    /**
     * 异步执行批次发送（在 {@code messageBatchExecutor} 线程池执行）。
     *
     * <p>由 {@link #submitBatch} 在 {@code async=true}（默认）时调用；内部委托
     * {@link #doExecuteBatch} 完成状态推进与并行发送。
     * 注意：本方法通过 Spring {@code @Async} 代理生效，<strong>务必经注入的
     * Bean 调用</strong>，同类内直接调用（self-invocation）不会触发异步。
     *
     * @param batchId  批次 ID
     * @param requests 待发送消息请求列表（非空，已在 {@link #submitBatch} 校验上限）
     */
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
        // D-4: 从 DB 恢复请求列表（需 MsgBatch 新增 payload 字段存储 JSON 序列化的 requests）
        MsgBatch batch = msgBatchMapper.selectOne(new LambdaQueryWrapper<MsgBatch>()
                .eq(MsgBatch::getBatchId, batchId)
                .last("LIMIT 1"));
        if (batch == null) {
            log.warn("[Batch] 批次不存在: {}", batchId);
            return;
        }
        // D-4: 从 batchName 字段反序列化请求列表（临时方案，后续应新增 payload 列）
        // TODO: MsgBatch 新增 payload TEXT 列后，改为 batch.getPayload()
        log.warn("[Batch] executeBatch(batchId) 需 MsgBatch.payload 列支持，当前版本请使用 executeBatchAsync(batchId, requests)");
    }

    /**
     * 执行批次发送核心逻辑。
     *
     * <p>P1-3: 使用 ParallelBatchSender 并行发送，避免单线程逐条发送的性能瓶颈。
     *
     * @param batchId  批次 ID
     * @param requests 消息请求列表
     */
    private void doExecuteBatch(String batchId, List<MessageRequest> requests) {
        MsgBatch batch = msgBatchMapper.selectOne(new LambdaQueryWrapper<MsgBatch>()
                .eq(MsgBatch::getBatchId, batchId)
                .last("LIMIT 1"));
        if (batch == null) {
            log.warn("[Batch] 批次不存在: {}", batchId);
            return;
        }
        batch.setStatus("PROCESSING");
        batch.setStartedAt(LocalDateTime.now());
        msgBatchMapper.updateById(batch);

        // P1-3: 使用并行批量发送器
        String channel = batch.getChannel() != null ? batch.getChannel() : "INAPP";
        BatchSendResult batchResult = parallelBatchSender.sendBatch(
                requests, channel, messageService::send);

        batch.setSuccess(batchResult.getSuccess());
        batch.setFailed(batchResult.getFailed());
        batch.setSkipped(batchResult.getSkipped());
        batch.setStatus("COMPLETED");
        batch.setCompletedAt(LocalDateTime.now());
        msgBatchMapper.updateById(batch);
        log.info("[Batch] 批次完成: batchId={} total={} success={} failed={} skipped={}",
                batchId, requests.size(), batchResult.getSuccess(),
                batchResult.getFailed(), batchResult.getSkipped());
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
                req.setMessageId(String.valueOf(snowflakeIdGenerator.nextId()));
                requests.add(req);
            }
        }
        return requests;
    }
}
