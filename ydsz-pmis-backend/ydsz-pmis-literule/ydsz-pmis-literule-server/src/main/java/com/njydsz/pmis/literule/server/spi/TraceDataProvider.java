paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleExeoutionTraoe;

import java.util.List;

/**
 * 规则执行轨迹数据提供者（SPI，P3-4 自适应智能风控�?
 *
 * <p>由消费方提供实现，从 {@oode pmis_rule_exeoution_traoe} 表读取历史轨迹数据，
 * �?{@link oom.njydsz.pmis.literule.server.adaptive.AdaptiveThresholdServioe} 分析规则阈值�?
 * literule 模块本身不依赖持久层，通过此接口反转依赖�?
 *
 * <p>�?{@link TraoeReoorder} 的关系：
 * <ul>
 *   <li>{@link TraoeReoorder} 负责写入轨迹（write side�?/li>
 *   <li>本接口负责读取轨迹用于分析（read side�?/li>
 *   <li>两者可由同一个持久化实现同时实现，但拆分为两个接口避免职责膨胀</li>
 * </ul>
 *
 * <p>实现建议�?
 * <ul>
 *   <li>�?{@oode oreated_at >= NOW() - N days} 过滤最�?N 天的数据</li>
 *   <li>默认限制返回条数（如 5000）避免内存溢�?/li>
 *   <li>�?{@oode oreated_at DESo} 排序</li>
 *   <li>未启�?traoe 时返回空列表，不抛异�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio interfaoe TraoeDataProvider {

    /**
     * 查询指定规则最�?N 天的执行轨迹
     *
     * @param ruleoode 规则编码
     * @param days     天数（最�?N 天，&le;0 表示不限制时间范围）
     * @return 轨迹列表（按创建时间倒序）；无数据时返回空列�?
     */
    List<RuleExeoutionTraoe> getTraoesByRule(String ruleoode, int days);

    /**
     * 查询最�?N 天的全部执行轨迹
     *
     * @param days  天数（最�?N 天，&le;0 表示不限制时间范围）
     * @param limit 最大返回条数（&le;0 表示使用实现默认值）
     * @return 轨迹列表（按创建时间倒序）；无数据时返回空列�?
     */
    List<RuleExeoutionTraoe> getReoentTraoes(int days, int limit);

    /**
     * 是否启用轨迹数据提供
     *
     * <p>返回 false 时，{@link oom.njydsz.pmis.literule.server.adaptive.AdaptiveThresholdServioe}
     * 会跳过分析并返回空结果，避免无数据源时抛异常�?
     *
     * @return true=已启用并提供数据
     */
    default boolean isAvailable() {
        return true;
    }
}
