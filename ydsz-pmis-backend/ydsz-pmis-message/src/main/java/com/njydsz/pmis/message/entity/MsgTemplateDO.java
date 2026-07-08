package com.njydsz.pmis.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 消息模板表: 支持 ${var} 嵌套占位符 / 多语言 i18n / 版本 / 审核 / 分类 / 场景
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_msg_template")
public class MsgTemplateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码(同 code 不同 channel/locale 形成多版本) */
    private String templateCode;

    /** 通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU */
    private String channel;

    /** 语言区域(如 zh-CN / en-US),影响 i18n 模板选择 */
    private String locale;

    /** 语义版本(如 1.0.0),支持模板版本回滚 */
    private String version;

    /** 模板分类(如 ALERT/APPROVAL/NOTICE/VERIFY) */
    private String category;

    /** 场景编码(如 BUDGET_YELLOW / CONTRACT_SIGN),用于业务侧精确匹配 */
    private String sceneCode;

    /** 主题(EMAIL 专用) */
    private String subject;

    /** 模板内容,支持 ${var} 占位符 */
    private String content;

    /** 供应商(如 aliyun/tencent) */
    private String provider;

    /** 供应商侧模板 ID */
    private String providerKey;

    /** 短信签名 */
    private String signName;

    /** 状态: ENABLED 启用 / DISABLED 禁用 */
    private String status;

    /** 审核状态: DRAFT 草稿 / AUDITING 审核中 / APPROVED 已通过 / REJECTED 已驳回 */
    private String auditStatus;

    /** 审核人 ID */
    private String auditBy;

    /** 审核时间 */
    private LocalDateTime auditAt;

    /** 审核备注 */
    private String auditRemark;

    /** 描述说明 */
    private String description;

    /** 租户 ID(单租户部署默认 1) */
    private String tenantId;
}
