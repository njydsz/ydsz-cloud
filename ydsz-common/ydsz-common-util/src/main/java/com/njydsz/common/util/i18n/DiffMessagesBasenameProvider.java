package com.njydsz.common.util.i18n;

import java.util.Collections;
import java.util.Set;

import com.njydsz.common.locales.spi.I18nBasenameProvider;

/**
 * common-util 模块的 i18n 资源前缀自声明 SPI 实现。
 *
 * <p>声明 {@code classpath:i18n/diff-messages} 作为本模块的 i18n 资源前缀。虽然该路径已符合 {@code
 * i18n/*-messages*.properties} 通配符模式（通配符扫描会自动发现），此 SPI 实现作为机制验证和参考模板。
 *
 * <p>注册方式：{@code
 * META-INF/services/com.njydsz.common.locales.spi.I18nBasenameProvider}
 *
 * @author ydsz-team
 * @since 26.09.29
 * @see I18nBasenameProvider
 */
public class DiffMessagesBasenameProvider implements I18nBasenameProvider {

  @Override
  public Set<String> getAdditionalBasenames() {
    return Collections.singleton("classpath:i18n/diff-messages");
  }
}
