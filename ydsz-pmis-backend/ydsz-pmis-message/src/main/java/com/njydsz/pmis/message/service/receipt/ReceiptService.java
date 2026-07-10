package com.njydsz.pmis.message.service;

import com.njydsz.pmis.message.dto.receipt.ReceiptCallbackDTO;
import com.njydsz.pmis.message.entity.receipt.MsgReceiptDO;

import java.util.List;

/**
 * 消息回执服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ReceiptService {

    /**
     * 处理服务商回执回调
     *
     * @param dto 回执回调参数
     */
    void callback(ReceiptCallbackDTO dto);

    /**
     * 根据日志 ID 查询回执列表
     *
     * @param logId 日志 ID
     * @return 回执列表
     */
    List<MsgReceiptDO> listByLogId(String logId);
}
