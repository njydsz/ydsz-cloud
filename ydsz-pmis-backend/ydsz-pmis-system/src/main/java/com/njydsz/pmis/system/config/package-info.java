/**
 * 系统配置层：集中托管 MinIO、WebSocket 等基础设施组件的 Spring 配置。
 *
 * <p>本包负责 PMIS 系统中横切关注点的 Bean 装配与参数绑定，所有配置类均使用
 * {@code @Configuration} 标注，并通过 {@code @ConfigurationProperties} 绑定
 * {@code application.yml} 中的对应配置段。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code MinioConfig} - MinIO 对象存储客户端配置，绑定 {@code minio.*} 配置段，
 *       提供 {@code MinioClient} 单例 Bean，支持预签名 URL 生成</li>
 *   <li>{@code WebSocketConfig} - WebSocket + STOMP 消息代理配置，启用简单 broker，
 *       注册 {@code /ws} 端点，配置心跳（10s/10s）与用户私有频道前缀 {@code /user}</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>配置与代码分离</b>：所有可变参数（端点、密钥、超时等）均通过 {@code yml} 注入，
 *       避免硬编码</li>
 *   <li><b>单职责</b>：每个配置类只负责一类基础设施，便于独立测试与替换</li>
 *   <li><b>Bean 命名规范</b>：工厂方法名即 Bean 名（如 {@code minioClient}），
 *       便于按需 {@code @Qualifier} 注入</li>
 *   <li><b>默认安全</b>：关键参数（如 URL 过期秒数）提供合理默认值，避免空配置下崩溃</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增配置类时须在本 {@code package-info.java} 中同步登记</li>
 *   <li>配置属性前缀须与业务语义对齐（如对象存储 → {@code minio.*}）</li>
 *   <li>敏感信息（accessKey/secretKey）禁止写入 Java 常量，统一通过环境变量注入</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.config;
