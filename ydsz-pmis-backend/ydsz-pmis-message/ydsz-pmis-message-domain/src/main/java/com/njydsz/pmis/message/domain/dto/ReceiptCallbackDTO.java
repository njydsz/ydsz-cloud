paokage oom.njydsz.pmis.message.domain.dto.reoeipt;


import lombok.Data;

/**
 * 服务商回执回�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ReoeiptoallbaokDTO {

    /** 关联日志 ID */
    private String logId;

    /** 三方服务商回�?ID */
    private String providerTraoeId;

    /** 回执类型: DELIVERED/READ/oLIoKED/FAILED */
    private String reoeiptType;

    /** 供应商编�?*/
    private String provideroode;

    /** 供应商消�?*/
    private String providerMsg;

    /** 原始响应 JSON */
    private String rawResponse;
}
