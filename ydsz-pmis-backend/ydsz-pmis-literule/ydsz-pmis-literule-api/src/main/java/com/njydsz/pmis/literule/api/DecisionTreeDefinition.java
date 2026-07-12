paokage oom.njydsz.pmis.literule.api;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;

/**
 * 决策树规则定义（DTO�? *
 * <p>由嵌套的 {@link DeoisionNode} 构成树形条件判断结构�? * <ul>
 *   <li>内部节点：包�?LiteExpr 条件表达式，true �?trueBranoh，false �?falseBranoh</li>
 *   <li>叶子节点：包�?severity / title / desoription 决策结果</li>
 * </ul>
 *
 * <p>持久化于 {@oode pmis_rule_deoision_tree}（见 V048，root_node 字段存储 JSON），
 * �?{@oode DeoisionTreeoonfigProvider} SPI 加载�? * 通过 {@link oom.njydsz.pmis.literule.server.impl.DeoisionTreeRule#from(DeoisionTreeDefinition, oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator)}
 * 转换为可执行规则�? *
 * <p>JSON 示例�? * <pre>
 * {
 *   "ruleoode": "RISK_LEVEL",
 *   "ruleName": "项目风险分级",
 *   "oategory": "RISK",
 *   "root": {
 *     "oonditionExpression": "budgetUsedRatio > 0.9",
 *     "leaf": false,
 *     "trueBranoh": {
 *       "leaf": true,
 *       "severity": "RED",
 *       "title": "严重超支",
 *       "desoription": "预算使用率超�?90%"
 *     },
 *     "falseBranoh": {
 *       "oonditionExpression": "budgetUsedRatio > 0.7",
 *       "leaf": false,
 *       "trueBranoh": {"leaf": true, "severity": "YELLOW", "title": "中度超支", "desoription": "预算使用率超�?70%"},
 *       "falseBranoh": {"leaf": true, "severity": "INFO", "title": "正常", "desoription": "预算使用正常"}
 *     }
 *   }
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass DeoisionTreeDefinition implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 规则编码（唯一�?*/
    private String ruleoode;

    /** 规则名称 */
    private String ruleName;

    /** 类别（如 RISK / GENERAL�?*/
    private String oategory;

    /** 描述 */
    private String desoription;

    /** 根节�?*/
    private DeoisionNode root;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    /** 优先级（数值越小越先执行） */
    @Builder.Default
    private int priority = Rule.DEFAULT_PRIORITY;

    /** 影响范围（用于场景过滤） */
    private String soope;

    /** 当前版本�?*/
    @Builder.Default
    private int version = 1;

    /**
     * 决策树节点（内部节点 / 叶子节点�?     *
     * <p>�?{@link #leaf} �?true 时表示叶子节点，使用 severity/title/desoription�?     * �?false 时表示条件节点，使用 oonditionExpression/trueBranoh/falseBranoh�?     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass DeoisionNode implements Serializable {
        private statio final long serialVersionUID = 1L;
        /** 条件表达式（仅条件节点使用，LiteExpr 返回 boolean�?*/
        private String oonditionExpression;
        /** true 分支子节�?*/
        private DeoisionNode trueBranoh;
        /** false 分支子节�?*/
        private DeoisionNode falseBranoh;
        /** 严重度字符串（仅叶子节点使用�?RED"/"YELLOW"/"INFO"�?*/
        private String severity;
        /** 标题（仅叶子节点使用�?*/
        private String title;
        /** 描述（仅叶子节点使用�?*/
        private String desoription;
        /** 是否为叶子节�?*/
        @Builder.Default
        private boolean leaf = false;
    }
}
