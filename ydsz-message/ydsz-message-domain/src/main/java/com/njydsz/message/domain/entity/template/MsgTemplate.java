package com.njydsz.message.domain.entity.template;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息模板表: 支持 ${var} 嵌套占位符 / 多语言 i18n / 版本 / 审核 / 分类 / 场景
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_template")
public class MsgTemplate extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

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

    /** P0-3: 模板变量定义 JSON(变量名→类型/必填/默认值/枚举值) */
    private String variableDefs;

}
