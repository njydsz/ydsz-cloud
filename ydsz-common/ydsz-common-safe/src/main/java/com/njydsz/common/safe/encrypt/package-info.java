/**
 * 字段级加密能力。
 *
 * <p>提供声明式字段级加密，通过 {@code @EncryptField} 注解 + {@code EncryptTypeHandler} 实现自动入库加密、出库解密。
 *
 * <p><b>注意：</b>{@code @EncryptField} 使用 AES-256-GCM 随机 IV，加密字段不可用于 WHERE/LIKE 条件查询。
 * 需要查询的字段（如手机号、邮箱）不建议加密，或采用应用层哈希索引方案。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.common.safe.encrypt;
