package com.njydsz.system.server._support;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.njydsz.common.core.response.YdszResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Controller 测试基类（共享 MockMvc 配置与常用断言辅助）。
 *
 * <p>子类继承后获得：
 * <ul>
 *   <li>{@link #mockMvc} — Spring MVC 测试客户端</li>
 *   <li>{@link #objectMapper} — JSON 序列化工具</li>
 *   <li>{@link #expectSuccess(MvcResult)} — 断言响应 code === 'A00000'</li>
 *   <li>{@link #getJson(String)} — 构建 GET 请求（JSON accept）</li>
 *   <li>{@link #postJson(String, Object)} — 构建 POST 请求（JSON content-type + body）</li>
 * </ul>
 *
 * <pre>{@code
 * &#64;WebMvcTest(UserController.class)
 * class UserControllerTest extends BaseWebMvcTest {
 *
 *     &#64;Test
 *     void should_return_user() throws Exception {
 *         MvcResult result = mockMvc.perform(getJson("/api/user/1"))
 *                                   .andReturn();
 *         expectSuccess(result);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.26
 */
@SpringBootTest
public abstract class BaseWebMvcTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 断言响应 body 中 code 字段为 'A00000'（业务成功）。
     *
     * @param result MockMvc 执行结果
     * @throws Exception JSON 解析失败时抛出
     */
    protected void expectSuccess(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        YdszResponse<?> resp = objectMapper.readValue(body, YdszResponse.class);
        assertThat(resp.getCode()).isEqualTo("A00000");
    }

    /**
     * 构建 GET 请求（JSON accept）。
     *
     * @param url 请求 URL
     * @return MockMvc GET 请求构建器
     */
    protected MockHttpServletRequestBuilder getJson(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(url)
                .accept(MediaType.APPLICATION_JSON);
    }

    /**
     * 构建 POST 请求（JSON content-type + body）。
     *
     * @param url 请求 URL
     * @param body 请求体对象（自动序列化为 JSON）
     * @return MockMvc POST 请求构建器
     * @throws Exception JSON 序列化失败时抛出
     */
    protected MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }
}
