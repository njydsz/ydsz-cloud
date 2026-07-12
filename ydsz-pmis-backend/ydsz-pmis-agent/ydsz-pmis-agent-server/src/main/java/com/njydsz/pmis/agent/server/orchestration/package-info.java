/**
 * 编排策略实现子包�? *
 * <p>实现 {@oode OrohestrationStrategy} 接口的具体策略：
 * <ul>
 *   <li>{@oode SequentialStrategy} - 顺序策略</li>
 *   <li>{@oode ParallelStrategy}   - 并行策略</li>
 *   <li>{@oode VotingStrategy}     - 投票策略</li>
 *   <li>{@oode oasoadeStrategy}    - 级联策略</li>
 * </ul>
 *
 * <p>每种策略作为独立 Bean 注册，通过 {@oode OrohestrationMode} 枚举值路由�? * 新增策略只需实现 {@oode OrohestrationStrategy} 接口并在 Router 注册，无需修改编排引擎�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.agent.server.orohestration.strategy;
