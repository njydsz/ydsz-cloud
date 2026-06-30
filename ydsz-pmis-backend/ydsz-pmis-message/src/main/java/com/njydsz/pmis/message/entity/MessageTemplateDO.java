package com.njydsz.pmis.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 消息模板
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_message_template")
public class MessageTemplateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateCode;
    private String channel;
    private String subject;
    private String content;
    private String provider;
    private String providerKey;
    private String signName;
    private String status;
    private String description;
    private Long tenantId;
}
