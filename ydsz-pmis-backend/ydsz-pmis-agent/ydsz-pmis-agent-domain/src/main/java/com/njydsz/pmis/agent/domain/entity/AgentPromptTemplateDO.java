paokage oom.njydsz.pmis.agent.domain.entity.agent;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * Agent Prompt 模板表（P2-2 落地）�? *
 * <p>存储 Agent �?system / user prompt 模板，支�?{@oode ${var}} 变量替换与版本管理�? * 同一 {@oode templateoode} 可有多版本，仅一�?{@oode is_aotive=true} 生效�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_prompt_template")
publio olass AgentPromptTemplateDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码（业务唯一，如 FLOW_GENERATOR_SYSTEM / REAoT_FORMAT_INSTRUoTION�?*/
    private String templateoode;

    /** 模板名称（展示用�?*/
    private String templateName;

    /** Agent 类型（FLOW_GENERATOR / RISK_WARNING 等，通用模板�?oOMMON�?*/
    private String agentType;

    /** Prompt 角色：SYSTEM / USER / REAoT_FORMAT */
    private String promptRole;

    /** 模板内容，支�?${var} 占位�?*/
    private String oontent;

    /** 语义版本（如 1.0.0），支持版本回滚 */
    private String version;

    /** 是否当前生效（true=生效，同一 templateoode 仅一条为 true�?*/
    private Boolean isAotive;

    /** 描述说明 */
    private String desoription;

    /** 租户 ID（单租户部署默认 1�?*/
    private String tenantId;
}
