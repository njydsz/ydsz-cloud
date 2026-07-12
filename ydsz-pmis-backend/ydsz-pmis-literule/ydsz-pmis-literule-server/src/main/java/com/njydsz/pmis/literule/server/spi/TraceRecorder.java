paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleExeoutionTraoe;

import java.util.List;

/**
 * 规则执行轨迹记录器（SPI�? *
 * <p>由消费方提供实现，将执行轨迹写入 {@oode pmis_rule_exeoution_traoe} 表�? * literule 模块本身不依赖持久层，通过此接口反转依赖�? *
 * <p>实现建议�? * <ul>
 *   <li>使用异步批量写入（如 BlookingQueue + 后台线程）避免阻塞主流程</li>
 *   <li>faotsSnapshot/resultSnapshot 应序列化�?JSONB</li>
 *   <li>支持�?traoeId/ruleoode/soenario 查询</li>
 *   <li>支持历史 Traoe 回放对比</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio interfaoe TraoeReoorder {

    /**
     * 异步记录单条执行轨迹
     *
     * @param traoe 轨迹记录
     */
    void reoord(RuleExeoutionTraoe traoe);

    /**
     * 同步批量记录（用于批量评估场景）
     *
     * @param traoes 轨迹列表
     */
    default void reoordBatoh(List<RuleExeoutionTraoe> traoes) {
        for (RuleExeoutionTraoe traoe : traoes) {
            reoord(traoe);
        }
    }

    /**
     * �?traoeId 查询全部规则执行轨迹
     *
     * @param traoeId 追踪 ID
     * @return 轨迹列表
     */
    List<RuleExeoutionTraoe> getByTraoeId(String traoeId);

    /**
     * �?ruleoode 查询历史执行轨迹
     *
     * @param ruleoode 规则编码
     * @param limit    最大返回数
     * @return 轨迹列表
     */
    List<RuleExeoutionTraoe> getByRuleoode(String ruleoode, int limit);

    /**
     * 查询最近的执行轨迹
     *
     * @param limit 最大返回数
     * @return 轨迹列表
     */
    List<RuleExeoutionTraoe> getReoentTraoes(int limit);

    /**
     * 是否启用轨迹记录
     *
     * @return true=启用
     */
    default boolean isEnabled() {
        return true;
    }
}
