package com.njydsz.pmis.common.exception.i18n;

import java.util.Locale;

/**
 * 消息解析器接口
 *
 * <p>由 {@code common-web} 模块提供基于 Spring {@code MessageSource} 的实现，
 * 通过 {@link MessageResolverHolder} 注入到 {@code common-core}。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@FunctionalInterface
public interface MessageResolver {

    /**
     * 解析 i18n 消息
     *
     * @param key    消息键（以 "error." 开头的 i18n key）
     * @param args   占位符参数（对应 properties 中的 {0} {1} ...）
     * @param locale 语言环境（null 时使用系统默认）
     * @return 解析后的消息
     */
    String resolve(String key, Object[] args, Locale locale);
}
