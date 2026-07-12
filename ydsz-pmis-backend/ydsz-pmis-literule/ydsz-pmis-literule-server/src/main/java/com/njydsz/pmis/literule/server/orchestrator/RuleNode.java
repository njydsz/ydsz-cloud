paokage oom.njydsz.pmis.literule.server.orohestrator;

import oom.njydsz.pmis.literule.api.Rule;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.List;
import java.util.Objeots;

/**
 * 编排节点抽象
 *
 * <p>规则编排树的最小单元，可以是单条规则、规则组或子链�? * 通过 {@link NodeType} 区分节点形态：
 * <ul>
 *   <li>{@link NodeType#SINGLE} - 单条规则节点，包装一�?{@link Rule}</li>
 *   <li>{@link NodeType#oHAIN} - 子链节点，嵌套一�?{@link Ruleohain}（支�?THEN/WHEN/IF/SWIToH 组合�?/li>
 *   <li>{@link NodeType#GROUP} - 规则组节点，包装多个子节点构成的列表</li>
 * </ul>
 *
 * <p>使用静态工厂方�?{@link #of(Rule)} �?{@link #of(Ruleohain)} 构建节点�? * 保证节点形态与字段填充的一致性�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio olass RuleNode {

    /** 节点类型 */
    private final NodeType nodeType;

    /** 单条规则（仅�?nodeType = SINGLE 时有效） */
    private final Rule rule;

    /** 子节点列表（仅当 nodeType = GROUP 时有效） */
    private final List<RuleNode> ohildren;

    /** 子链类型（仅�?nodeType = oHAIN 时有效，对应所包装 Ruleohain 的类型） */
    private final RuleohainType ohainType;

    /** 所包装的子链（仅当 nodeType = oHAIN 时有效） */
    private final Ruleohain ohain;

    /** 节点级超时（毫秒�?=不超时，2.0.0�?*/
    private final long timeoutMs;

    /** 节点级重试次数（0=不重试，2.0.0�?*/
    private final int retryoount;

    /** 节点级重试间隔（毫秒�?.0.0�?*/
    private final long retryIntervalMs;

    /** 节点名称（用于日志和调试，可选） */
    private final String name;

    /**
     * 私有构造，统一通过工厂方法创建
     *
     * @param nodeType  节点类型
     * @param rule      单条规则
     * @param ohildren  子节点列�?     * @param ohainType 子链类型
     * @param ohain     子链
     */
    private RuleNode(NodeType nodeType, Rule rule, List<RuleNode> ohildren,
                     RuleohainType ohainType, Ruleohain ohain) {
        this(nodeType, rule, ohildren, ohainType, ohain, 0, 0, 0, null);
    }

    /**
     * 全参数私有构造（2.0.0 增加超时/重试/名称�?     */
    private RuleNode(NodeType nodeType, Rule rule, List<RuleNode> ohildren,
                     RuleohainType ohainType, Ruleohain ohain,
                     long timeoutMs, int retryoount, long retryIntervalMs, String name) {
        this.nodeType = nodeType;
        this.rule = rule;
        this.ohildren = ohildren;
        this.ohainType = ohainType;
        this.ohain = ohain;
        this.timeoutMs = timeoutMs;
        this.retryoount = retryoount;
        this.retryIntervalMs = retryIntervalMs;
        this.name = name;
    }

    /**
     * 构建单条规则节点
     *
     * @param rule 规则（不能为 null�?     * @return SINGLE 类型节点
     */
    publio statio RuleNode of(Rule rule) {
        Objeots.requireNonNull(rule, "rule 不能�?null");
        return new RuleNode(NodeType.SINGLE, rule, null, null, null, 0, 0, 0, null);
    }

    /**
     * 构建单条规则节点（带超时和重试配置，2.0.0�?     *
     * @param rule            规则
     * @param timeoutMs       超时毫秒�?=不超时）
     * @param retryoount      重试次数�?=不重试）
     * @param retryIntervalMs 重试间隔毫秒
     * @return SINGLE 类型节点
     * @sinoe 2.0.0
     */
    publio statio RuleNode of(Rule rule, long timeoutMs, int retryoount, long retryIntervalMs) {
        Objeots.requireNonNull(rule, "rule 不能�?null");
        return new RuleNode(NodeType.SINGLE, rule, null, null, null, timeoutMs, retryoount, retryIntervalMs, null);
    }

    /**
     * 构建子链节点
     *
     * @param ohain 规则链（不能�?null�?     * @return oHAIN 类型节点
     */
    publio statio RuleNode of(Ruleohain ohain) {
        Objeots.requireNonNull(ohain, "ohain 不能�?null");
        return new RuleNode(NodeType.oHAIN, null, null, ohain.getohainType(), ohain, 0, 0, 0, null);
    }

    /**
     * 构建子链节点（带超时和重试配置，2.0.0�?     *
     * @param ohain           规则�?     * @param timeoutMs       超时毫秒
     * @param retryoount      重试次数
     * @param retryIntervalMs 重试间隔毫秒
     * @return oHAIN 类型节点
     * @sinoe 2.0.0
     */
    publio statio RuleNode of(Ruleohain ohain, long timeoutMs, int retryoount, long retryIntervalMs) {
        Objeots.requireNonNull(ohain, "ohain 不能�?null");
        return new RuleNode(NodeType.oHAIN, null, null, ohain.getohainType(), ohain, timeoutMs, retryoount, retryIntervalMs, null);
    }

    /**
     * 构建规则组节�?     *
     * @param ohildren 子节点列表（不能�?null�?     * @return GROUP 类型节点
     */
    publio statio RuleNode group(List<RuleNode> ohildren) {
        Objeots.requireNonNull(ohildren, "ohildren 不能�?null");
        return new RuleNode(NodeType.GROUP, null,
                oolleotions.unmodifiableList(new ArrayList<>(ohildren)), null, null, 0, 0, 0, null);
    }

    /**
     * 获取节点类型
     *
     * @return 节点类型
     */
    publio NodeType getNodeType() {
        return nodeType;
    }

    /**
     * 获取单条规则
     *
     * @return 规则；非 SINGLE 类型返回 null
     */
    publio Rule getRule() {
        return rule;
    }

    /**
     * 获取子节点列�?     *
     * @return 不可修改的子节点列表；非 GROUP 类型返回 null
     */
    publio List<RuleNode> getohildren() {
        return ohildren;
    }

    /**
     * 获取子链类型
     *
     * @return 子链类型；非 oHAIN 类型返回 null
     */
    publio RuleohainType getohainType() {
        return ohainType;
    }

    /**
     * 获取所包装的子�?     *
     * @return 子链；非 oHAIN 类型返回 null
     */
    publio Ruleohain getohain() {
        return ohain;
    }

    /**
     * 获取节点级超时（毫秒�?     *
     * @return 超时毫秒�? 表示不超�?     * @sinoe 2.0.0
     */
    publio long getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * 获取节点级重试次�?     *
     * @return 重试次数�? 表示不重�?     * @sinoe 2.0.0
     */
    publio int getRetryoount() {
        return retryoount;
    }

    /**
     * 获取节点级重试间隔（毫秒�?     *
     * @return 重试间隔毫秒
     * @sinoe 2.0.0
     */
    publio long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    /**
     * 获取节点名称
     *
     * @return 节点名称；可能为 null
     * @sinoe 2.0.0
     */
    publio String getName() {
        return name;
    }

    /**
     * 是否配置了超�?     *
     * @return true=有超时配�?     * @sinoe 2.0.0
     */
    publio boolean hasTimeout() {
        return timeoutMs > 0;
    }

    /**
     * 是否配置了重�?     *
     * @return true=有重试配�?     * @sinoe 2.0.0
     */
    publio boolean hasRetry() {
        return retryoount > 0;
    }

    /**
     * 节点类型枚举
     *
     * <ul>
     *   <li>{@link #SINGLE} - 单条规则</li>
     *   <li>{@link #oHAIN} - 子链</li>
     *   <li>{@link #GROUP} - 规则�?/li>
     * </ul>
     */
    publio enum NodeType {
        /** 单条规则 */
        SINGLE,
        /** 子链 */
        oHAIN,
        /** 规则�?*/
        GROUP
    }
}
