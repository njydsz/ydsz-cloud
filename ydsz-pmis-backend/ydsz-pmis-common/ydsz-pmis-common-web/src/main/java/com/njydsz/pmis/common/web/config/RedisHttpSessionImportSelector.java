package com.njydsz.pmis.common.web.config;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.NonNull;

/**
 * Redis HttpSession 导入选择器
 *
 * <p>通过字符串引用避免编译期依赖 spring-session-data-redis，
 * 仅在运行时 classpath 中存在对应类时生效。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
class RedisHttpSessionImportSelector implements ImportSelector {

    private static final String REDIS_HTTP_SESSION_CONFIG =
            "org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration";

    @Override
    @NonNull
    public String[] selectImports(@NonNull AnnotationMetadata importingClassMetadata) {
        return new String[]{REDIS_HTTP_SESSION_CONFIG};
    }
}