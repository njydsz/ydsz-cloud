package com.remisoft.common.util.password;

import java.util.Locale;

/**
 * 密码强度校验 SPI（Service Provider Interface）。
 *
 * <p>通过 JDK {@link java.util.ServiceLoader} 机制支持业务方自定义密码强度规则，
 * 替代 {@link PwdUtils#checkPasswordStrength(String)} 内置的简单评分逻辑。
 *
 * <p><b>典型扩展场景：</b>
 * <ul>
 *   <li>校验密码不在常见弱密码字典中（如 123456、password）</li>
 *   <li>校验密码不含用户名/手机号等个人信息</li>
 *   <li>校验密码不含连续字符（如 123、abc）或重复字符（如 aaa）</li>
 *   <li>NIST SP 800-63B / 等保 2.0 / OWASP ASVS 等合规规则</li>
 * </ul>
 *
 * <p><b>接入方式：</b>
 * 在 {@code META-INF/services/com.remisoft.common.util.password.PasswordStrengthChecker}
 * 文件中填写实现类的全限定名（每行一个）。Utils 模块默认提供 {@link DefaultPasswordStrengthChecker}，
 * 业务方提供自己的实现后会自动覆盖默认实现。
 *
 * <p><b>实现约定：</b>
 * <ul>
 *   <li>实现类必须提供 public 无参构造器（ServiceLoader 要求）</li>
 *   <li>实现必须线程安全（无实例变量或使用 ThreadLocal）</li>
 *   <li>入参 password 可能为 null，需 null-safe 处理</li>
 * </ul>
 *
 * <pre>{@code
 * // 1. 实现自定义校验器
 * public class MyPasswordChecker implements PasswordStrengthChecker {
 *     &#64;Override
 *     public PasswordStrengthLevel check(String password) { ... }
 *
 *     &#64;Override
 *     public String describe(PasswordStrengthLevel level, Locale locale) { ... }
 * }
 *
 * // 2. 注册服务（src/main/resources/META-INF/services/...password.PasswordStrengthChecker 文件）
 * com.example.MyPasswordChecker
 *
 * // 3. 使用（自动发现自定义实现）
 * PasswordStrengthLevel level = PwdUtils.checkPasswordStrength("abc123");
 * }</pre>
 *
 * @author remi-team
 * @since 1.3.0
 * @see DefaultPasswordStrengthChecker
 */
public interface PasswordStrengthChecker {

    /**
     * 校验密码强度。
     *
     * @param password 明文密码，可能为 null
     * @return 密码强度等级（非 null）
     */
    PasswordStrengthLevel check(String password);

    /**
     * 将密码强度等级转为用户可读的本地化描述。
     *
     * <p>用于前端展示或错误消息国际化。
     *
     * @param level  密码强度等级
     * @param locale 目标语言区域，不可为 null
     * @return 本地化描述字符串（如 "密码强度弱" / "Password is weak"）
     */
    String describe(PasswordStrengthLevel level, Locale locale);

    /**
     * 校验失败时的建议提示。
     *
     * <p>用于指导用户增强密码强度（如 "建议包含大小写字母、数字和特殊字符"）。
     *
     * @param password 明文密码（用于诊断具体不足）
     * @param locale   目标语言区域
     * @return 改进建议，无建议时返回空字符串或 null
     */
    String suggest(String password, Locale locale);

    /**
     * 密码强度等级枚举。
     *
     * <p>与原 {@link PwdUtils.PasswordStrength} 语义一致但独立定义，
     * 便于 SPI 演进时互不影响。
     */
    enum PasswordStrengthLevel {
        /** 极弱（低于最低要求，不可接受） */
        VERY_WEAK,
        /** 弱 */
        WEAK,
        /** 中等 */
        MEDIUM,
        /** 强 */
        STRONG,
        /** 极强 */
        VERY_STRONG
    }
}
