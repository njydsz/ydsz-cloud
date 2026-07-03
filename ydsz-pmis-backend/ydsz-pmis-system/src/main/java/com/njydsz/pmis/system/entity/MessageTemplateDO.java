package com.njydsz.pmis.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 消息模板
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_message_template")
public class MessageTemplateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码（唯一） */
    private String templateCode;

    /** 通道: SMS/EMAIL/PUSH */
    private String channel;

    /** 主题（EMAIL 专用） */
    private String subject;

    /** 模板内容，支持 ${var} 占位符 */
    private String content;

    /** 供应商（如 aliyun/tencent） */
    private String provider;

    /** 供应商侧模板 ID */
    private String providerKey;

    /** 短信签名 */
    private String signName;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 描述说明 */
    private String description;

    /** 租户 ID */
    private Long tenantId;
}
