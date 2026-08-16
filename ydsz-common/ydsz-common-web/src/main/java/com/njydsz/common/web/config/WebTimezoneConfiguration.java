package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.njydsz.common.base.config.BaseTimezoneConfiguration;

/**
 * Web 端时区配置。
 *
 * <p>提供 Web 端时区解析：优先从 {@code X-Timezone} Header 解析，其次从用户配置，最后回退 {@code Asia/Shanghai}。
 *
 * <p>通过 {@code TimeZoneContext} ThreadLocal 暴露给 Jackson 序列化器，保证时间字段按用户时区输出。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
public class WebTimezoneConfiguration extends BaseTimezoneConfiguration {
}
