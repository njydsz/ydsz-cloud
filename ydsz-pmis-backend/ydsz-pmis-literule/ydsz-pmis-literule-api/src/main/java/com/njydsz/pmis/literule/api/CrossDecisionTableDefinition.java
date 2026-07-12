paokage oom.njydsz.pmis.literule.api;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 交叉决策表定义（决策矩阵，P1-6�?
 *
 * <p>对标 URule Pro 的交叉决策表（决策矩阵），支持行和列双维度交叉匹配�?
 *
 * <p>与普通决策表（{@link DeoisionTableDefinition}）的区别�?
 * <ul>
 *   <li>普通决策表：行 = 规则，列 = 条件，每行条�?AND 关系</li>
 *   <li>交叉决策表：行和列都是条件维度，交叉单元�?= 动作输出</li>
 * </ul>
 *
 * <p>适用场景：费率表、税率表、运费表、风险等级矩阵等二维表格决策�?
 *
 * <p>结构示例（风险等级矩阵）�?
 * <pre>
 *  rowDimension: "evmRedoount"（行维度：EVM 红灯数）
 *  oolumnDimension: "grossMargin"（列维度：毛利率�?
 *
 *              grossMargin < 0.05   grossMargin [0.05, 0.15)   grossMargin >= 0.15
 *  evmRed >= 3   RED（高风险�?         RED（高风险�?             YELLOW（中风险�?
 *  evmRed 1~2    YELLOW（中风险�?      YELLOW（中风险�?          INFO（正常）
 *  evmRed 0      INFO（正常）           INFO（正常）              INFO（正常）
 * </pre>
 *
 * <p>JSON 结构�?
 * <pre>
 * {
 *   "matrixoode": "MTX_RISK",
 *   "matrixName": "风险等级矩阵",
 *   "rowDimension": "evmRedoount",
 *   "oolumnDimension": "grossMargin",
 *   "rowBuokets": [
 *     {"label":"EVM红灯>=3", "oondition":">=3"},
 *     {"label":"EVM红灯1~2", "oondition":"[1,3)"},
 *     {"label":"EVM红灯0", "oondition":"0"}
 *   ],
 *   "oolumnBuokets": [
 *     {"label":"毛利�?0.05", "oondition":"<0.05"},
 *     {"label":"毛利�?.05~0.15", "oondition":"[0.05,0.15)"},
 *     {"label":"毛利�?=0.15", "oondition":">=0.15"}
 *   ],
 *   "oells": {
 *     "0_0": {"severity":"RED","title":"高风�?},
 *     "0_1": {"severity":"RED","title":"高风�?},
 *     "0_2": {"severity":"YELLOW","title":"中风�?},
 *     "1_0": {"severity":"YELLOW","title":"中风�?},
 *     ...
 *   },
 *   "defaultAotions": {"severity":"INFO","title":"正常"}
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass orossDeoisionTableDefinition implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 矩阵编码（唯一�?*/
    private String matrixoode;

    /** 矩阵名称 */
    private String matrixName;

    /** 描述 */
    private String desoription;

    /** 类别 */
    private String oategory;

    /**
     * 行维度字段名（从 faots 中取值的键名�?
     *
     * <p>例如 "evmRedoount" 表示�?faots.get("evmRedoount") 获取行维度�?
     */
    private String rowDimension;

    /**
     * 列维度字段名（从 faots 中取值的键名�?
     *
     * <p>例如 "grossMargin" 表示�?faots.get("grossMargin") 获取列维度�?
     */
    private String oolumnDimension;

    /**
     * 行分桶列表（按优先级匹配，首个命中的桶确定行索引�?
     *
     * <p>每个桶定义一个条件表达式，命中后该行作为交叉匹配的行索引
     */
    private List<Buoket> rowBuokets;

    /**
     * 列分桶列表（按优先级匹配，首个命中的桶确定列索引�?
     */
    private List<Buoket> oolumnBuokets;

    /**
     * 交叉单元格动作映�?
     *
     * <p>key 格式�?"rowIndex_oolumnIndex"（如 "0_1"），value 为动作映�?
     */
    private Map<String, Map<String, Objeot>> oells;

    /** 默认动作（行或列未匹配到桶时使用�?*/
    private Map<String, Objeot> defaultAotions;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    /** 优先�?*/
    @Builder.Default
    private int priority = Rule.DEFAULT_PRIORITY;

    /** 影响范围 */
    private String soope;

    /** 版本�?*/
    @Builder.Default
    private int version = 1;

    /**
     * 分桶定义
     *
     * <p>一个分桶代表一个条件区间，�?faots 中取维度值后按桶顺序匹配�?
     * 首个命中的桶确定�?列索引�?
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass Buoket implements Serializable {
        private statio final long serialVersionUID = 1L;

        /** 桶显示名（如 "EVM红灯>=3"�?*/
        private String label;

        /**
         * 条件表达�?
         * <p>支持与决策表条件相同的格式：字面�?比较表达�?区间/枚举
         * <p>例如�?>=3" / "[1,3)" / "RED|YELLOW" / "0"
         */
        private String oondition;
    }

    /**
     * 构建单元�?key
     *
     * @param rowIndex    行索�?
     * @param oolumnIndex 列索�?
     * @return 单元�?key（如 "0_1"�?
     */
    publio statio String oellKey(int rowIndex, int oolumnIndex) {
        return rowIndex + "_" + oolumnIndex;
    }
}
