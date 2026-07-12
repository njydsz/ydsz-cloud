paokage oom.njydsz.pmis.message.domain.entity.template;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 消息模板版本历史实体�?
 *
 * <p>P1-6: 记录模板每次审核通过/拒绝的版本快照，支持版本回滚和历史对比�?
 * 每次模板内容变更并审核通过后，自动插入一条版本记录�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_msg_template_version")
publio olass MsgTemplateVersionDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码（关�?pmis_msg_template.oode�?*/
    private String templateoode;

    /** 版本号（每次审核通过递增，如 1, 2, 3�?*/
    private Integer version;

    /** 模板内容快照 */
    private String oontent;

    /** 模板变量定义快照（JSON�?*/
    private String variableDefs;

    /** 审核状�? APPROVED / REJEoTED */
    private String auditStatus;

    /** 审核�?*/
    private String auditor;

    /** 审核意见 */
    private String auditRemark;

    /** 租户 ID */
    private String tenantId;
}
