package com.njydsz.literule.server.orchestrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.njydsz.literule.api.Rule;

/**
 * 编排节点抽象
 *
 * <p>规则编排树的最小单元，可以是单条规则、规则组或子链。
 * 通过 {@link NodeType} 区分节点形态：
 * <ul>
 *   <li>{@link NodeType#SINGLE} - 单条规则节点，包装一条 {@link Rule}</li>
 *   <li>{@link NodeType#CHAIN} - 子链节点，嵌套一条 {@link RuleChain}（支持 THEN/WHEN/IF/SWITCH 组合）</li>
 *   <li>{@link NodeType#GROUP} - 规则组节点，包装多个子节点构成的列表</li>
 * </ul>
 *
 * <p>使用静态工厂方法 {@link #of(Rule)} 与 {@link #of(RuleChain)} 构建节点，
 * 保证节点形态与字段填充的一致性。
 *
 * @since 1.2.0
 */
public class RuleNode {

    /** 节点类型 */
    private final NodeType nodeType;

    /** 单条规则（仅当 nodeType = SINGLE 时有效） */
    private final Rule rule;

    /** 子节点列表（仅当 nodeType = GROUP 时有效） */
    private final List<RuleNode> children;

    /** 子链类型（仅当 nodeType = CHAIN 时有效，对应所包装 RuleChain 的类型） */
    private final RuleChainType chainType;

    /** 所包装的子链（仅当 nodeType = CHAIN 时有效） */
    private final RuleChain chain;

    /** 节点级超时（毫秒，0=不超时，2.0.0） */
    private final long timeoutMs;

    /** 节点级重试次数（0=不重试，2.0.0） */
    private final int retryCount;

    /** 节点级重试间隔（毫秒，2.0.0） */
    private final long retryIntervalMs;

    /** 节点名称（用于日志和调试，可选） */
    private final String name;

    /**
     * 私有构造，统一通过工厂方法创建
     *
     * @param nodeType  节点类型
     * @param rule      单条规则
     * @param children  子节点列表
     * @param chainType 子链类型
     * @param chain     子链
     */
    private RuleNode(NodeType nodeType, Rule rule, List<RuleNode> children,
                     RuleChainType chainType, RuleChain chain) {
        this(nodeType, rule, children, chainType, chain, 0, 0, 0, null);
    }

    /**
     * 全参数私有构造（2.0.0 增加超时/重试/名称）
     */
    private RuleNode(NodeType nodeType, Rule rule, List<RuleNode> children,
                     RuleChainType chainType, RuleChain chain,
                     long timeoutMs, int retryCount, long retryIntervalMs, String name) {
        this.nodeType = nodeType;
        this.rule = rule;
        this.children = children;
        this.chainType = chainType;
        this.chain = chain;
        this.timeoutMs = timeoutMs;
        this.retryCount = retryCount;
        this.retryIntervalMs = retryIntervalMs;
        this.name = name;
    }

    /**
     * 构建单条规则节点
     *
     * @param rule 规则（不能为 null）
     * @return SINGLE 类型节点
     */
    public static RuleNode of(Rule rule) {
        Objects.requireNonNull(rule, "rule 不能为 null");
        return new RuleNode(NodeType.SINGLE, rule, null, null, null, 0, 0, 0, null);
    }

    /**
     * 构建单条规则节点（带超时和重试配置，2.0.0）
     *
     * @param rule            规则
     * @param timeoutMs       超时毫秒（0=不超时）
     * @param retryCount      重试次数（0=不重试）
     * @param retryIntervalMs 重试间隔毫秒
     * @return SINGLE 类型节点
     * @since 2.0.0
     */
    public static RuleNode of(Rule rule, long timeoutMs, int retryCount, long retryIntervalMs) {
        Objects.requireNonNull(rule, "rule 不能为 null");
        return new RuleNode(NodeType.SINGLE, rule, null, null, null, timeoutMs, retryCount, retryIntervalMs, null);
    }

    /**
     * 构建子链节点
     *
     * @param chain 规则链（不能为 null）
     * @return CHAIN 类型节点
     */
    public static RuleNode of(RuleChain chain) {
        Objects.requireNonNull(chain, "chain 不能为 null");
        return new RuleNode(NodeType.CHAIN, null, null, chain.getChainType(), chain, 0, 0, 0, null);
    }

    /**
     * 构建子链节点（带超时和重试配置，2.0.0）
     *
     * @param chain           规则链
     * @param timeoutMs       超时毫秒
     * @param retryCount      重试次数
     * @param retryIntervalMs 重试间隔毫秒
     * @return CHAIN 类型节点
     * @since 2.0.0
     */
    public static RuleNode of(RuleChain chain, long timeoutMs, int retryCount, long retryIntervalMs) {
        Objects.requireNonNull(chain, "chain 不能为 null");
        return new RuleNode(NodeType.CHAIN, null, null, chain.getChainType(), chain, timeoutMs, retryCount, retryIntervalMs, null);
    }

    /**
     * 构建规则组节点
     *
     * @param children 子节点列表（不能为 null）
     * @return GROUP 类型节点
     */
    public static RuleNode group(List<RuleNode> children) {
        Objects.requireNonNull(children, "children 不能为 null");
        return new RuleNode(NodeType.GROUP, null,
                Collections.unmodifiableList(new ArrayList<>(children)), null, null, 0, 0, 0, null);
    }

    /**
     * 获取节点类型
     *
     * @return 节点类型
     */
    public NodeType getNodeType() {
        return nodeType;
    }

    /**
     * 获取单条规则
     *
     * @return 规则；非 SINGLE 类型返回 null
     */
    public Rule getRule() {
        return rule;
    }

    /**
     * 获取子节点列表
     *
     * @return 不可修改的子节点列表；非 GROUP 类型返回 null
     */
    public List<RuleNode> getChildren() {
        return children;
    }

    /**
     * 获取子链类型
     *
     * @return 子链类型；非 CHAIN 类型返回 null
     */
    public RuleChainType getChainType() {
        return chainType;
    }

    /**
     * 获取所包装的子链
     *
     * @return 子链；非 CHAIN 类型返回 null
     */
    public RuleChain getChain() {
        return chain;
    }

    /**
     * 获取节点级超时（毫秒）
     *
     * @return 超时毫秒；0 表示不超时
     * @since 2.0.0
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * 获取节点级重试次数
     *
     * @return 重试次数；0 表示不重试
     * @since 2.0.0
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * 获取节点级重试间隔（毫秒）
     *
     * @return 重试间隔毫秒
     * @since 2.0.0
     */
    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    /**
     * 获取节点名称
     *
     * @return 节点名称；可能为 null
     * @since 2.0.0
     */
    public String getName() {
        return name;
    }

    /**
     * 是否配置了超时
     *
     * @return true=有超时配置
     * @since 2.0.0
     */
    public boolean hasTimeout() {
        return timeoutMs > 0;
    }

    /**
     * 是否配置了重试
     *
     * @return true=有重试配置
     * @since 2.0.0
     */
    public boolean hasRetry() {
        return retryCount > 0;
    }

    /**
     * 节点类型枚举
     *
     * <ul>
     *   <li>{@link #SINGLE} - 单条规则</li>
     *   <li>{@link #CHAIN} - 子链</li>
     *   <li>{@link #GROUP} - 规则组</li>
     * </ul>
     */
    public enum NodeType {
        /** 单条规则 */
        SINGLE,
        /** 子链 */
        CHAIN,
        /** 规则组 */
        GROUP
    }
}
