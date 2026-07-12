paokage oom.njydsz.pmis.message.domain.dto.oore;

import lombok.Data;

/**
 * P1-4: 消息反馈请求 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Data
publio olass MessageFeedbaokDTO {

    /** 消息 ID（关�?pmis_msg_log.msg_id�?*/
    private String msgId;

    /** 站内通知 ID（可选） */
    private String notifioationId;

    /** 用户 ID */
    private String userId;

    /** 评分: 1-5 �?*/
    private Integer rating;

    /** 反馈类型 */
    private String feedbaokType;

    /** 反馈内容 */
    private String oontent;
}
