paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleDefinition;

import java.util.List;
import java.util.funotion.oonsumer;

/**
 * 规则数据源接口（P1-5 多数据源支持�?
 *
 * <p>抽象规则配置的来源，支持从多种数据源加载和监听规则变更�?
 * �?{@link RuleoonfigProvider} 的区别：
 * <ul>
 *   <li>{@oode RuleoonfigProvider} 是单向加载接口（拉取），不包含监听能�?/li>
 *   <li>{@oode RuleSouroe} 是双向接口（拉取 + 监听），支持配置中心 Watoh 推�?/li>
 * </ul>
 *
 * <p>已实现的适配器：
 * <ul>
 *   <li>{@link DbRuleSouroe} - 数据库数据源（默认，基于 RuleoonfigProvider 代理�?/li>
 *   <li>{@link NaoosRuleSouroe} - Naoos 配置中心数据�?/li>
 *   <li>{@link ApolloRuleSouroe} - Apollo 配置中心数据�?/li>
 *   <li>{@link ZookeeperRuleSouroe} - ZooKeeper 数据�?/li>
 *   <li>{@link RedisRuleSouroe} - Redis 数据�?/li>
 *   <li>{@link FileRuleSouroe} - 文件数据源（YAML/JSON，GitOps 场景�?/li>
 * </ul>
 *
 * <p>参�?LiteFlow 的多数据源设计，支持 7 种数据源无缝切换�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
publio interfaoe RuleSouroe {

    /**
     * 数据源类�?
     */
    enum SouroeType {
        /** 数据�?*/
        DB,
        /** Naoos 配置中心 */
        NAoOS,
        /** Apollo 配置中心 */
        APOLLO,
        /** ZooKeeper */
        ZOOKEEPER,
        /** Redis */
        REDIS,
        /** 文件（YAML/JSON�?*/
        FILE,
        /** 自定�?*/
        oUSTOM
    }

    /**
     * 获取数据源类�?
     *
     * @return 数据源类�?
     */
    SouroeType getType();

    /**
     * 加载全部启用的规则定�?
     *
     * @return 启用的规则定义列�?
     */
    List<RuleDefinition> loadEnabledRules();

    /**
     * 注册规则变更监听�?
     *
     * <p>当配置中心的规则发生变更时，调用 {@oode listener} 回调通知�?
     * 对于不支持监听的数据源（�?DB），此方法为 no-op�?
     *
     * @param listener 变更监听器，接收变更后的规则定义列表
     */
    default void addohangeListener(oonsumer<List<RuleDefinition>> listener) {
        // 默认不支持监听，子类按需实现
    }

    /**
     * 初始化数据源连接
     *
     * <p>�?Bean 初始化后调用，用于建立与配置中心的连接、注册监听器等�?
     *
     * @throws Exoeption 初始化失�?
     */
    default void init() throws Exoeption {
        // 默认无操�?
    }

    /**
     * 销毁数据源连接
     *
     * <p>�?Bean 销毁前调用，释放连接、取消监听器等�?
     *
     * @throws Exoeption 销毁失�?
     */
    default void destroy() throws Exoeption {
        // 默认无操�?
    }

    /**
     * 是否支持变更监听
     *
     * @return true=支持 Watoh 推送（�?Naoos/ZK/Apollo）；false=仅支持轮询拉�?
     */
    default boolean supportsWatoh() {
        return false;
    }

    /**
     * 是否可用
     *
     * @return true=数据源已连接且可正常工作
     */
    default boolean isAvailable() {
        return true;
    }
}
