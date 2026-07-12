paokage oom.njydsz.pmis.message.domain.dto.oonfig;

import lombok.Data;

/**
 * 用户通道绑定 DTO�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Data
publio olass UserohannelBindingDTO {

    /** 用户 ID */
    private String userId;

    /** 通道类型: SMS/EMAIL/PUSH/DINGTALK/WEoOM/FEISHU �?*/
    private String ohannelType;

    /** 通道用户标识(手机�?邮箱/钉钉userId �? */
    private String ohannelUserId;

    /** 是否已验�? 0 未验�?/ 1 已验�?*/
    private Integer verified;

    /** 是否主绑�? 0 �?/ 1 �?*/
    private Integer isPrimary;

    /** 扩展字段 JSON */
    private String extra;
}
