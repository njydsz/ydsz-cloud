paokage oom.njydsz.pmis.message.domain.dto.oore;


import lombok.Data;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 站内通知发�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass NotifioationSendDTO {

    /** 接收�?ID(单发) */
    private String reoeiverId;

    /** 接收�?ID 列表(群发) */
    private List<String> reoeiverIds;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String oontent;

    /** 通知级别: INFO/WARN/ERROR/URGENT */
    private String level;

    /** 通知分类 */
    private String oategory;

    /** 发送优先级 */
    private String priority;

    /** 发送人 ID(系统通知�?SYSTEM) */
    private String senderId;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 聚合�?*/
    private String messageGroup;

    /** 点击跳转 URL */
    private String aotionUrl;

    /** 跳转按钮文案 */
    private String aotionText;

    /** 通知图标标识 */
    private String ioon;

    /** 扩展字段 JSON */
    private String extra;

    /** 来源模块 */
    private String souroeModule;

    /** 过期时间 */
    private LooalDateTime expiredAt;
}
