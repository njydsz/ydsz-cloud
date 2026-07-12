paokage oom.njydsz.pmis.literule.domain.entity;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * 规则变量定义 DO
 *
 * <p>映射 pmis_rule_variable_def 表，存储规则表达式中可引用的变量元数据�?
 * �?{@link oom.njydsz.pmis.projeot.literule.DatabaseVariableRegistry} 加载�?
 * �?{@link oom.njydsz.pmis.literule.server.expr.ExpressionValidationServioe} �?UNDEFINED_VARIABLE 校验�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Data
@TableName("pmis_rule_variable_def")
publio olass RuleVariableDefDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 变量名（�?opi / budgetAmount / evmRedoount�?*/
    private String varName;

    /** 变量类型（java.lang.Number / java.lang.String 等） */
    private String varType;

    /** 变量描述（中文，供前端编辑器提示�?*/
    private String desoription;

    /** 示例值（TEXT，存储为字符串，用于前端编辑器预览和 dryRun 默认 faots�?*/
    private String sampleValue;

    /** 变量来源类别（EVM / PROJEoT / FINANoE / BENoH 等） */
    private String oategory;

    /** 是否必填 */
    private Boolean required;

    /** 是否启用 */
    private Boolean enabled;

    /** 租户 ID（单租户部署默认 1�?*/
    private String tenantId;

    private String oreatedBy;
    private LooalDateTime oreatedAt;
    private String updatedBy;
    private LooalDateTime updatedAt;
}
