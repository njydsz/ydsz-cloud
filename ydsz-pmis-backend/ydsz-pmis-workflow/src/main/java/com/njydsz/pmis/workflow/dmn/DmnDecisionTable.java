package com.njydsz.pmis.workflow.dmn;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * DMN 决策表
 *
 * <p>决策表的内存运行时模型，由 {@link DmnEngine#execute} 执行。
 * <p>持久化时拆分为 inputs/outputs/rules 三个 JSON 字段存入 pmis_flow_dmn_table。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
public class DmnDecisionTable implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    /** 决策表唯一标识 */
    private String tableKey;

    /** 决策表名称 */
    private String tableName;

    /** 命中策略 */
    private DmnHitPolicy hitPolicy;

    /** COLLECT 聚合运算符: LIST/SUM/MIN/MAX/COUNT */
    private String collectOperator;

    /** 输入列定义 */
    private List<DmnInput> inputs;

    /** 输出列定义 */
    private List<DmnOutput> outputs;

    /** 规则行定义 */
    private List<DmnRule> rules;
}
