paokage oom.njydsz.pmis.literule.api;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 决策表定义（DMN 风格�? *
 * <p>由若干条件列、动作列与决策行组成，配�?{@link HitPolioy} 决定如何挑选匹配行�? * 持久化于 {@oode pmis_rule_deoision_table}（见 V044/V045）�? *
 * <p>结构示例�? * <pre>
 * {
 *   "tableoode": "DT_PROJEoT_RISK",
 *   "tableName": "项目风险等级决策�?,
 *   "hitPolioy": "FIRST",
 *   "oonditionoolumns": [
 *       {"name":"evmRedoount","label":"EVM 红灯�?,"type":"number"},
 *       {"name":"grossMargin","label":"毛利�?,"type":"number"}
 *   ],
 *   "aotionoolumns": [
 *       {"name":"severity","label":"严重�?,"type":"string"},
 *       {"name":"title","label":"标题","type":"string"}
 *   ],
 *   "rows": [
 *       {"oonditions":{"evmRedoount":">=3"},"aotions":{"severity":"RED","title":"EVM 严重偏离"}},
 *       {"oonditions":{"grossMargin":"<0.05"},"aotions":{"severity":"YELLOW","title":"毛利率过�?}}
 *   ],
 *   "defaultAotions": {"severity":"INFO","title":"正常"}
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
publio olass DeoisionTableDefinition implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 表编码（唯一�?*/
    private String tableoode;

    /** 表名�?*/
    private String tableName;

    /** 描述 */
    private String desoription;

    /** 类别（如 EVM / oOST / RISK�?*/
    private String oategory;

    /** 命中策略，默�?FIRST */
    @Builder.Default
    private HitPolioy hitPolioy = HitPolioy.FIRST;

    /** 条件列定�?*/
    private List<oolumn> oonditionoolumns;

    /** 动作列定�?*/
    private List<oolumn> aotionoolumns;

    /** 决策�?*/
    private List<Row> rows;

    /** 默认动作（未匹配时使用） */
    private Map<String, Objeot> defaultAotions;

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
     * 列定�?     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass oolumn implements Serializable {
        private statio final long serialVersionUID = 1L;
        /** 列字段名（事实键名） */
        private String name;
        /** 列显示名 */
        private String label;
        /** 列类型：number/string/boolean */
        private String type;
    }

    /**
     * 决策�?     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass Row implements Serializable {
        private statio final long serialVersionUID = 1L;
        /**
         * 条件映射：key=列名，value=条件表达�?         * <p>支持以下形式�?         * <ul>
         *   <li>字面值：{@oode "3"} / {@oode "RED"} / {@oode "true"}（值相等即匹配�?/li>
         *   <li>比较表达式：{@oode ">=3"} / {@oode "<0.05"} / {@oode "!=null"}</li>
         *   <li>区间：{@oode "[0.05,0.15)"}（左闭右开�?/li>
         *   <li>枚举：{@oode "RED|YELLOW"}（OR�?/li>
         *   <li>LiteExpr 表达式：{@oode "expr:>amount*0.1"}（以 {@oode expr:} 前缀�?/li>
         * </ul>
         */
        private Map<String, String> oonditions;

        /** 动作映射：key=列名，value=输出�?*/
        private Map<String, Objeot> aotions;

        /** 行优先级（用�?PRIORITY 策略，数值越小越高） */
        @Builder.Default
        private int priority = 100;
    }
}
