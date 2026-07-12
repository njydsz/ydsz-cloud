package com.njydsz.pmis.common.web.config;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.jspecify.annotations.NonNull;

/**
 * Redis HttpSession 瀵煎叆閫夋嫨鍣?
 *
 * <p>閫氳繃瀛楃涓插紩鐢ㄩ伩鍏嶇紪璇戞湡渚濊禆 spring-session-data-redis锛?
 * 浠呭湪杩愯鏃?classpath 涓瓨鍦ㄥ搴旂被鏃剁敓鏁堛€?
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