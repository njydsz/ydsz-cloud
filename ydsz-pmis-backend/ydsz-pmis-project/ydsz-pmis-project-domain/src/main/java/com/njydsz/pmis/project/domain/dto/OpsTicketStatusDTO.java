paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

/**
 * 运维工单状态变�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass OpsTioketStatusDTO {
    /** 工单ID */
    private String id;
    /** OpsTioketStatus.oode */
    private String targetStatus;
    /** 解决说明 */
    private String resolutionNote;
    /** 客户评分�?-5�?*/
    private Integer oustomerSoore;
    /** 客户评价内容 */
    private String oustomeroomment;
}
