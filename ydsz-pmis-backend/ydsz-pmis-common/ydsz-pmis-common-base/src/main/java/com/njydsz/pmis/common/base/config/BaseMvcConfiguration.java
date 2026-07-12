package com.njydsz.pmis.common.base.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.util.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * MVC 鍩虹閰嶇疆锛圵eb/App 鍏变韩锛? *
 * <p>瀛愮被鎻愪緵鍏蜂綋鐨?{@link BaseCorsProperties} 鍜?{@link BaseTraceProperties} 瀹炵幇锛? * 浠ュ強娉ㄥ唽鑷繁鐨勬嫤鎴櫒鍜岃繃婊ゅ櫒 Bean銆? *
 * <p>JSON 搴忓垪鍖栫粺涓€浣跨敤 Jackson锛堝ぇ鍘傛爣鍑嗭級銆侽bjectMapper 浼樺厛浣跨敤 Spring 瀹瑰櫒涓敞鍏ョ殑瀹炰緥锛? * 鑻ヤ笉瀛樺湪鍒欎娇鐢?JsonUtils 鐨勫叏灞€瀹炰緥銆係pring Boot 鑷姩閰嶇疆浼氬熀浜庤 ObjectMapper 鍒涘缓 JSON 娑堟伅杞崲鍣ㄣ€? *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @since 3.5.0
 */
public abstract class BaseMvcConfiguration implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BaseMvcConfiguration.class);

    /**
     * CORS 璺ㄥ煙閰嶇疆灞炴€?     */
    private final BaseCorsProperties corsProperties;

    /**
     * 鏋勯€?MVC 鍩虹閰嶇疆
     *
     * @param corsProperties CORS 閰嶇疆灞炴€?     */
    protected BaseMvcConfiguration(BaseCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 鑾峰彇 CORS 閰嶇疆灞炴€?     *
     * @return CORS 閰嶇疆灞炴€у疄渚?     */
    protected BaseCorsProperties getCorsProperties() {
        return corsProperties;
    }

    /**
     * 娉ㄥ唽 ObjectMapper Bean
     *
     * <p>浼樺厛浣跨敤 Spring 瀹瑰櫒涓凡鏈夌殑 ObjectMapper锛岃嫢涓嶅瓨鍦ㄥ垯浣跨敤 JsonUtils 鐨勫叏灞€瀹炰緥銆?     * Spring Boot 鑷姩閰嶇疆浼氬熀浜庢 ObjectMapper 鍒涘缓 JSON 娑堟伅杞崲鍣紝鏃犻渶鎵嬪姩娉ㄥ唽 HttpMessageConverters銆?     *
     * @return ObjectMapper 瀹炰緥
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return JsonUtils.getMapper();
    }

    /**
     * 娉ㄥ唽 CORS 杩囨护鍣?     *
     * <p>閫氳繃 {@link BaseCorsProperties#isEnabled()} 鎺у埗鏄惁鐢熸晥锛?     * 閰嶇疆鐢卞瓙绫婚€氳繃 {@code @ConfigurationProperties} 缁戝畾鍏蜂綋鍓嶇紑銆?     *
     * @return CORS 杩囨护鍣ㄦ敞鍐屽櫒锛岀鐢ㄦ椂杩斿洖 null
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        // 閫氳繃瀛愮被缁戝畾鐨勯厤缃睘鎬э紙remi.web.cors / remi.app.cors锛夌殑 enabled 瀛楁鎺у埗锛?        // 涓嶅啀浣跨敤 @ConditionalOnProperty(prefix = "remi.cors")锛岄伩鍏嶅墠缂€涓庡瓙绫婚厤缃笉鍖归厤
        if (!corsProperties.isEnabled()) {
            return null;
        }

        // P1-6: CORS 瀹夊叏鍔犲浐 鈥?鍚姩鏃舵牎楠岄厤缃畨鍏ㄦ€э紝杈撳嚭璀﹀憡鏃ュ織
        List<String> securityWarnings = corsProperties.validateSecurity();
        for (String warning : securityWarnings) {
            log.warn("[CORS Security] {}", warning);
        }

        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowCredentials(corsProperties.isAllowCredentials());
        corsProperties.getAllowedOriginPatterns().forEach(corsConfig::addAllowedOriginPattern);
        corsProperties.getAllowedHeaders().forEach(corsConfig::addAllowedHeader);
        corsProperties.getAllowedMethods().forEach(corsConfig::addAllowedMethod);
        // 鏆撮湶鍝嶅簲澶撮厤缃紙鍘熶唬鐮侀仐婕忎簡姝ら」锛?        corsProperties.getExposedHeaders().forEach(corsConfig::addExposedHeader);
        corsConfig.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();
        String pathPattern = corsProperties.getPathPattern();
        configSource.registerCorsConfiguration(pathPattern != null ? pathPattern : "/**", corsConfig);

        FilterRegistrationBean<CorsFilter> corsBean = new FilterRegistrationBean<>(new CorsFilter(configSource));
        corsBean.setName("corsFilter");
        corsBean.setOrder(corsProperties.getOrder());
        return corsBean;
    }
}
