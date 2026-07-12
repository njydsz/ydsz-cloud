/**
 * 跨模块共享常量层。
 *
 * <p>集中管理所有"魔数 / 魔法字符串"常量化，避免硬编码字面量散落业务代码。
 * 各业务模块（system / userinfo / project / workflow / agent）通过引用本包常量保持一致。
 *
 * <h3>常量分类</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.constant.SystemConstants}      - 系统级常量（审计字段默认值 = "SYSTEM"）</li>
 *   <li>{@link com.njydsz.pmis.common.constant.CommonConstants}      - 公共常量（字符集、Header 名称、状态枚举）</li>
 *   <li>{@link com.njydsz.pmis.common.constant.CacheConstants}       - Spring Cache 名称（与 PmisCacheConfig 一一对应）</li>
 *   <li>{@link com.njydsz.pmis.common.constant.AsyncExecutorNames}   - 异步线程池 Bean 名（审计 / 导出 / Agent）</li>
 *   <li>{@link com.njydsz.pmis.common.constant.PmisMessageTopics}    - RocketMQ Topic / ConsumerGroup 名称</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>常量名全大写 + 下划线分隔</li>
 *   <li>工具类 / 常量类使用 {@code final} + 私有构造方法，禁止实例化</li>
 *   <li>枚举语义常量优先使用 {@code enum}，例如业务状态（{@code ENABLED} / {@code DISABLED}）</li>
 *   <li>新增常量时同步在本包的 JavaDoc 中登记</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.constant;
