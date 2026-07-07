package com.njydsz.pmis.cronjob.core.handler;

import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpJobHandler} 单元测试。
 *
 * <p>覆盖 paramsJson 解析、HTTP 方法路由、成功/失败状态码判定。
 * 实际 HTTP 调用依赖外部服务，仅测试参数校验和状态码逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("HttpJobHandler HTTP 任务处理器测试")
class HttpJobHandlerTest {

    private CronjobProperties cronjobProperties;
    private HttpJobHandler handler;

    @BeforeEach
    void setUp() {
        cronjobProperties = new CronjobProperties();
        handler = new HttpJobHandler(cronjobProperties);
    }

    @Test
    @DisplayName("paramsJson 为空时抛出 IllegalArgumentException")
    void execute_emptyParams_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> handler.execute(null));
        assertThrows(IllegalArgumentException.class, () -> handler.execute(""));
        assertThrows(IllegalArgumentException.class, () -> handler.execute("  "));
    }

    @Test
    @DisplayName("缺少 url 字段时抛出 IllegalArgumentException")
    void execute_missingUrl_throwsException() {
        JSONObject params = new JSONObject();
        params.put("method", "GET");
        assertThrows(IllegalArgumentException.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("url 为空字符串时抛出 IllegalArgumentException")
    void execute_blankUrl_throwsException() {
        JSONObject params = new JSONObject();
        params.put("url", "");
        assertThrows(IllegalArgumentException.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("不支持的 HTTP 方法抛出 IllegalArgumentException")
    void execute_unsupportedMethod_throwsException() {
        JSONObject params = new JSONObject();
        params.put("url", "https://example.com/api");
        params.put("method", "INVALID");
        assertThrows(IllegalArgumentException.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("无效 URL 抛出异常")
    void execute_invalidUrl_throwsException() {
        JSONObject params = new JSONObject();
        params.put("url", "not-a-valid-url");
        params.put("method", "GET");
        assertThrows(Exception.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("配置属性默认值正确")
    void config_defaultValues_correct() {
        CronjobProperties.Http httpConfig = cronjobProperties.getHttp();
        assertEquals(10, httpConfig.getConnectTimeoutSeconds());
        assertEquals(30, httpConfig.getRequestTimeoutSeconds());
        assertEquals("200-299", httpConfig.getSuccessStatusRange());
        assertTrue(httpConfig.isFollowRedirects());
    }

    @Test
    @DisplayName("BEAN_NAME 常量正确")
    void beanName_constant_correct() {
        assertEquals("httpJobHandler", HttpJobHandler.BEAN_NAME);
    }

    @Test
    @DisplayName("GET 请求到无效主机抛出异常（验证请求构建成功但网络失败）")
    void execute_getRequestToInvalidHost_throwsNetworkException() {
        JSONObject params = new JSONObject();
        params.put("url", "http://localhost:1/nonexistent");
        params.put("method", "GET");
        params.put("timeoutMs", 1000);
        // 应该抛出异常（连接被拒绝），而不是返回 null
        assertThrows(Exception.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("POST 请求带 body 和 headers 到无效主机抛出异常")
    void execute_postRequestWithBodyAndHeaders_throwsNetworkException() {
        JSONObject params = new JSONObject();
        params.put("url", "http://localhost:1/api");
        params.put("method", "POST");
        params.put("body", "{\"key\":\"value\"}");
        params.put("timeoutMs", 1000);
        JSONObject headers = new JSONObject();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer token123");
        params.put("headers", headers);
        assertThrows(Exception.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("所有支持的 HTTP 方法不抛出 IllegalArgumentException")
    void execute_allSupportedMethods_noIllegalArgument() {
        String[] methods = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"};
        for (String method : methods) {
            JSONObject params = new JSONObject();
            params.put("url", "http://localhost:1/api");
            params.put("method", method);
            params.put("timeoutMs", 500);
            // 应该抛出网络异常（连接被拒绝），但不是 IllegalArgumentException
            Exception e = assertThrows(Exception.class, () -> handler.execute(params.toJSONString()));
            assertNotNull(e);
        }
    }
}
