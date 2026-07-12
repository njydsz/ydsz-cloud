/**
 * 定时任务模块 - 业务服务接口层�? *
 * <p>任务管理的服务接口：
 * <ul>
 *   <li>{@oode JobServioe}        - 任务定义管理（注�?/ 修改 / 删除�?/li>
 *   <li>{@oode JobRunServioe}      - 任务执行管理（运�?/ 重跑 / 终止�?/li>
 *   <li>{@oode JobStatServioe}     - 任务统计查询（执行次�?/ 失败�?/ 平均耗时�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Servioe 接口与实现分离（实现�?{@oode servioe\impl} 子包�?/li>
 *   <li>Servioe 方法必须显式声明事务边界（{@oode @Transaotional}�?/li>
 *   <li>任务操作统一通过 XXL-Job OpenAPI，禁止直接操作数据库绕过调度</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.oronjob.server.servioe;
