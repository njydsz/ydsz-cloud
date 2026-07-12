package com.njydsz.pmis.common.auth.filter;

import com.njydsz.pmis.common.auth.config.AuthFilterConfiguration;
import com.njydsz.pmis.common.core.constant.FilterIgnoreConstant;
import com.njydsz.pmis.common.util.auth.AuthInfo;
import com.njydsz.pmis.common.util.auth.RequestHolder;
import com.njydsz.pmis.common.util.url.UrlPathUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 璁よ瘉杩囨护鍣ㄦ娊璞″熀绫? *
 * <p>鎻愬彇 Web 绔拰 App 绔璇佽繃婊ゅ櫒鐨勫叕鍏遍€昏緫銆?/p>
 *
 * @author Marvin Lee
 * @version 3.5.0
 */
@Slf4j
public abstract class BaseAuthFilter extends OncePerRequestFilter {

    protected final String applicationName;
    protected final AuthFilterConfiguration authFilterConfiguration;

    public BaseAuthFilter(String applicationName, AuthFilterConfiguration authFilterConfiguration) {
        this.applicationName = applicationName;
        this.authFilterConfiguration = authFilterConfiguration;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String servletPath = request.getServletPath();
        doPreAuth(request, response);
        if (shouldSkipAuth(request)) {
            log.debug("{}[璺宠繃璁よ瘉] 璇锋眰璺緞: {}", getLogPrefix(), servletPath);
            filterChain.doFilter(request, response);
            return;
        }
        long startTime = System.currentTimeMillis();
        AuthInfo authInfo = resolveAuthInfo(request, response);
        log.debug("{}璇锋眰璺緞: {}, 璁よ瘉淇℃伅宸插啓鍏ヤ笂涓嬫枃", getLogPrefix(), servletPath);
        RequestHolder.add(authInfo);
        RequestHolder.add(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestHolder.remove();
            doPostAuth(request, response, System.currentTimeMillis() - startTime);
        }
    }

    protected boolean shouldSkipAuth(HttpServletRequest request) {
        if (shouldSkipService()) {
            return true;
        }
        Set<String> ignoreUrl = authFilterConfiguration.getAllIgnoreUrls();
        return UrlPathUtils.isIgnoreUrl(ignoreUrl, request.getServletPath());
    }

    protected abstract AuthInfo resolveAuthInfo(HttpServletRequest request, HttpServletResponse response);

    protected abstract boolean shouldSkipService();

    protected abstract String getLogPrefix();

    protected void doPreAuth(HttpServletRequest request, HttpServletResponse response) {
    }

    protected void doPostAuth(HttpServletRequest request, HttpServletResponse response, long duration) {
    }

    protected boolean isServiceIgnored(String appName) {
        if (appName == null) {
            return false;
        }
        return FilterIgnoreConstant.getAuthFilterIgnoreServiceNames().contains(appName);
    }
}
