package com.njydsz.pmis.common.util.http;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.njydsz.pmis.common.util.classloader.ClassUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 高性能 OkHttp 工具类
 *
 * <p>功能特性：
 * 1. 单例 OkHttpClient 连接池复用
 * 2. 所有 Response 使用 try-with-resources 自动关闭
 * 3. 提供显式 buildInsecureClient() 忽略 SSL 验证（测试环境）
 * 4. 超时时间可配置
 * </p>
 *
 * <p><b>注意：</b>本工具类依赖 okhttp3，该依赖在 ydsz-pmis-common-util 中为 optional。
 * 使用本工具类前需确保项目已引入 okhttp 依赖，否则会抛出 UnsupportedOperationException。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class OkHttpUtils {

    private static final String OKHTTP_CLIENT_CLASS = "okhttp3.OkHttpClient";
    private static final boolean OKHTTP_AVAILABLE = ClassUtils.isPresent(OKHTTP_CLIENT_CLASS);

    private OkHttpUtils() {
        throw new UnsupportedOperationException("OkHttpUtils is a utility class and cannot be instantiated");
    }

    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_WRITE_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 50;
    private static final long DEFAULT_KEEP_ALIVE_MINUTES = 5;

    // Fallback defaults when not running in Spring context.
    // In Spring context, OkHttpProperties is used instead (see UtilAutoConfiguration).
    private static final long FALLBACK_CONNECT_TIMEOUT_SECONDS = Long.getLong(
            "ydsz.http.connectTimeout", DEFAULT_CONNECT_TIMEOUT_SECONDS);
    private static final long FALLBACK_READ_TIMEOUT_SECONDS = Long.getLong(
            "ydsz.http.readTimeout", DEFAULT_READ_TIMEOUT_SECONDS);
    private static final long FALLBACK_WRITE_TIMEOUT_SECONDS = Long.getLong(
            "ydsz.http.writeTimeout", DEFAULT_WRITE_TIMEOUT_SECONDS);

    private static volatile OkHttpClient client;
    private static volatile OkHttpClient insecureClient;
    private static final AtomicReference<OkHttpClient> springManagedClient = new AtomicReference<>();

    private static final MediaType JSON_MEDIA_TYPE;

    static {
        if (OKHTTP_AVAILABLE) {
            try {
                JSON_MEDIA_TYPE = createJsonMediaType();
                client = createDefaultClient();
            } catch (Exception e) {
                log.warn("Failed to initialize OkHttp client: {}", e.getMessage());
                throw new ExceptionInInitializerError(e);
            }
        } else {
            JSON_MEDIA_TYPE = null;
            client = null;
            log.debug("OkHttp not available, OkHttpUtils will be disabled");
        }
    }

    private static MediaType createJsonMediaType() {
        return MediaType.parse("application/json; charset=utf-8");
    }

    private static OkHttpClient createDefaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(FALLBACK_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(FALLBACK_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(FALLBACK_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(DEFAULT_MAX_IDLE_CONNECTIONS, DEFAULT_KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
                .build();
    }

    private static void checkAvailable() {
        if (!OKHTTP_AVAILABLE) {
            throw new UnsupportedOperationException(
                    "OkHttp is not available. Please add okhttp3 dependency to your project.");
        }
    }

    private static OkHttpClient getClient() {
        checkAvailable();
        OkHttpClient springClient = springManagedClient.get();
        return springClient != null ? springClient : client;
    }

    /**
     * 设置 Spring 管理的 OkHttpClient（由自动配置调用）
     *
     * @param okClient Spring 管理的客户端实例（okhttp3.OkHttpClient）
     */
    public static void setSpringManagedClient(OkHttpClient okClient) {
        if (okClient != null && !okClient.getClass().getName().equals("okhttp3.OkHttpClient")) {
            throw new IllegalArgumentException("Expected okhttp3.OkHttpClient, got: " + okClient.getClass().getName());
        }
        springManagedClient.set(okClient);
    }

    /**
     * 构建忽略 SSL 验证的客户端（仅非生产环境使用）
     *
     * <p>安全约束：
     * 1. 必须显式启用：系统属性 ydsz.http.insecure-enabled=true
     * 2. 禁止在生产环境（prod）使用
     * 3. 未设置 spring.profiles.active 时默认拒绝
     * </p>
     *
     * @return 忽略 SSL 的 OkHttpClient 实例
     * @throws IllegalStateException 如果环境不允许或功能未启用
     */
    public static OkHttpClient buildInsecureClient() {
        // 必须显式启用不安全模式
        boolean insecureEnabled = Boolean.getBoolean("ydsz.http.insecure-enabled");
        if (!insecureEnabled) {
            throw new IllegalStateException(
                    "buildInsecureClient() requires ydsz.http.insecure-enabled=true. "
                    + "This is disabled by default for security reasons.");
        }
        String profile = System.getProperty("spring.profiles.active",
                System.getenv("SPRING_PROFILES_ACTIVE"));
        // 未设置 profile 时默认拒绝（防御性编程）
        if (profile == null || profile.isEmpty()) {
            throw new IllegalStateException(
                    "buildInsecureClient() cannot be used when spring.profiles.active is not set. "
                    + "This is a security measure to prevent accidental use in production.");
        }
        // 禁止在生产环境使用
        if (profile.contains("prod") || profile.contains("production")) {
            throw new IllegalStateException(
                    "buildInsecureClient() must NOT be used in production! "
                    + "Current profile: " + profile);
        }
        if (insecureClient != null) {
            return insecureClient;
        }
        synchronized (OkHttpUtils.class) {
            if (insecureClient != null) {
                return insecureClient;
            }
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new SecureRandom());

                insecureClient = new OkHttpClient.Builder()
                        .connectTimeout(FALLBACK_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(FALLBACK_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .writeTimeout(FALLBACK_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .connectionPool(new ConnectionPool(DEFAULT_MAX_IDLE_CONNECTIONS, DEFAULT_KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
                        .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                        .hostnameVerifier((hostname, session) -> true)
                        .build();

                log.warn("Insecure HTTP client built, SSL verification disabled. DO NOT use in production.");
            } catch (Exception e) {
                log.error("Failed to build insecure HTTP client", e);
                throw new IllegalStateException("Failed to build insecure client", e);
            }
        }
        return insecureClient;
    }

    /**
     * 获取 OkHttp 连接池统计信息
     *
     * <p>用于监控 OkHttp 客户端的连接池状态，包括空闲连接数、总连接数和调度器排队请求数。
     * 可通过定时任务或 Micrometer Gauge 定期采集。
     *
     * @return 连接池统计 Map，包含 idleConnections、totalConnections、queuedCallsCount；如果 OkHttp 不可用返回空 Map
     */
    public static Map<String, Object> getConnectionPoolStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (!OKHTTP_AVAILABLE) {
            return stats;
        }
        try {
            OkHttpClient okClient = getClient();
            if (okClient != null) {
                stats.put("idleConnections", okClient.connectionPool().idleConnectionCount());
                stats.put("totalConnections", okClient.connectionPool().connectionCount());
                stats.put("queuedCallsCount", okClient.dispatcher().queuedCallsCount());
                stats.put("runningCallsCount", okClient.dispatcher().runningCallsCount());
            }
        } catch (Exception e) {
            log.warn("Failed to get OkHttp connection pool stats: {}", e.getMessage());
        }
        return stats;
    }

    /**
     * 关闭客户端（应用关闭时调用）
     *
     * <p>安全关闭自建的 OkHttpClient 实例。若 OkHttp 不可用（client 为 null），则跳过关闭。
     * springManagedClient 由 Spring 管理其生命周期，不做关闭。
     */
    public static void close() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
        if (insecureClient != null) {
            insecureClient.dispatcher().executorService().shutdown();
            insecureClient.connectionPool().evictAll();
        }
        // Do NOT close springManagedClient - Spring manages its lifecycle
        springManagedClient.set(null);
    }

    // ==================== GET 请求 ====================

    public static String doGet(String url, Map<String, String> param) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.get(url).newBuilder();
        if (param != null) {
            param.forEach(urlBuilder::addQueryParameter);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();

        return execute(request);
    }

    /**
     * GET 请求（带请求头）
     */
    public static String doGet(String url, Map<String, String> param, Map<String, String> headers) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.get(url).newBuilder();
        if (param != null) {
            param.forEach(urlBuilder::addQueryParameter);
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(urlBuilder.build());

        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        Request request = requestBuilder.build();
        return execute(request);
    }

    public static String doGet(String url) throws IOException {
        return doGet(url, null);
    }

    // ==================== POST 请求 ====================

    public static String doPost(String url, Map<String, String> param) throws IOException {
        FormBody.Builder formBodyBuilder = new FormBody.Builder();
        if (param != null) {
            param.forEach(formBodyBuilder::add);
        }

        Request request = new Request.Builder()
                .url(url)
                .post(formBodyBuilder.build())
                .build();

        return execute(request);
    }

    /**
     * POST 请求（带请求头）
     */
    public static String doPost(String url, Map<String, String> param, Map<String, String> headers) throws IOException {
        FormBody.Builder formBodyBuilder = new FormBody.Builder();
        if (param != null) {
            param.forEach(formBodyBuilder::add);
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(formBodyBuilder.build());

        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        Request request = requestBuilder.build();
        return execute(request);
    }

    public static String doPostJson(String url, String json) throws IOException {
        return doPostJson(url, json, null);
    }

    public static String doPostJson(String url, String json, Map<String, String> headers) throws IOException {
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(body);

        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        Request request = requestBuilder.build();
        return execute(request);
    }

    // ==================== PUT 请求 ====================

    /**
     * PUT 请求（JSON）
     */
    public static String doPutJson(String url, String json) throws IOException {
        return doPutJson(url, json, null);
    }

    public static String doPutJson(String url, String json, Map<String, String> headers) throws IOException {
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .put(body);

        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        Request request = requestBuilder.build();
        return execute(request);
    }

    // ==================== DELETE 请求 ====================

    /**
     * DELETE 请求
     */
    public static String doDelete(String url, Map<String, String> param) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.get(url).newBuilder();
        if (param != null) {
            param.forEach(urlBuilder::addQueryParameter);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .delete()
                .build();

        return execute(request);
    }

    public static String doDelete(String url) throws IOException {
        return doDelete(url, null);
    }

    // ==================== PATCH 请求 ====================

    /**
     * PATCH 请求（JSON）
     *
     * @param url     请求 URL
     * @param json    JSON 请求体
     * @return 响应体字符串
     * @throws IOException 请求异常
     */
    public static String doPatchJson(String url, String json) throws IOException {
        return doPatchJson(url, json, null);
    }

    /**
     * PATCH 请求（JSON，带请求头）
     *
     * @param url     请求 URL
     * @param json    JSON 请求体
     * @param headers 请求头
     * @return 响应体字符串
     * @throws IOException 请求异常
     */
    public static String doPatchJson(String url, String json, Map<String, String> headers) throws IOException {
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .patch(body);

        if (headers != null) {
            headers.forEach(requestBuilder::addHeader);
        }

        Request request = requestBuilder.build();
        return execute(request);
    }

    // ==================== 请求级超时 ====================

    /**
     * 执行带请求级超时的 GET 请求
     *
     * @param url              请求 URL
     * @param param            查询参数
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     * @return 响应体字符串
     * @throws IOException 请求异常
     */
    public static String doGetWithTimeout(String url, Map<String, String> param,
                                          long connectTimeoutMs, long readTimeoutMs) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.get(url).newBuilder();
        if (param != null) {
            param.forEach(urlBuilder::addQueryParameter);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();

        return executeWithTimeout(request, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 执行带请求级超时的 POST JSON 请求
     *
     * @param url              请求 URL
     * @param json             JSON 请求体
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     * @return 响应体字符串
     * @throws IOException 请求异常
     */
    public static String doPostJsonWithTimeout(String url, String json,
                                               long connectTimeoutMs, long readTimeoutMs) throws IOException {
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        return executeWithTimeout(request, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 使用请求级超时执行请求
     *
     * @param request          OkHttp 请求对象
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     * @return 响应体字符串
     * @throws IOException 请求异常
     */
    private static String executeWithTimeout(Request request, long connectTimeoutMs, long readTimeoutMs)
            throws IOException {
        OkHttpClient baseClient = getClient();
        OkHttpClient timedClient = baseClient.newBuilder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build();

        try (Response response = timedClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            ResponseBody body = response.body();
            return body != null ? body.string() : "";
        }
    }

    // ==================== 异步请求 ====================

    /**
     * 异步 GET 请求
     */
    public static void doGetAsync(String url, Map<String, String> param, Callback callback) {
        HttpUrl.Builder urlBuilder = HttpUrl.get(url).newBuilder();
        if (param != null) {
            param.forEach(urlBuilder::addQueryParameter);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .build();

        getClient().newCall(request).enqueue(callback);
    }

    /**
     * 异步 POST 请求（JSON）
     */
    public static void doPostJsonAsync(String url, String json, Callback callback) {
        RequestBody body = RequestBody.create(json, JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        getClient().newCall(request).enqueue(callback);
    }

    // ==================== 工具方法 ====================

    /**
     * 检查 URL 是否可访问
     */
    public static boolean isAccessible(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = getClient().newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.error("OkHttpUtils -> isAccessible error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 执行请求（使用默认客户端）
     */
    private static String execute(Request request) throws IOException {
        try (Response response = getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            ResponseBody body = response.body();
            return body != null ? body.string() : "";
        }
    }
}
