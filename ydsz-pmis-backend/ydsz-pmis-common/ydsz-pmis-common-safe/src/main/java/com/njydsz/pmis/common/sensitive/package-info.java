/**
 * 敏感数据脱敏 / 加密层。
 *
 * <p>实现 7 种敏感数据脱敏策略（{@code NAME} / {@code ID_CARD} / {@code PHONE} / {@code EMAIL} /
 * {@code BANK_CARD} / {@code ADDRESS} / {@code CUSTOM}）与可逆加密字段序列化器。
 * 业务实体通过 {@link com.njydsz.pmis.common.sensitive.Sensitive} 注解自动在序列化（响应给前端）时
 * 脱敏，通过 {@link com.njydsz.pmis.common.sensitive.EncryptedField} 注解自动在持久化时加密。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.sensitive.Sensitive}             - 脱敏注解</li>
 *   <li>{@link com.njydsz.pmis.common.sensitive.SensitiveStrategy}     - 脱敏策略枚举</li>
 *   <li>{@link com.njydsz.pmis.common.sensitive.SensitiveSerializer}   - 脱敏序列化器（Jackson）</li>
 *   <li>{@link com.njydsz.pmis.common.sensitive.SensitiveUtil}         - 脱敏工具类（程序内部调用）</li>
 *   <li>{@link com.njydsz.pmis.common.sensitive.EncryptedField}        - 字段加密注解</li>
 *   <li>{@link com.njydsz.pmis.common.sensitive.EncryptedFieldSerializer} - 字段加密序列化器（持久化）</li>
 *   <li>{@link com.njydsz.pmis.common.sensitive.EncryptedFieldKeyRegistry} - 字段密钥注册中心（按字段名分发密钥）</li>
 * </ul>
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>密钥不得硬编码，统一通过 {@code SecretManager} 获取</li>
 *   <li>加密字段必须建立索引时使用加密后的密文索引，不在原字段上加索引</li>
 *   <li>脱敏只对 API 响应生效，内部 RPC / MQ 消息不脱敏</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.sensitive;
