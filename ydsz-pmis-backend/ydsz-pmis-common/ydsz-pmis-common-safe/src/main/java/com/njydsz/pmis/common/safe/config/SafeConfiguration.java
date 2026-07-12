package com.njydsz.pmis.common.safe.config;

import com.njydsz.pmis.common.redis.service.RedisService;
import com.njydsz.pmis.common.safe.csrf.CsrfTokenGenerator;
import com.njydsz.pmis.common.safe.csrf.CsrfTokenRepository;
import com.njydsz.pmis.common.safe.csrf.impl.DefaultCsrfTokenGenerator;
import com.njydsz.pmis.common.safe.csrf.impl.InMemoryCsrfTokenRepository;
import com.njydsz.pmis.common.safe.csrf.impl.RedisCsrfTokenRepository;
import com.njydsz.pmis.common.safe.alert.SafeAlertProperties;
import com.njydsz.pmis.common.safe.alert.SecurityEventPublisher;
import com.njydsz.pmis.common.safe.converter.XssJsonMessageConverter;
import com.njydsz.pmis.common.safe.core.JsonBodyXssCleaner;
import com.njydsz.pmis.common.safe.advice.XssRequestBodyAdvice;
import com.njydsz.pmis.common.safe.config.condition.XssConverterModeCondition;
import com.njydsz.pmis.common.safe.config.condition.XssFilterModeCondition;
import com.njydsz.pmis.common.safe.filter.CsrfFilter;
import com.njydsz.pmis.common.safe.filter.SecurityHeaderFilter;
import com.njydsz.pmis.common.safe.filter.SqlInjectionFilter;
import com.njydsz.pmis.common.safe.filter.XssFilter;
import com.njydsz.pmis.common.safe.ratelimit.RateLimitFilter;
import com.njydsz.pmis.common.safe.ratelimit.RateLimitProperties;
import com.njydsz.pmis.common.safe.sensitive.SensitiveDataAdvice;
import com.njydsz.pmis.common.safe.sensitive.SensitiveDataConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 瀹夊叏妯″潡鑷姩閰嶇疆
 * <p>
 * 闆嗕腑娉ㄥ唽浠ヤ笅瀹夊叏闃叉姢鑳藉姏锛? * <ul>
 *   <li>XSS 杩囨护鍣細鍩轰簬 OWASP 搴撶殑鍏ㄥ眬 HTTP 璇锋眰鍙傛暟涓?JSON 璇锋眰浣撴竻娲?/li>
 *   <li>瀹夊叏鍝嶅簲澶达細闃叉 XSS銆佺偣鍑诲姭鎸併€丮IME 鍡呮帰绛?Web 瀹夊叏濞佽儊</li>
 *   <li>CSRF 闃叉姢锛氬熀浜?Token 鏈哄埗锛孯edis 瀛樺偍鏀寔鍒嗗竷寮?/li>
 *   <li>SQL 娉ㄥ叆闃叉姢锛氬熀浜庢鍒欑殑璇锋眰鍙傛暟鎷︽埅</li>
 *   <li>闄愭祦闃叉姢锛氬熀浜?Redis 浠ょ墝妗剁殑鍏ㄥ眬闄愭祦</li>
 *   <li>鏁忔劅鏁版嵁鑴辨晱锛氬熀浜?Jackson 搴忓垪鍖栧櫒鐨勫瓧娈电骇鑴辨晱</li>
 *   <li>楠岃瘉鐮侊細鍥惧舰/绠楁湳楠岃瘉鐮佺敓鎴愪笌楠岃瘉</li>
 * </ul>
 *
 * <p><b>杩囨护鍣ㄦ墽琛岄『搴忥細</b>SecurityHeaderFilter 鈫?XssFilter 鈫?SqlInjectionFilter
 * 鈫?CsrfFilter 鈫?RateLimitFilter銆傚叾涓?RateLimitFilter 浼樺厛绾ф渶楂橈紝闄愭祦澶辫触鐩存帴
 * 杩斿洖 429 鑰屼笉鍐嶈繘鍏ュ悗缁繃婊ゅ櫒銆?/p>
 *
 * <p><b>娉ㄦ剰锛?/b>闃查噸澶嶆彁浜?骞傜瓑鎬у姛鑳界敱鏈ā鍧楃殑 Redis 闄愭祦鑳藉姏鎻愪緵銆?/p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(FilterRegistrationBean.class)
@EnableConfigurationProperties({
        SafeXssProperties.class,
        SecurityHeaderProperties.class,
        CsrfProperties.class,
        SensitiveDataConfiguration.class,
        SafeAlertProperties.class,
        RateLimitProperties.class
})
public class SafeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SafeConfiguration.class);

    /**
     * 娉ㄥ唽瀹夊叏鍝嶅簲澶磋繃婊ゅ櫒
     *
     * <p>涓?HTTP 鍝嶅簲娣诲姞瀹夊叏鐩稿叧鐨勫ご閮紝鍖呮嫭锛?     * <ul>
     *   <li>X-Frame-Options锛氶槻姝㈢偣鍑诲姭鎸?/li>
     *   <li>X-Content-Type-Options锛氶槻姝?MIME 鍡呮帰</li>
     *   <li>X-XSS-Protection锛歑SS 杩囨护鍣?/li>
     *   <li>Strict-Transport-Security锛氬己鍒?HTTPS</li>
     *   <li>Content-Security-Policy锛氬唴瀹瑰畨鍏ㄧ瓥鐣?/li>
     * </ul>
     *
     * @param properties 瀹夊叏鍝嶅簲澶撮厤缃睘鎬?     * @return 瀹夊叏鍝嶅簲澶磋繃婊ゅ櫒娉ㄥ唽 bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "securityHeaderFilter")
    @ConditionalOnProperty(prefix = "remi.safe.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SecurityHeaderFilter> securityHeaderFilterRegistration(SecurityHeaderProperties properties) {
        FilterRegistrationBean<SecurityHeaderFilter> registrationBean = new FilterRegistrationBean<>(new SecurityHeaderFilter(properties));
        registrationBean.setName("securityHeaderFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(properties.getOrder());
        return registrationBean;
    }

    /**
     * 娉ㄥ唽瀹夊叏浜嬩欢鍙戝竷鍣?     *
     * @return 瀹夊叏浜嬩欢鍙戝竷鍣ㄥ疄渚?     */
    @Bean
    @ConditionalOnMissingBean(SecurityEventPublisher.class)
    public SecurityEventPublisher securityEventPublisher() {
        return new SecurityEventPublisher();
    }

    /**
     * 娉ㄥ唽 XSS 杩囨护鍣?     *
     * <p>榛樿鎺掗櫎璺緞锛?error銆?favicon.ico銆?actuator/**
     * 浠呭湪 mode=filter 鏃舵敞鍐岋紙涓?RequestBodyAdvice 鍜?Converter 浜掓枼锛夈€?     *
     * @param xssProperties   XSS 閰嶇疆灞炴€?     * @param eventPublisher  瀹夊叏浜嬩欢鍙戝竷鍣?     * @param alertProperties 瀹夊叏鍛婅閰嶇疆灞炴€?     * @return XSS 杩囨护鍣ㄦ敞鍐?bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "xssFilterRegistration")
    @Conditional(XssFilterModeCondition.class)
    public FilterRegistrationBean<XssFilter> xssFilterRegistration(SafeXssProperties xssProperties,
                                                                    SecurityEventPublisher eventPublisher,
                                                                    SafeAlertProperties alertProperties) {
        FilterRegistrationBean<XssFilter> registrationBean = new FilterRegistrationBean<>(
                new XssFilter(xssProperties.getExcludes(), eventPublisher, alertProperties));
        registrationBean.setName("xssFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(xssProperties.getOrder());
        return registrationBean;
    }

    /**
     * 娉ㄥ唽 JSON Body XSS 娓呯悊鍣?     *
     * <p>鐢ㄤ簬閫掑綊娓呯悊 JSON 瀵硅薄涓墍鏈夊瓧绗︿覆鍊肩殑 XSS 鍐呭銆?     *
     * @return JSON Body XSS 娓呯悊鍣ㄥ疄渚?     */
    @Bean
    @ConditionalOnMissingBean(JsonBodyXssCleaner.class)
    public JsonBodyXssCleaner jsonBodyXssCleaner() {
        return new JsonBodyXssCleaner();
    }

    /**
     * 娉ㄥ唽 XSS 璇锋眰浣撴嫤鎴櫒
     *
     * <p>鍦?JSON 鍙嶅簭鍒楀寲鍓嶏紝瀵硅姹備綋涓殑瀛楃涓插€艰繘琛?XSS 娓呯悊銆?     * 浠呭湪 Filter 妯″紡涓嬬敓鏁堬紝閬垮厤涓?Converter 妯″紡鍙岄噸娓呮礂銆?     *
     * @param xssCleaner    JSON Body XSS 娓呯悊鍣?     * @param xssProperties XSS 閰嶇疆灞炴€?     * @return XSS 璇锋眰浣撴嫤鎴櫒瀹炰緥
     */
    @Bean
    @ConditionalOnMissingBean(XssRequestBodyAdvice.class)
    public XssRequestBodyAdvice xssRequestBodyAdvice(JsonBodyXssCleaner xssCleaner,
            SafeXssProperties xssProperties) {
        return new XssRequestBodyAdvice(xssCleaner, xssProperties);
    }

    /**
     * 娉ㄥ唽 XSS JSON 娑堟伅杞崲鍣?     *
     * <p>鍦?JSON 鍙嶅簭鍒楀寲闃舵瀵瑰瓧绗︿覆鍊艰繘琛?XSS 杩囨护銆?     * 浠呭湪 mode=converter 鏃舵敞鍐岋紙涓?Filter 鍜?Advice 妯″紡浜掓枼锛夈€?     *
     * <p>杩囨护鍣ㄩ€氳繃 {@link HttpMessageConverters} 娉ㄥ唽鍒?Spring MVC 鐨勬秷鎭浆鎹㈠櫒閾句腑锛?     * 鏇挎崲榛樿鐨?MappingJackson2HttpMessageConverter锛屽湪鍙嶅簭鍒楀寲鍓嶅畬鎴?XSS 娓呮礂銆?     *
     * @param properties XSS 閰嶇疆灞炴€?     * @return XSS JSON 娑堟伅杞崲鍣?Bean
     */
    @Bean
    @ConditionalOnMissingBean(XssJsonMessageConverter.class)
    @Conditional(XssConverterModeCondition.class)
    public XssJsonMessageConverter xssJsonMessageConverter(SafeXssProperties properties) {
        log.info("娉ㄥ唽 XSS JSON 娑堟伅杞崲鍣紝妯″紡: {}", properties.getMode());
        return new XssJsonMessageConverter();
    }

    // XssJsonMessageConverter 宸叉敞鍐屼负 Bean锛孲pring Boot 4.1 鑷姩妫€娴?HttpMessageConverter Bean 骞舵敞鍐屽埌杞崲鍣ㄩ摼锛?    // 鏃犻渶鍐嶉€氳繃 HttpMessageConverters锛堝凡寮冪敤锛夊寘瑁呮敞鍐屻€?
    /**
     * 娉ㄥ唽 CSRF 浠ょ墝鐢熸垚鍣?     */
    @Bean
    @ConditionalOnMissingBean(CsrfTokenGenerator.class)
    public CsrfTokenGenerator csrfTokenGenerator(CsrfTokenRepository tokenRepository) {
        return new DefaultCsrfTokenGenerator(tokenRepository);
    }

    /**
     * 娉ㄥ唽 CSRF 浠ょ墝瀛樺偍搴擄紙Redis 鍒嗗竷寮忕幆澧冿級
     *
     * <p>褰?RedisService 鍙敤鏃讹紝鑷姩浣跨敤 Redis 瀛樺偍浠ユ敮鎸佸垎甯冨紡閮ㄧ讲銆?     */
    @Bean
    @Primary
    @ConditionalOnBean(RedisService.class)
    public CsrfTokenRepository redisCsrfTokenRepository(CsrfProperties properties, RedisService redisService) {
        return new RedisCsrfTokenRepository(properties.getExpirationSeconds(), redisService);
    }

    /**
     * 娉ㄥ唽 CSRF 浠ょ墝瀛樺偍搴擄紙鍗曟満鍐呭瓨鐜锛?     *
     * <p>浠呭綋 RedisService 涓嶅彲鐢ㄦ椂浣跨敤鍐呭瓨瀛樺偍銆傞€傜敤浜庡崟鏈洪儴缃插満鏅€?     *
     * @param properties CSRF 閰嶇疆灞炴€?     * @return CSRF 浠ょ墝瀛樺偍搴撳疄渚?     */
    @Bean
    @ConditionalOnMissingBean({RedisService.class, CsrfTokenRepository.class})
    public CsrfTokenRepository inMemoryCsrfTokenRepository(CsrfProperties properties) {
        return new InMemoryCsrfTokenRepository(properties.getExpirationSeconds());
    }

    /**
     * 娉ㄥ唽 CSRF 闃叉姢杩囨护鍣?     *
     * <p>闃叉璺ㄧ珯璇锋眰浼€狅紙CSRF锛夋敾鍑伙細
     * <ul>
     *   <li>GET 璇锋眰锛氳嚜鍔ㄧ敓鎴愬苟杩斿洖 CSRF 浠ょ墝</li>
     *   <li>鍏朵粬璇锋眰锛氶獙璇?CSRF 浠ょ墝鏈夋晥鎬?/li>
     * </ul>
     *
     * @param properties      CSRF 閰嶇疆灞炴€?     * @param tokenRepository CSRF 浠ょ墝瀛樺偍搴?     * @param tokenGenerator  CSRF 浠ょ墝鐢熸垚鍣?     * @return CSRF 闃叉姢杩囨护鍣ㄦ敞鍐?bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "csrfFilterRegistration")
    @ConditionalOnProperty(prefix = "remi.safe.csrf", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<CsrfFilter> csrfFilterRegistration(CsrfProperties properties, CsrfTokenRepository tokenRepository, CsrfTokenGenerator tokenGenerator) {
        FilterRegistrationBean<CsrfFilter> registrationBean = new FilterRegistrationBean<>(new CsrfFilter(properties, tokenRepository));
        registrationBean.setName("csrfFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(properties.getOrder());
        return registrationBean;
    }

    /**
     * 娉ㄥ唽鏁忔劅鏁版嵁鑴辨晱 AOP 鎷︽埅鍣?     *
     * <p>瀵?Controller 杩斿洖鍊艰繘琛屾晱鎰熸暟鎹劚鏁忓鐞嗐€?     * 浠呭湪 {@code remi.safe.sensitive.enabled=true} 鏃剁敓鏁堛€?     *
     * @param configuration 鏁忔劅鏁版嵁鑴辨晱閰嶇疆
     * @return 鏁忔劅鏁版嵁鑴辨晱 AOP 鎷︽埅鍣ㄥ疄渚?     */
    @Bean
    @ConditionalOnMissingBean(SensitiveDataAdvice.class)
    public SensitiveDataAdvice sensitiveDataAdvice(SensitiveDataConfiguration configuration) {
        log.info("娉ㄥ唽鏁忔劅鏁版嵁鑴辨晱 AOP 鎷︽埅鍣紝鍚敤鐘舵€? {}", configuration.isEnabled());
        return new SensitiveDataAdvice(configuration);
    }

    /**
     * 娉ㄥ唽闄愭祦杩囨护鍣?     *
     * <p>鍩轰簬 Redis 浠ょ墝妗剁殑鍏ㄥ眬闄愭祦 Filter锛屾敮鎸佹寜 IP/鐢ㄦ埛/鍏ㄥ眬缁村害銆?     * 浠呭湪 {@code remi.safe.ratelimit.enabled=true} 鏃舵敞鍐屻€?     *
     * @param properties      闄愭祦閰嶇疆灞炴€?     * @param redisService    Redis 鏈嶅姟
     * @param eventPublisher  瀹夊叏浜嬩欢鍙戝竷鍣?     * @param alertProperties 瀹夊叏鍛婅閰嶇疆灞炴€?     * @return 闄愭祦杩囨护鍣ㄦ敞鍐?bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "rateLimitFilterRegistration")
    @ConditionalOnBean(RedisService.class)
    @ConditionalOnProperty(prefix = "remi.safe.ratelimit", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties,
            RedisService redisService,
            SecurityEventPublisher eventPublisher,
            SafeAlertProperties alertProperties) {
        FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>(
                new RateLimitFilter(properties, redisService, eventPublisher, alertProperties));
        registrationBean.setName("rateLimitFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registrationBean;
    }

    /**
     * 娉ㄥ唽 SQL 娉ㄥ叆闃叉姢杩囨护鍣?     *
     * <p>妫€娴嬪苟鎷︽埅 HTTP 璇锋眰涓殑 SQL 娉ㄥ叆鏀诲嚮锛屼繚鎶ゅ簲鐢ㄥ畨鍏ㄣ€?     * 浠呭湪 {@code remi.safe.sql-injection.enabled=true} 鏃舵敞鍐屻€?     *
     * <p>鏀寔鐧藉悕鍗曢厤缃細
     * <ul>
     *   <li>{@code remi.safe.sql-injection.whitelist-paths} - 鐧藉悕鍗曡矾寰勶紙Ant 椋庢牸锛岄€楀彿鍒嗛殧锛?/li>
     *   <li>{@code remi.safe.sql-injection.whitelist-params} - 鐧藉悕鍗曞弬鏁板悕锛堥€楀彿鍒嗛殧锛?/li>
     * </ul>
     *
     * @param eventPublisher  瀹夊叏浜嬩欢鍙戝竷鍣?     * @param whitelistPaths  鐧藉悕鍗曡矾寰?     * @param whitelistParams 鐧藉悕鍗曞弬鏁板悕
     * @return SQL 娉ㄥ叆杩囨护鍣ㄦ敞鍐?bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "sqlInjectionFilterRegistration")
    @ConditionalOnProperty(prefix = "remi.safe.sql-injection", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SqlInjectionFilter> sqlInjectionFilterRegistration(
            SecurityEventPublisher eventPublisher,
            @org.springframework.beans.factory.annotation.Value("${remi.safe.sql-injection.whitelist-paths:}") List<String> whitelistPaths,
            @org.springframework.beans.factory.annotation.Value("${remi.safe.sql-injection.whitelist-params:}") List<String> whitelistParams) {
        FilterRegistrationBean<SqlInjectionFilter> registrationBean = new FilterRegistrationBean<>(
                new SqlInjectionFilter(true, eventPublisher, whitelistPaths, whitelistParams));
        registrationBean.setName("sqlInjectionFilter");
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registrationBean;
    }
}
