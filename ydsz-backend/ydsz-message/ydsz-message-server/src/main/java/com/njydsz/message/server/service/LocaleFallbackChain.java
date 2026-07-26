package com.njydsz.message.server.service.core;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 多语言回退链（P1-6）。
 *
 * <p>模板加载时按 locale 回退链查找：用户偏好 locale → 租户默认 locale → 系统默认 zh-CN。
 * 例如用户偏好 en-US 时回退链为: en-US → zh-CN。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class LocaleFallbackChain {

    /** 系统默认语言 */
    public static final String DEFAULT_LOCALE = "zh-CN";

    /**
     * 构建 locale 回退链。
     *
     * @param preferredLocale 用户偏好语言（可为 null）
     * @return 回退链列表（优先级从高到低，至少包含 zh-CN）
     */
    public List<String> buildFallbackChain(String preferredLocale) {
        List<String> chain = new ArrayList<>();
        if (StringUtils.hasText(preferredLocale)) {
            String locale = preferredLocale.trim();
            chain.add(locale);
            // 如果是 zh-TW / zh-HK 等,回退到 zh-CN
            if (locale.toLowerCase().startsWith("zh") && !locale.equalsIgnoreCase(DEFAULT_LOCALE)) {
                chain.add(DEFAULT_LOCALE);
            }
            // 如果是 en-US / en-GB 等,回退到 en,再到 zh-CN
            if (locale.toLowerCase().startsWith("en") && !locale.equalsIgnoreCase("en")) {
                chain.add("en");
            }
        }
        if (!chain.contains(DEFAULT_LOCALE)) {
            chain.add(DEFAULT_LOCALE);
        }
        return chain;
    }
}
