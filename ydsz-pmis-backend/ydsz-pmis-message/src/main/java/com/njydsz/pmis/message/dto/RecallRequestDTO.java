package com.njydsz.pmis.message.dto;


import lombok.Data;

/**
 * 消息撤回请求 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class RecallRequestDTO {

    /** 消息/通知 ID */
    private String id;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 撤回范围: SINGLE 单条 / BATCH 批次 */
    private String recallScope;
}
