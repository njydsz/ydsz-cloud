package com.njydsz.pmis.common.base.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 璇锋眰浣撶紦瀛樿繃婊ゅ櫒鎶借薄鍩虹被
 *
 * <p>鍖呰璇锋眰涓?{@link ContentCachingRequestWrapper}锛屾敮鎸佽姹備綋澶氭璇诲彇銆?
 * 璺宠繃 multipart 璇锋眰锛岄伩鍏嶅ぇ鏂囦欢涓婁紶鍦烘櫙涓嬬殑鍐呭瓨闂銆?/p>
 *
 * <p>榛樿缂撳瓨瀹归噺 512KB锛岃秴杩囬儴鍒嗕笉缂撳瓨锛堜笉浼?OOM锛夈€傚彲鐢卞瓙绫绘瀯閫犲櫒鑷畾涔夈€?/p>
 *
 * <p>Web 绔拰 App 绔户鎵挎鍩虹被鍗冲彲锛屾棤闇€閲嶅瀹炵幇銆?/p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public abstract class AbstractContentCachingFilter extends OncePerRequestFilter {

    /**
     * 榛樿缂撳瓨瀹归噺锛堝瓧鑺傦紝512KB锛夈€?
     *
     * <p>璇存槑锛欳ontentCachingRequestWrapper 鍦ㄨ秴杩?cacheCapacity 鏃朵笉浼氭姏閿欙紝
     * 鍙槸鎴柇缂撳瓨銆?12KB 閫傚悎鍏稿瀷 JSON 璇锋眰浣擄紱濡傞渶鏀寔鏇村ぇ璇锋眰锛?
     * 鍙敱瀛愮被浼犲叆鏇村ぇ鐨?cacheCapacity銆?/p>
     */
    protected static final int DEFAULT_CACHE_CAPACITY = 512 * 1024;

    /**
     * 缂撳瓨瀹归噺锛堝瓧鑺傦級
     */
    private int cacheCapacity;

    /**
     * 浣跨敤榛樿缂撳瓨瀹归噺鏋勯€?
     */
    protected AbstractContentCachingFilter() {
        this(DEFAULT_CACHE_CAPACITY);
    }

    /**
     * 浣跨敤鎸囧畾缂撳瓨瀹归噺鏋勯€?
     *
     * @param cacheCapacity 缂撳瓨瀹归噺锛堝瓧鑺傦級
     */
    protected AbstractContentCachingFilter(int cacheCapacity) {
        this.cacheCapacity = cacheCapacity > 0 ? cacheCapacity : DEFAULT_CACHE_CAPACITY;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (isMultipart(request)) {
            filterChain.doFilter(request, response);
        } else {
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, getCacheCapacity());
            filterChain.doFilter(wrappedRequest, response);
        }
    }

    /**
     * 鍒ゆ柇鏄惁涓?multipart 璇锋眰
     */
    protected boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * 鑾峰彇缂撳瓨瀹归噺锛堝彲鐢卞瓙绫婚噸鍐欎互鏀寔鍔ㄦ€侀厤缃級
     *
     * @return 缂撳瓨瀹归噺锛堝瓧鑺傦級
     */
    protected int getCacheCapacity() {
        return cacheCapacity;
    }
}