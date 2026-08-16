package com.njydsz.message.domain.entity.receipt;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息回执表: 服务商送达/已读/点击/失败回调记录
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_receipt")
public class MsgReceipt extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联 ydsz_msg_log.id */
    private String logId;

    /** 三方服务商回执 ID */
    private String providerTraceId;

    /** 回执类型: DELIVERED 送达 / READ 已读 / CLICKED 点击 / FAILED 失败 */
    private String receiptType;

    /** 回执时间 */
    private LocalDateTime receiptTime;

    /** 供应商编码 */
    private String providerCode;

    /** 供应商消息 */
    private String providerMsg;

    /** 原始响应 JSON */
    private String rawResponse;

}
