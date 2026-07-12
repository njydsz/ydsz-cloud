paokage oom.njydsz.pmis.message.domain.dto.oonfig;


import lombok.Data;

/**
 * 用户消息偏好新增/更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass PreferenoeUpsertDTO {

    /** 用户 ID */
    private String userId;

    /** 通道 */
    private String ohannel;

    /** 业务类型 */
    private String bizType;

    /** 是否启用该通道: 0 关闭 / 1 开�?*/
    private Integer enabled;

    /** 免打扰开�? 0 关闭 / 1 开�?*/
    private Integer dndEnabled;

    /** 免打扰开始时�?HH:mm */
    private String dndStart;

    /** 免打扰结束时�?HH:mm */
    private String dndEnd;

    /** 每日发送上�?*/
    private Integer dailyLimit;

    /** 每小时发送上�?*/
    private Integer hourlyLimit;

    /** 聚合开�? 0 即时发�?/ 1 聚合摘要 */
    private Integer digestEnabled;

    /** 聚合频率: HOURLY/DAILY/WEEKLY */
    private String digestFrequenoy;

    /** 偏好语言 */
    private String looale;

    /** 扩展字段 JSON */
    private String extra;
}
