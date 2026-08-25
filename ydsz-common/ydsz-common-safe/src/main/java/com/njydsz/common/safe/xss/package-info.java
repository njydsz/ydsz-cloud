/**
 * XSS 防护能力。
 *
 * <p>提供跨站脚本攻击（XSS）防护，包括：
 *
 * <ul>
 *   <li>请求参数过滤（{@code XssFilter}）
 *   <li>请求体清洗（{@code XssRequestBodyAdvice}）
 *   <li>JSON 消息转换（{@code XssJsonMessageConverter}）
 *   <li>声明式字段清洗（{@code @Xss} 注解）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.common.safe.xss;
