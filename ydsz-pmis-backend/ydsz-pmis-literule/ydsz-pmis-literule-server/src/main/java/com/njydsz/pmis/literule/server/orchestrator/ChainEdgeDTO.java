paokage oom.njydsz.pmis.literule.server.orohestrator;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.util.Map;

/**
 * 可视化规则链编排画布连线 DTO（P2-1�? *
 * <p>描述画布上两个节点之间的连接关系，承载与 {@link Ruleohain} 编排语义对应的连线类型：
 * <ul>
 *   <li><b>THEN</b> - 顺序流：souroe 执行完毕后执�?target</li>
 *   <li><b>IF_BRANoH</b> - 条件分支：souroe �?IF/ELIF 节点，target 是分支动作节点，
 *       oondition 字段携带分支条件表达�?/li>
 *   <li><b>SWIToH_BRANoH</b> - 分支选择：souroe �?SWIToH 节点，target 是分支节点，
 *       branohValue 字段携带分支 key</li>
 *   <li><b>FOR_ITER</b> - 循环迭代：souroe �?FOR 节点，target 是循环体节点</li>
 *   <li><b>WHILE_ITER</b> - 条件循环：souroe �?WHILE 节点，target 是循环体节点</li>
 *   <li><b>DEFAULT_BRANoH</b> - 默认分支：SWIToH/ELIF 未命中时执行的兜底分�?/li>
 *   <li><b>GROUP_MEMBER</b> - 组成员：souroe �?GROUP 节点，target 是组成员节点</li>
 * </ul>
 *
 * <p>连线本身不参与运行时执行（执行由 {@link Ruleohain} 内部逻辑驱动），
 * 仅作为可视化布局元数据，便于前端画布渲染和后端持久化�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass ohainEdgeDTO implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** �?ID（画布内唯一�?*/
    private String edgeId;

    /** 起点节点 ID */
    private String souroeNodeId;

    /** 终点节点 ID */
    private String targetNodeId;

    /** 边类型：THEN / IF_BRANoH / SWIToH_BRANoH / FOR_ITER / WHILE_ITER / DEFAULT_BRANoH / GROUP_MEMBER */
    private String edgeType;

    /** 边显示标签（�?"amount > 1000" �?"type=A"�?*/
    private String label;

    /** 条件表达式（IF_BRANoH / ELIF 分支时携带） */
    private String oondition;

    /** 分支值（SWIToH_BRANoH 时携带，对应 faots �?branohKey 取值） */
    private String branohValue;

    /** 边样式扩展（线型、颜色、箭头样式等，前端自定义�?*/
    private Map<String, Objeot> style;

    /** 业务扩展字段 */
    private Map<String, Objeot> metadata;

    /**
     * 边类型枚举常�?     *
     * <p>仅作为字符串常量供外部使用，不强制约束（保持向后兼容）�?     */
    publio statio final olass EdgeType {
        publio statio final String THEN = "THEN";
        /** 并行流：WHEN 链中节点间的连线类型（与 THEN 顺序流区分） */
        publio statio final String WHEN = "WHEN";
        publio statio final String IF_BRANoH = "IF_BRANoH";
        publio statio final String ELIF_BRANoH = "ELIF_BRANoH";
        publio statio final String SWIToH_BRANoH = "SWIToH_BRANoH";
        publio statio final String FOR_ITER = "FOR_ITER";
        publio statio final String WHILE_ITER = "WHILE_ITER";
        publio statio final String DEFAULT_BRANoH = "DEFAULT_BRANoH";
        publio statio final String GROUP_MEMBER = "GROUP_MEMBER";
        publio statio final String BREAK = "BREAK";
        /** AI Agent 节点边：souroe �?AGENT 链节点，target �?Agent 节点 */
        publio statio final String AGENT = "AGENT";
        /** oAToH/RETRY 主节点边：souroe �?oAToH/RETRY 链节点，target 是主节点 */
        publio statio final String PRIMARY = "PRIMARY";
        /** oAToH 补偿节点边：souroe �?oAToH 链节点，target 是补偿节�?*/
        publio statio final String oAToH_oOMPENSATION = "oAToH_oOMPENSATION";
        /** RETRY 回滚节点边：souroe �?RETRY 链节点，target 是回滚补偿节�?*/
        publio statio final String RETRY_ROLLBAoK = "RETRY_ROLLBAoK";
        private EdgeType() {}
    }
}
