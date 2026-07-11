/**
 * 密钥管理（KMS）层。
 *
 * <p>通过 SPI 方式抽象密钥提供方，业务方通过 {@link com.njydsz.pmis.common.kms.SecretProvider} 接口
 * 获取密钥，无需关心底层实现（环境变量 / Jasypt / 阿里云 KMS / 腾讯云 KMS / HashiCorp Vault）。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.kms.SecretProvider}             - 密钥提供方 SPI</li>
 *   <li>{@link com.njydsz.pmis.common.kms.SecretManager}              - 密钥管理门面（缓存 / 降级 / 监控）</li>
 *   <li>{@link com.njydsz.pmis.common.kms.KmsProperties}              - KMS 配置（provider 选择 / 缓存 TTL）</li>
 *   <li>{@link com.njydsz.pmis.common.kms.EnvironmentSecretProvider}  - 环境变量实现（本地 / 容器部署）</li>
 *   <li>{@link com.njydsz.pmis.common.kms.JasyptSecretProvider}        - Jasypt 加密属性实现（传统 Nacos 配置加密）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止在业务代码中硬编码密钥（数据库密码 / 第三方 AppSecret 等）</li>
 *   <li>密钥轮转通过 KMS 平台完成，业务侧无需修改代码</li>
 *   <li>密钥访问失败时记录告警（但不应阻塞主流程）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.kms;
