/**
 * 敏感数据处理能力。
 *
 * <p>提供敏感数据的扫描、脱敏、加密等统一处理能力：
 *
 * <ul>
 *   <li>PII 自由文本扫描（身份证、手机号、银行卡、邮箱、护照）
 *   <li>字段级脱敏（{@code @Sensitive} 注解 + {@code SensitiveDataSerializer}）
 *   <li>数据库列级脱敏（{@code ColumnDesensitizationExecutor}）
 * </ul>
 *
 * <p>所有需要从自由文本中自动发现 PII 的场景应使用 {@link com.njydsz.common.safe.sensitive.SensitiveUtil}，
 * 避免各模块自行维护正则导致升级遗漏。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.common.safe.sensitive;
