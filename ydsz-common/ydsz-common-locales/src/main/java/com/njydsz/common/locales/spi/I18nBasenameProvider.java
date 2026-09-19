package com.njydsz.common.locales.spi;

import java.util.Collections;
import java.util.Set;

/**
 * i18n 资源前缀自声明 SPI（L2 基础设施接口）。
 *
 * <p>业务模块实现此接口，通过 {@link java.util.ServiceLoader} 机制在启动时声明自身资源前缀。适用于以下场景：
 *
 * <ul>
 *   <li>资源文件路径不符合 {@code classpath:i18n/*-messages*.properties} 通配符约定（如 {@code
 *       com/njydsz/common/util/password/password-messages}）
 *   <li>动态计算或条件性声明 basename（如根据环境 profile 决定是否加载某资源文件）
 * </ul>
 *
 * <p>对于符合 {@code classpath:i18n/*-messages*.properties} 标准约定的资源，通配符自动扫描（{@link
 * com.njydsz.common.locales.config.I18nProperties#isWildcardScanEnabled()}）即可发现，无需实现此 SPI。
 *
 * <p><b>服务注册：</b>实现类需在 {@code META-INF/services/com.njydsz.common.locales.spi.I18nBasenameProvider}
 * 文件中列出自身全限定名（标准 {@link java.util.ServiceLoader} 约定）。
 *
 * <p><b>典型实现：</b>
 *
 * <pre>{@code
 * // ydsz-foo 模块
 * package com.njydsz.foo.i18n;
 *
 * public class FooBasenameProvider implements I18nBasenameProvider {
 *     {@literal @}Override
 *     public Set<String> getAdditionalBasenames() {
 *         return Set.of("classpath:com/njydsz/foo/internal/foo-messages");
 *     }
 * }
 *
 * // META-INF/services/com.njydsz.common.locales.spi.I18nBasenameProvider 文件内容：
 * // com.njydsz.foo.i18n.FooBasenameProvider
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see com.njydsz.common.locales.config.I18nProperties#getEffectiveBasenames()
 */
public interface I18nBasenameProvider {

  /**
   * 返回本模块需要追加的 i18n 资源前缀集合。
   *
   * <p>返回值应为标准 Spring Resource 路径格式（如 {@code classpath:i18n/foo-messages}），不含 locale 后缀和
   * .properties 扩展名（{@link org.springframework.context.support.ReloadableResourceBundleMessageSource}
   * 会自动拼接 locale 和扩展名）。
   *
   * <p>实现类被 {@link java.util.ServiceLoader} 加载时仅调用一次（启动时），非频繁调用场景，可安全做 IO 或反射操作。
   *
   * @return 本模块额外声明的 basename 集合；无额外声明时返回 {@link Collections#emptySet()}，禁止返回 null
   */
  Set<String> getAdditionalBasenames();
}
