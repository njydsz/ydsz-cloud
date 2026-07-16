package com.njydsz.common.web.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Redis HttpSession 导入选择器
 *
 * <p>通过字符串引用避免编译期依赖 spring-session-data-redis，
 * 仅在运行时 classpath 中存在对应类时生效。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
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