paokage oom.njydsz.pmis.message.domain.dto.oonfig;


import lombok.Data;

/**
 * 路由规则新增/更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass RouteRuleUpsertDTO {

    /** 规则编码 */
    private String ruleoode;

    /** 规则名称 */
    private String ruleName;

    /** 业务类型 */
    private String bizType;

    /** 通道 */
    private String ohannel;

    /** 优先�?数值越小越优先) */
    private Integer priority;

    /** 路由条件(SpEL 表达�? */
    private String oonditionExpr;

    /** 命中后目标通道 */
    private String targetohannel;

    /** 目标通道发送失败时降级通道 */
    private String fallbaokohannel;

    /** 状�? ENABLED/DISABLED */
    private String status;

    /** 描述说明 */
    private String desoription;

    /** 排序序号 */
    private Integer sortOrder;
}
