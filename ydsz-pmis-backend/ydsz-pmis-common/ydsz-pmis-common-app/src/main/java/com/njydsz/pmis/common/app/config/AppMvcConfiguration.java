package com.njydsz.pmis.common.app.config;

import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.app.advice.AppGlobalResponseAdvice;
import com.njydsz.pmis.common.app.exception.AppExceptionHandler;
import com.njydsz.pmis.common.app.filter.AppAuthFilter;
import com.njydsz.pmis.common.app.filter.AppContentCachingFilter;
import com.njydsz.pmis.common.app.filter.AppRequestIdResponseFilter;
import com.njydsz.pmis.common.app.filter.AppSecurityHeaderFilter;
import com.njydsz.pmis.common.app.filter.AppSignatureFilter;
import com.njydsz.pmis.common.app.interceptor.AppRequestLogInterceptor;
import com.njydsz.pmis.common.base.config.BaseAutoConfiguration;
import com.njydsz.pmis.common.base.constant.BaseFilterOrders;
import com.njydsz.pmis.common.base.interceptor.BaseHttpInterceptor;
import com.njydsz.pmis.common.auth.config.AuthFilterConfiguration;
import com.njydsz.pmis.common.auth.model.AuthenticationProvider;
import com.njydsz.pmis.common.base.config.BaseMvcConfiguration;
import com.njydsz.pmis.common.safe.config.SafeConfiguration;
import com.njydsz.pmis.common.safe.config.SecurityHeaderProperties;
import com.njydsz.pmis.common.auth.handler.AuthHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/**
 * App 绔?MVC 鏍稿績閰嶇疆
 *
 * <p>App 绔殑鏍稿績 Web 閰嶇疆锛岄泦涓敞鍐岃繃婊ゅ櫒閾俱€佹嫤鎴櫒銆佸璞℃槧灏勭瓑銆? * 涓?Web 绔浉姣旓紝App 绔湪 Filter 閾俱€佺鍚嶆牎楠屻€丆ORS 绛夋柟闈㈡湁浠ヤ笅宸紓锛? * <ul>
 *   <li>鏀寔 {@link AppSignatureFilter} 璇锋眰绛惧悕鏍￠獙锛堥槻姝㈣姹備吉閫狅級</li>
 *   <li>杩囨护鍣ㄩ摼椤哄簭锛氬唴瀹圭紦瀛?鈫?瀹夊叏澶?鈫?绛惧悕 鈫?TraceId 鍝嶅簲 鈫?閴存潈</li>
 *   <li>鎷︽埅鍣ㄩ『搴忥細璇锋眰鏃ュ織 鈫?璇锋眰涓婁笅鏂囨竻鐞?/li>
 *   <li>鍏ㄥ眬 ObjectMapper 鍏抽棴 BigDecimal 绉戝璁℃暟娉?/li>
 * </ul>
 *
 * <p><b>Filter 閾鹃『搴忥紙Order 鐢卞皬鍒板ぇ锛夛細</b>
 * <pre>
 * AppContentCachingFilter  鈫?AppSecurityHeaderFilter 鈫?AppSignatureFilter
 *                          鈫?AppRequestIdResponseFilter 鈫?AppAuthFilter
 * </pre>
 *
 * <p><b>绾跨▼瀹夊叏鎬э細</b>鏈厤缃被涓烘棤鐘舵€侀厤缃被锛屾敞鍐岀殑 Filter / Interceptor 鍧囦负绾跨▼瀹夊叏瀹炵幇銆? *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@AutoConfigureBefore({BaseAutoConfiguration.class, SafeConfiguration.class})
@EnableConfigurationProperties({AppCorsProperties.class, AppTraceProperties.class, AppSignatureProperties.class})
@Import({AppGlobalResponseAdvice.class, AppExceptionHandler.class})
public class AppMvcConfiguration extends BaseMvcConfiguration {

    private final BaseHttpInterceptor baseHttpInterceptor;
    private final AppRequestLogInterceptor appRequestLogInterceptor;
    private final AppTraceProperties appTraceProperties;
    private final AppSignatureProperties appSignatureProperties;

    /**
     * 鏋勯€犳柟娉?     *
     * @param appCorsProperties       App 绔?CORS 閰嶇疆灞炴€?     * @param securityHeaderProperties 瀹夊叏鍝嶅簲澶撮厤缃睘鎬?     * @param baseHttpInterceptor     鍩虹 HTTP 鎷︽埅鍣紙璇锋眰涓婁笅鏂囨竻鐞嗭級
     * @param appRequestLogInterceptor App 绔姹傛棩蹇楁嫤鎴櫒
     * @param appTraceProperties      App 绔?Trace / 璇锋眰杩借釜閰嶇疆
     * @param appSignatureProperties  App 绔姹傜鍚嶉厤缃?     */
    public AppMvcConfiguration(AppCorsProperties appCorsProperties,
                               SecurityHeaderProperties securityHeaderProperties,
                               BaseHttpInterceptor baseHttpInterceptor,
                               AppRequestLogInterceptor appRequestLogInterceptor,
                               AppTraceProperties appTraceProperties,
                               AppSignatureProperties appSignatureProperties) {
        super(appCorsProperties);
        this.baseHttpInterceptor = baseHttpInterceptor;
        this.appRequestLogInterceptor = appRequestLogInterceptor;
        this.appTraceProperties = appTraceProperties;
        this.appSignatureProperties = appSignatureProperties;
    }

    /**
     * 鑷畾涔?ObjectMapper 閰嶇疆
     *
     * <p>App 绔叧闂?BigDecimal 鐨勭瀛﹁鏁版硶琛ㄧず锛岀‘淇濋暱 ID 绛夊ぇ鏁板瓧浠ユ櫘閫氬崄杩涘埗瀛楃涓茶緭鍑恒€?     *
     * @param mapper Spring MVC 榛樿 ObjectMapper 瀹炰緥
     */
    protected void configureObjectMapper(ObjectMapper mapper) {
        // App绔ぇ鏁版牸寮忓寲锛氶伩鍏嶇瀛﹁鏁版硶
        mapper.configure(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN.mappedFeature(), true);
    }

    /**
     * 娉ㄥ唽鎷︽埅鍣?     *
     * <p>娉ㄥ唽椤哄簭锛?     * <ol>
     *   <li>App 绔姹傛棩蹇楁嫤鎴櫒锛堥『搴忥細{@link BaseFilterOrders#INTERCEPTOR_REQUEST_LOG}锛?/li>
     *   <li>鍩虹璇锋眰涓婁笅鏂囨竻鐞嗘嫤鎴櫒锛堥『搴忥細{@link BaseFilterOrders#REQUEST_CONTEXT_CLEANUP}锛?/li>
     * </ol>
     *
     * @param registry Spring MVC 鎷︽埅鍣ㄦ敞鍐岃〃
     */
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(appRequestLogInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.INTERCEPTOR_REQUEST_LOG);

        registry.addInterceptor(baseHttpInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.REQUEST_CONTEXT_CLEANUP);
    }

    /**
     * 娉ㄥ唽璇锋眰浣撶紦瀛樿繃婊ゅ櫒
     *
     * <p>鐢ㄤ簬鍦ㄨ繃婊ゅ櫒閾句腑鍚庣画鑺傜偣鍙互閲嶅璇诲彇璇锋眰浣擄紙濡傜鍚嶆牎楠屻€佸弬鏁版牎楠岋級銆?     *
     * @return FilterRegistrationBean 瀹炰緥
     */
    @Bean
    public FilterRegistrationBean<AppContentCachingFilter> appContentCachingFilter() {
        FilterRegistrationBean<AppContentCachingFilter> bean = new FilterRegistrationBean<>(new AppContentCachingFilter());
        bean.addUrlPatterns("/*");
        bean.setName("appContentCachingFilter");
        bean.setOrder(BaseFilterOrders.CONTENT_CACHING_FILTER);
        return bean;
    }

    /**
     * 娉ㄥ唽 App 绔姹傜鍚嶆牎楠岃繃婊ゅ櫒
     *
     * <p>浠呭湪 {@code remi.app.signature.enabled=true} 鏃舵縺娲汇€?     * 鍚敤鏃跺繀椤婚厤缃?{@code remi.app.signature.app-secret}锛屽惁鍒欏惎鍔ㄥけ璐ャ€?     *
     * @return FilterRegistrationBean 瀹炰緥
     * @throws IllegalStateException 褰撳惎鐢ㄧ鍚嶆牎楠屼絾鏈厤缃?app-secret 鏃舵姏鍑?     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.app.signature", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<AppSignatureFilter> appSignatureFilter() {
        if (appSignatureProperties.getAppSecret() == null || appSignatureProperties.getAppSecret().isBlank()) {
            throw new IllegalStateException("鍚敤绛惧悕楠岃瘉鏃跺繀椤婚厤缃?remi.app.signature.app-secret");
        }
        AppSignatureFilter filter = new AppSignatureFilter(
                appSignatureProperties.getAppSecret(),
                appSignatureProperties.getTimestampTolerance());
        FilterRegistrationBean<AppSignatureFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setName("appSignatureFilter");
        bean.setOrder(appSignatureProperties.getOrder());
        return bean;
    }

    /**
     * 鍒涘缓 App 绔壌鏉冭繃婊ゅ櫒 Bean
     *
     * <p>浠呭湪涓氬姟鏂规湭鑷娉ㄥ唽 {@code appAuthFilter} 鏃跺垱寤洪粯璁ゅ疄鐜般€?     * 涓氬姟鏂瑰彲鎻愪緵鑷畾涔?{@link com.njydsz.pmis.common.auth.model.AuthenticationProvider} 瑕嗙洊榛樿璁よ瘉閫昏緫銆?     *
     * @param appAuthHandler             App 绔璇佸鐞嗗櫒
     * @param authFilterConfiguration    閫氱敤閴存潈杩囨护鍣ㄩ厤缃?     * @param authenticationProvider     鑷畾涔夎璇佹彁渚涜€咃紙鍙负绌猴紝涓虹┖鏃朵娇鐢?AuthHandler锛?     * @param applicationContext         Spring 搴旂敤涓婁笅鏂囷紝鐢ㄤ簬鑾峰彇搴旂敤鍚?     * @return AppAuthFilter 瀹炰緥
     */
    @Bean
    @ConditionalOnMissingBean(name = "appAuthFilter")
    public AppAuthFilter appAuthFilter(AuthHandler appAuthHandler,
                                        AuthFilterConfiguration authFilterConfiguration,
                                        @Nullable AuthenticationProvider authenticationProvider,
                                        ApplicationContext applicationContext) {
        String applicationName = applicationContext.getApplicationName();
        return new AppAuthFilter(applicationName, authFilterConfiguration, appAuthHandler, authenticationProvider);
    }

    /**
     * 娉ㄥ唽閴存潈杩囨护鍣ㄥ埌 Servlet 瀹瑰櫒
     *
     * @param appAuthFilter App 绔壌鏉冭繃婊ゅ櫒瀹炰緥
     * @return FilterRegistrationBean 瀹炰緥
     */
    @Bean
    public FilterRegistrationBean<AppAuthFilter> authFilter(AppAuthFilter appAuthFilter) {
        FilterRegistrationBean<AppAuthFilter> authFilterBean = new FilterRegistrationBean<>(appAuthFilter);
        authFilterBean.addUrlPatterns("/*");
        authFilterBean.setName("appAuthFilter");
        authFilterBean.setOrder(BaseFilterOrders.AUTH_FILTER);
        return authFilterBean;
    }

    /**
     * 娉ㄥ唽瀹夊叏鍝嶅簲澶磋繃婊ゅ櫒
     *
     * <p>榛樿寮€鍚紙{@code remi.safe.security-headers.enabled=true}锛夛紝閫氳繃 {@link SecurityHeaderProperties}
     * 鎺у埗鍏蜂綋鍝嶅簲澶寸瓥鐣ャ€?     *
     * @param securityHeaderProperties 瀹夊叏鍝嶅簲澶撮厤缃?     * @return FilterRegistrationBean 瀹炰緥
     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.safe.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AppSecurityHeaderFilter> securityHeaderFilter(SecurityHeaderProperties securityHeaderProperties) {
        AppSecurityHeaderFilter securityHeaderFilter = new AppSecurityHeaderFilter(securityHeaderProperties);
        FilterRegistrationBean<AppSecurityHeaderFilter> bean = new FilterRegistrationBean<>(securityHeaderFilter);
        bean.addUrlPatterns("/*");
        bean.setName("securityHeaderFilter");
        bean.setOrder(BaseFilterOrders.SECURITY_HEADER_FILTER);
        return bean;
    }

    /**
     * 娉ㄥ唽璇锋眰 ID 鍝嶅簲杩囨护鍣?     *
     * <p>榛樿寮€鍚紙{@code remi.app.trace.enabled=true}锛夛紝灏嗗綋鍓嶈姹傜殑 RequestId 鍐欏叆鍝嶅簲澶?     * {@code X-Request-Id}锛屼究浜庡鎴风鍏宠仈鏃ュ織銆?     *
     * @return FilterRegistrationBean 瀹炰緥
     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.app.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AppRequestIdResponseFilter> requestIdResponseFilter() {
        AppRequestIdResponseFilter requestIdResponseFilter = new AppRequestIdResponseFilter(appTraceProperties);
        FilterRegistrationBean<AppRequestIdResponseFilter> bean = new FilterRegistrationBean<>(requestIdResponseFilter);
        bean.addUrlPatterns("/*");
        bean.setName("appRequestIdResponseFilter");
        bean.setOrder(BaseFilterOrders.TRACE_ID_RESPONSE_FILTER);
        return bean;
    }
}
