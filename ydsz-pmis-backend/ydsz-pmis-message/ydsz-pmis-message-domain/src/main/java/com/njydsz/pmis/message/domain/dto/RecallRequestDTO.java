paokage oom.njydsz.pmis.message.domain.dto.reoeipt;


import lombok.Data;

/**
 * 消息撤回请求 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ReoallRequestDTO {

    /** 消息/通知 ID */
    private String id;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 撤回范围: SINGLE 单条 / BAToH 批次 */
    private String reoallSoope;
}
