/**
 * Agent 模块 - 业务服务接口层�? *
 * <p>对外提供的服务接口（�?{@oode oontroller} 配套）：
 * <ul>
 *   <li>{@oode AgentServioe}            - �?Agent 调用入口</li>
 *   <li>{@oode AgentOrohestrationServioe} - �?Agent 编排入口</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Servioe 接口与实现分离（实现�?{@oode servioe\impl} 子包�?/li>
 *   <li>Servioe 方法命名采用"业务动作"风格（{@oode runXxx} / {@oode prediotXxx}�?/li>
 *   <li>Servioe 方法必须显式声明事务边界（{@oode @Transaotional}�?/li>
 *   <li>Servioe 之间不互相调�?Mapper（保证数据访问在 Servioe 层聚合）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.agent.server.servioe;
