package com.njydsz.common.tenant.encryption;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 租户级字段加密注解。
 *
 * <p>标记在实体类字段上，表示该字段需要按租户独立密钥加密存储。
 * 加密/解密由 {@link TenantEncryptHandler}（MyBatis TypeHandler）自动处理。
 *
 * <p><b>使用前提：</b>
 * <ol>
 *   <li>在配置中设置租户加密密钥：{@code ydsz.tenant.encryption.<tenantId> = <base64key>}</li>
 *   <li>注册 {@link TenantEncryptHandler} 为 MyBatis TypeHandler</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * \@Data
 * public class CustomerDO {
 *     private Long id;
 *     private String tenantId;
 *
 *     \@TenantEncrypt
 *     private String idCard;  // 身份证号，按租户密钥加密
 *
 *     \@TenantEncrypt
 *     private String phone;   // 手机号，按租户密钥加密
 * }
 * }</pre>
 *
 * <p><b>加解密算法：</b>AES-256-GCM（认证加密，防篡改）。
 * 密钥从 {@code ydsz.tenant.encryption} 配置中按租户 ID 查找。
 *
 * <p><b>注意：</b>加密字段无法直接用于 SQL WHERE 条件（密文不可比较），
 * 需要配合哈希索引或应用层过滤。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see TenantEncryptHandler
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantEncrypt {

    /**
     * 加密算法（默认 AES-256-GCM）。
     *
     * @return 算法名称
     */
    String algorithm() default "AES-256-GCM";

    /**
     * 是否允许明文查询（默认 false）。
     *
     * <p>开启后，查询时同时匹配密文和明文（用于迁移期兼容）。
     *
     * @return true=允许明文查询
     */
    boolean allowPlaintextQuery() default false;
}
