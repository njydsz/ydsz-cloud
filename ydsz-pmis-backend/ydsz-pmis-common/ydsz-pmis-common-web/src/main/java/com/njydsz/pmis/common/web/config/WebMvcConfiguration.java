package com.njydsz.pmis.common.web.config;

import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.auth.config.AuthFilterConfiguration;
import com.njydsz.pmis.common.auth.model.AuthenticationProvider;
import com.njydsz.pmis.common.base.config.BaseAutoConfiguration;
import com.njydsz.pmis.common.base.config.BaseMvcConfiguration;
import com.njydsz.pmis.common.base.constant.BaseFilterOrders;
import com.njydsz.pmis.common.safe.config.SafeConfiguration;
import com.njydsz.pmis.common.safe.config.SecurityHeaderProperties;
import com.njydsz.pmis.common.web.advice.GlobalResponseAdvice;
import com.njydsz.pmis.common.web.auth.AuthHandlerFactory;
import com.njydsz.pmis.common.web.exception.WebExceptionHandler;
import com.njydsz.pmis.common.web.filter.ContentCachingFilter;
import com.njydsz.pmis.common.web.filter.SecurityHeaderFilter;
import com.njydsz.pmis.common.web.filter.TraceIdResponseFilter;
import com.njydsz.pmis.common.web.filter.WebAuthFilter;
import com.njydsz.pmis.common.web.interceptor.RequestLogInterceptor;
import com.njydsz.pmis.common.base.interceptor.BaseHttpInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/**
 * Web 绔?MVC 鏍稿績閰嶇疆
 *
 * <p>缁ф壙 {@link BaseMvcConfiguration}锛屾敞鍐?Web 绔笓灞炵殑鎷︽埅鍣ㄥ拰杩囨护鍣ㄩ摼銆? *
 * <p><b>杩囨护鍣ㄩ摼锛堟寜 order 浠庡皬鍒板ぇ鎵ц锛夛細</b>
 * <ol>
 *   <li>{@link ContentCachingFilter}锛坥rder=MIN_VALUE锛? 璇锋眰浣撶紦瀛橈紝鏀寔澶氭璇诲彇</li>
 *   <li>{@link WebAuthFilter}锛坥rder=3锛? 璁よ瘉閴存潈锛岃В鏋?Token 骞舵瀯寤轰笂涓嬫枃</li>
 *   <li>{@link SecurityHeaderFilter}锛坥rder=鍙厤缃級- 瀹夊叏鍝嶅簲澶存敞鍏?/li>
 *   <li>{@link TraceIdResponseFilter}锛坥rder=5锛? TraceId 鍐欏叆鍝嶅簲澶?/li>
 * </ol>
 *
 * <p><b>鎷︽埅鍣ㄩ摼锛?/b>
 * <ul>
 *   <li>{@link RequestLogInterceptor}锛坥rder=MIN_VALUE锛? 璇锋眰鏃ュ織璁板綍</li>
 *   <li>{@link BaseHttpInterceptor}锛坥rder=MAX_VALUE锛? 璇锋眰缁撴潫娓呯悊锛圧equestContext锛?/li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseMvcConfiguration
 * @see WebAuthFilter
 * @see RequestLogInterceptor
 */
@AutoConfiguration
@AutoConfigureBefore({BaseAutoConfiguration.class, SafeConfiguration.class})
@EnableConfigurationProperties({WebCorsProperties.class, WebTraceProperties.class})
@Import({GlobalResponseAdvice.class, WebExceptionHandler.class})
public class WebMvcConfiguration extends BaseMvcConfiguration {

    private final BaseHttpInterceptor baseHttpInterceptor;
    private final RequestLogInterceptor requestLogInterceptor;
    @Nullable
    private final AuthenticationProvider authenticationProvider;
    private final AuthFilterConfiguration authFilterConfiguration;
    private final AuthHandlerFactory authHandlerFactory;
    private final WebTraceProperties webTraceProperties;
    private final String applicationName;

    public WebMvcConfiguration(WebCorsProperties webCorsProperties,
                               SecurityHeaderProperties securityHeaderProperties,
                               BaseHttpInterceptor baseHttpInterceptor,
                               RequestLogInterceptor requestLogInterceptor,
                               AuthFilterConfiguration authFilterConfiguration,
                               AuthHandlerFactory authHandlerFactory,
                               WebTraceProperties webTraceProperties,
                               @Nullable AuthenticationProvider authenticationProvider,
                               org.springframework.context.ApplicationContext applicationContext) {
        super(webCorsProperties);
        this.baseHttpInterceptor = baseHttpInterceptor;
        this.requestLogInterceptor = requestLogInterceptor;
        this.authFilterConfiguration = authFilterConfiguration;
        this.authHandlerFactory = authHandlerFactory;
        this.webTraceProperties = webTraceProperties;
        this.authenticationProvider = authenticationProvider;
        this.applicationName = applicationContext.getApplicationName();
    }

    protected void configureObjectMapper(ObjectMapper mapper) {
        // Web绔ぇ鏁版牸寮忓寲锛氶伩鍏嶇瀛﹁鏁版硶
        mapper.configure(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN.mappedFeature(), true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLogInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.INTERCEPTOR_REQUEST_LOG);

        registry.addInterceptor(baseHttpInterceptor)
                .addPathPatterns("/**")
                .order(BaseFilterOrders.REQUEST_CONTEXT_CLEANUP);
    }

    @Bean
    public FilterRegistrationBean<ContentCachingFilter> contentCachingFilter() {
        FilterRegistrationBean<ContentCachingFilter> bean = new FilterRegistrationBean<>(new ContentCachingFilter());
        bean.addUrlPatterns("/*");
        bean.setName("contentCachingFilter");
        bean.setOrder(BaseFilterOrders.CONTENT_CACHING_FILTER);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<WebAuthFilter> authFilter() {
        WebAuthFilter authFilter = new WebAuthFilter(
                applicationName,
                authFilterConfiguration,
                authHandlerFactory,
                authenticationProvider
        );
        FilterRegistrationBean<WebAuthFilter> authFilterBean = new FilterRegistrationBean<>(authFilter);
        authFilterBean.addUrlPatterns("/*");
        authFilterBean.setName("webAuthFilter");
        authFilterBean.setOrder(BaseFilterOrders.AUTH_FILTER);
        return authFilterBean;
    }

    @Bean
    @ConditionalOnProperty(prefix = "remi.safe.security-headers", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SecurityHeaderFilter> securityHeaderFilter(SecurityHeaderProperties securityHeaderProperties) {
        SecurityHeaderFilter securityHeaderFilter = new SecurityHeaderFilter(securityHeaderProperties);
        FilterRegistrationBean<SecurityHeaderFilter> bean = new FilterRegistrationBean<>(securityHeaderFilter);
        bean.addUrlPatterns("/*");
        bean.setName("securityHeaderFilter");
        bean.setOrder(BaseFilterOrders.SECURITY_HEADER_FILTER);
        return bean;
    }

    @Bean
    @ConditionalOnProperty(prefix = "remi.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter() {
        TraceIdResponseFilter traceIdResponseFilter = new TraceIdResponseFilter(webTraceProperties);
        FilterRegistrationBean<TraceIdResponseFilter> bean = new FilterRegistrationBean<>(traceIdResponseFilter);
        bean.addUrlPatterns("/*");
        bean.setName("traceIdResponseFilter");
        bean.setOrder(BaseFilterOrders.TRACE_ID_RESPONSE_FILTER);
        return bean;
    }
}
