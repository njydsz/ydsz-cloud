package com.njydsz.literule.server.spi;

import java.util.Map;

import com.njydsz.literule.api.RuleContext;

/**
 * 事实数据提供者 SPI（P0-2 动态事实采集管道）
 *
 * <p>实现本接口可从外部数据源（DB、Redis、HTTP API、消息队列等）动态采集事实数据，
 * 注入到 {@link RuleContext} 中供规则表达式引用。与 {@link com.njydsz.literule.domain.model.ModelInputProvider}
 * 的区别：
 * <ul>
 *   <li>{@code FactProvider} 采集业务事实数据（如项目预算、进度、合同金额等），直接合并到 facts 中</li>
 *   <li>{@code ModelInputProvider} 采集 ML 模型输出（如风控评分），以 {@code model.xxx} 前缀注入</li>
 * </ul>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>规则评估时需要查询数据库获取项目预算执行率、SPI/CPI 等指标</li>
 *   <li>从 Redis 缓存中获取实时统计数据（如当日预警次数、资源使用率）</li>
 *   <li>调用外部 HTTP API 获取第三方系统数据（如天气、汇率、市场行情）</li>
 *   <li>从消息队列消费的事件中提取事实数据</li>
 * </ul>
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>实现必须是线程安全的（多线程并发调用 {@link #getFacts}）</li>
 *   <li>单次调用应在配置的超时时间内完成（默认 200ms），超时将被丢弃</li>
 *   <li>抛出的异常将被 {@link FactProviderRegistry} 捕获，不影响其他 provider</li>
 *   <li>返回 {@code null} 或空 Map 视为该 provider 无数据</li>
 *   <li>同名字段后者覆盖前者（按 {@link #getOrder} 优先级，数字小的先执行）</li>
 * </ul>
 *
 * <h3>注册方式</h3>
 * <ul>
 *   <li>Spring Bean：实现类标注 {@code @Component}，Spring 自动注入到 {@link FactProviderRegistry}</li>
 *   <li>手动注册：调用 {@link FactProviderRegistry#register}</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>
 * {@literal @Component}
 * public class ProjectBudgetFactProvider implements FactProvider {
 *     {@literal @Autowired}
 *     private ProjectMapper projectMapper;
 *
 *     {@literal @Override}
 *     public Map<String, Object> getFacts(RuleContext context) {
 *         String projectId = (String) context.get("projectId");
 *         ProjectBudget budget = projectMapper.selectBudget(projectId);
 *         Map<String, Object> facts = new HashMap<>();
 *         facts.put("budgetUsedRatio", budget.getUsedRatio());
 *         facts.put("budgetTotal", budget.getTotal());
 *         facts.put("budgetRemaining", budget.getRemaining());
 *         return facts;
 *     }
 *
 *     {@literal @Override}
 *     public String getProviderId() {
 *         return "project-budget";
 *     }
 * }
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface FactProvider {

    /**
     * 根据规则上下文动态采集事实数据
     *
     * <p>实现方可从 {@link RuleContext#getFacts()} 中读取已有的上下文数据（如 projectId、tenantId），
     * 查询外部数据源，返回需要补充的事实数据。返回的 Map 将直接合并到 facts 中。
     *
     * @param context 规则上下文（含已有 facts）
     * @return 事实数据 Map；null 或空 Map 表示无数据
     */
    Map<String, Object> getFacts(RuleContext context);

    /**
     * 提供者标识（如 "project-budget"、"redis-stats"）
     *
     * <p>用于：
     * <ul>
     *   <li>日志与监控中区分不同数据源的调用情况</li>
     *   <li>通过 {@link FactProviderRegistry#getFacts(String, RuleContext)} 定点获取</li>
     * </ul>
     *
     * @return 提供者标识；全局唯一
     */
    String getProviderId();

    /**
     * 是否启用（可选，默认 true）
     *
     * <p>返回 false 时，{@link FactProviderRegistry} 将跳过该 provider。
     * 用于运行时灰度控制或临时禁用某数据源。
     *
     * @return true=启用；false=禁用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 执行优先级（可选，默认 0）
     *
     * <p>数字小的先执行，其返回的 facts 可以被后续 provider 读取。
     * 例如：DB provider (order=0) 先查询基础数据，HTTP provider (order=10)
     * 基于 DB 数据查询外部 API 补充信息。
     *
     * @return 优先级；默认 0
     */
    default int getOrder() {
        return 0;
    }
}
