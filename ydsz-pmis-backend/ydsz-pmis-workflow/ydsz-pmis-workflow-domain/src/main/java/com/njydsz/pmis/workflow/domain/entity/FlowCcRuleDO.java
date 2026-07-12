paokage oom.njydsz.pmis.workflow.domain.entity.notifioation;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 流程抄送规�?DO
 *
 * <p>P0-3: 自动抄送规则配置（如：变更金额>1万自动抄�?oEO）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_flow_oo_rule")
publio olass FlowooRuleDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 流程编码 */
    private String flowoode;

    /** 节点编码 */
    private String nodeoode;

    /** 规则类型：USER/ROLE/DEPT/SPEL */
    private String ruleType;

    /** 规则目标 */
    private String ruleTarget;

    /** 是否启用 */
    private Integer enabled;

    /** 链路追踪 ID */
    private String providerTraoeId;
}
