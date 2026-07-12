/**
 * 通用工具类层。
 *
 * <p>集中维护与业务无关的"工具类"：雪花 ID、链路追踪、排序、JSON、密码学、PDF、IP、路径安全等。
 * 所有工具类都使用 {@code final} + 私有构造方法，禁止实例化与继承。
 *
 * <h3>工具清单</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.util.SnowflakeIdGenerator}             - 雪花算法 ID 生成（16/64 位）</li>
 *   <li>{@link com.njydsz.pmis.common.util.PmisSnowflakeIdentifierGenerator} - MyBatis-Plus 集成版雪花 ID</li>
 *   <li>{@link com.njydsz.pmis.common.util.TraceIdUtil}                       - 链路追踪 ID 工具</li>
 *   <li>{@link com.njydsz.pmis.common.util.SortBy}                            - 类型安全 Spring Data Sort 工厂</li>
 *   <li>{@link com.njydsz.pmis.common.util.SerializableFunction}              - 可序列化函数式接口（{@code SortBy} 配套）</li>
 *   <li>{@link com.njydsz.pmis.common.util.JsonUtils}                         - JSON 序列化 / 反序列化</li>
 *   <li>{@link com.njydsz.pmis.common.util.CryptoUtil}                        - 密码学工具（HMAC / AES / SM4）</li>
 *   <li>{@link com.njydsz.pmis.common.util.InternalHeaderSigner}              - 内部头 HMAC 签名（防网关绕过）</li>
 *   <li>{@link com.njydsz.pmis.common.util.IpUtils}                           - IP 解析 / IPv6 兼容 / 内网判断</li>
 *   <li>{@link com.njydsz.pmis.common.util.PathGuard}                         - 路径穿越防护（文件下载场景）</li>
 *   <li>{@link com.njydsz.pmis.common.util.PdfUtil}                           - PDF 生成（基于 OpenPDF / iText）</li>
 *   <li>{@link com.njydsz.pmis.common.util.CursorHelper}                      - 游标分页辅助</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有工具方法 {@code static}，禁止依赖 Spring 容器（避免单测需要启动 Spring）</li>
 *   <li>需要 Spring 容器的工具下沉到 {@code service} 包</li>
 *   <li>线程安全：所有工具方法实现为无状态（{@code static} 方法 + 局部变量）</li>
 *   <li>性能敏感工具（{@code JsonUtils} / {@code CryptoUtil}）内部缓存复用对象</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.util;
