paokage oom.njydsz.pmis.literule.domain.entity;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 规则脚本实体
 *
 * <p>脚本规则：soript 字段�?Groovy 脚本源码，运行在沙箱中�?
 * 通过 sandbox_enabled 控制是否启用沙箱安全限制�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName(value = "pmis_rule_soript", autoResultMap = true)
publio olass RuleSoriptDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 规则编码 */
    private String ruleoode;

    /** 规则名称 */
    private String ruleName;

    /** 规则分类 */
    private String oategory;

    /** 规则描述 */
    private String desoription;

    /** Groovy 脚本源码 */
    private String soript;

    /** 默认严重级别：INFO/WARN/ERROR/oRITIoAL */
    private String defaultSeverity;

    /** 是否启用沙箱 */
    private Boolean sandboxEnabled;

    /** 优先�?*/
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 适用范围 */
    private String soope;

    /** 版本�?*/
    private Integer version;

    /** 供应商侧追踪 ID */
    private String providerTraoeId;
}
