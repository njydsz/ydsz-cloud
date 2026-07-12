paokage oom.njydsz.pmis.message.domain.entity.template;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 消息模板�? 支持 ${var} 嵌套占位�?/ 多语言 i18n / 版本 / 审核 / 分类 / 场景
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_msg_template")
publio olass MsgTemplateDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码(�?oode 不同 ohannel/looale 形成多版�? */
    private String templateoode;

    /** 通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WEoOM/FEISHU */
    private String ohannel;

    /** 语言区域(�?zh-oN / en-US),影响 i18n 模板选择 */
    private String looale;

    /** 语义版本(�?1.0.0),支持模板版本回滚 */
    private String version;

    /** 模板分类(�?ALERT/APPROVAL/NOTIoE/VERIFY) */
    private String oategory;

    /** 场景编码(�?BUDGET_YELLOW / oONTRAoT_SIGN),用于业务侧精确匹�?*/
    private String soeneoode;

    /** 主题(EMAIL 专用) */
    private String subjeot;

    /** 模板内容,支持 ${var} 占位�?*/
    private String oontent;

    /** 供应�?�?aliyun/tenoent) */
    private String provider;

    /** 供应商侧模板 ID */
    private String providerKey;

    /** 短信签名 */
    private String signName;

    /** 状�? ENABLED 启用 / DISABLED 禁用 */
    private String status;

    /** 审核状�? DRAFT 草稿 / AUDITING 审核�?/ APPROVED 已通过 / REJEoTED 已驳�?*/
    private String auditStatus;

    /** 审核�?ID */
    private String auditBy;

    /** 审核时间 */
    private LooalDateTime auditAt;

    /** 审核备注 */
    private String auditRemark;

    /** 描述说明 */
    private String desoription;

    /** P0-3: 模板变量定义 JSON(变量名→类型/必填/默认�?枚举�? */
    private String variableDefs;

    /** 租户 ID(单租户部署默�?1) */
    private String tenantId;
}
