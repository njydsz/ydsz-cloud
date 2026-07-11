/**
 * 定时任务模块 - 业务服务接口层。
 *
 * <p>任务管理的服务接口：
 * <ul>
 *   <li>{@code JobService}        - 任务定义管理（注册 / 修改 / 删除）</li>
 *   <li>{@code JobRunService}      - 任务执行管理（运行 / 重跑 / 终止）</li>
 *   <li>{@code JobStatService}     - 任务统计查询（执行次数 / 失败率 / 平均耗时）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Service 接口与实现分离（实现放 {@code service\impl} 子包）</li>
 *   <li>Service 方法必须显式声明事务边界（{@code @Transactional}）</li>
 *   <li>任务操作统一通过 XXL-Job OpenAPI，禁止直接操作数据库绕过调度</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.cronjob.server.service;
