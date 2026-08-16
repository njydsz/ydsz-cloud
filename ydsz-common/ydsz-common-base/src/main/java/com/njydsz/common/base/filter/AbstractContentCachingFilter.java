package com.njydsz.common.base.filter;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * 请求体缓存过滤器抽象基类
 *
 * <p>包装请求为 {@link ContentCachingRequestWrapper}，支持请求体多次读取。
 * 跳过 multipart 请求，避免大文件上传场景下的内存问题。</p>
 *
 * <p>默认缓存容量 512KB，超过部分不缓存（不会 OOM）。可由子类构造器自定义。</p>
 *
 * <p>Web 端和 App 端继承此基类即可，无需重复实现。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractContentCachingFilter extends OncePerRequestFilter {

    /**
     * 默认缓存容量（字节，512KB）。
     *
     * <p>说明：ContentCachingRequestWrapper 在超过 cacheCapacity 时不会抛错，
     * 只是截断缓存。512KB 适合典型 JSON 请求体；如需支持更大请求，
     * 可由子类传入更大的 cacheCapacity。</p>
     */
    protected static final int DEFAULT_CACHE_CAPACITY = 512 * 1024;

    /**
     * 缓存容量（字节）
     */
    private final int cacheCapacity;

    /**
     * 使用默认缓存容量构造
     */
    protected AbstractContentCachingFilter() {
        this(DEFAULT_CACHE_CAPACITY);
    }

    /**
     * 使用指定缓存容量构造
     *
     * @param cacheCapacity 缓存容量（字节）
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
     * 判断是否为 multipart 请求
     */
    protected boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * 获取缓存容量（可由子类重写以支持动态配置）
     *
     * @return 缓存容量（字节）
     */
    protected int getCacheCapacity() {
        return cacheCapacity;
    }
}
