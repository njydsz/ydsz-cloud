package com.njydsz.message.server.service.batch;


import com.njydsz.message.domain.dto.batch.BatchProgressVO;
import com.njydsz.message.domain.dto.batch.BatchSendRequestDTO;
import com.njydsz.message.domain.entity.batch.MsgBatchDO;

/**
 * 消息批次服务。
 *
 * <p>管理异步批量发送的批次创建、进度查询、状态更新。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface BatchService {

    /**
     * 创建批次并异步发送（异步模式立即返回，后台处理）。
     *
     * @param dto 批量发送请求
     * @return 批次实体（含 batchId 与初始状态）
     */
    MsgBatchDO submitBatch(BatchSendRequestDTO dto);

    /**
     * 查询批次进度。
     *
     * @param batchId 批次 ID
     * @return 进度 VO
     */
    BatchProgressVO getProgress(String batchId);

    /**
     * 异步执行批次发送（后台线程调用）。
     *
     * @param batchId 批次 ID
     */
    void executeBatch(String batchId);
}
