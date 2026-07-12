package com.njydsz.pmis.common.util.http;

import com.njydsz.pmis.common.util.concurrent.ExecutorUtils;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * OkHttpClient 单例工厂
 *
 * <p>提供：
 * <ol>
 *   <li>连接池复用（避免每次请求新建连接）</li>
 *   <li>DNS 双栈（IPv4 + IPv6）</li>
 *   <li>统一超时配置（connect/read/write/call）</li>
 *   <li>可插拔的拦截器（TraceId、Logging、Auth）</li>
 *   <li>对 Spring 容器的友好集成（通过 {@link ObjectProvider} 注入拦截器）</li>
 * </ol>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * OkHttpClient client = HttpClientFactory.create();
 * Request request = new Request.Builder().url("https://api.example.com").build();
 * try (Response response = client.newCall(request).execute()) {
 *     String body = response.body().string();
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public final class HttpClientFactory {

    private static volatile OkHttpClient DEFAULT_CLIENT;

    private HttpClientFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 使用默认配置获取 OkHttpClient 单例
     *
     * @return OkHttpClient 实例
     */
    public static OkHttpClient getDefault() {
        OkHttpClient local = DEFAULT_CLIENT;
        if (local == null) {
            synchronized (HttpClientFactory.class) {
                local = DEFAULT_CLIENT;
                if (local == null) {
                    DEFAULT_CLIENT = local = newBuilder().build();
                }
            }
        }
        return local;
    }

    /**
     * 重置默认单例（测试场景）
     */
    public static void resetDefault() {
        synchronized (HttpClientFactory.class) {
            DEFAULT_CLIENT = null;
        }
    }

    /**
     * 创建一个新的 OkHttpClient Builder（自定义配置）
     *
     * @return OkHttpClient.Builder 实例
     */
    public static OkHttpClient.Builder newBuilder() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .writeTimeout(Duration.ofSeconds(5))
                .callTimeout(Duration.ofSeconds(10))
                .connectionPool(new ConnectionPool(200, 5, TimeUnit.MINUTES))
                .dispatcher(newDispatcher())
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true);
    }

    /**
     * 创建 OkHttpClient 实例（自定义 + 注入拦截器）
     *
     * @param interceptors 拦截器提供者（Spring）
     * @return OkHttpClient 实例
     */
    public static OkHttpClient create(ObjectProvider<okhttp3.Interceptor> interceptors) {
        OkHttpClient.Builder builder = newBuilder();
        if (interceptors != null) {
            interceptors.orderedStream().forEach(builder::addInterceptor);
        }
        return builder.build();
    }

    private static Dispatcher newDispatcher() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) ExecutorUtils.newCachedThreadPool("ydsz-okhttp-");
        return new Dispatcher(executor);
    }
}
