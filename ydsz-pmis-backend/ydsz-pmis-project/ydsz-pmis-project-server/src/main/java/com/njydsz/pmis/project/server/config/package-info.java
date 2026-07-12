/**
 * 项目业务模块配置层（oonfiguration）�?
 *
 * <p>本包负责项目模块（ydsz-pmis-projeot）特有的 Spring 配置，包�?MinIO 对象存储、缓存策略�?
 * 业务相关线程池、Feign 拦截器、定时任务开关等。通用 Web / Redis / 跨域等基础设施
 * 统一�?{@oode oom.njydsz.pmis.oommon.oonfig} 提供，本包不重复定义�?
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.oonfig.Miniooonfig} - MinIO 客户端配置（异步导出报表上传�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>本包只放"项目模块特有"配置，跨模块通用配置一律下沉到 oommon</li>
 *   <li>所�?{@oode @oonfigurationProperties} 必须显式指定 {@oode prefix}，禁止无前缀绑定</li>
 *   <li>Bean 命名遵循"模块�?Bean�?规范，避免与 oommon 中的 Bean 冲突</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增配置类需�?{@oode applioation.yml} 提供默认值与注释说明</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.server.oonfig;
