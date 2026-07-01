package com.njydsz.pmis.common.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SentinelAutoConfiguration 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SentinelAutoConfiguration 测试")
class SentinelAutoConfigurationTest {

    @Test
    @DisplayName("BlockExceptionHandler 处理 FlowException 返回 429")
    void blockHandler_flowException() throws Exception {
        SentinelAutoConfiguration config = new SentinelAutoConfiguration();
        BlockExceptionHandler handler = config.sentinelBlockExceptionHandler();

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(null, response, new FlowException("test", "default"));

        assertThat(response.getStatus()).isEqualTo(429);
        String body = response.getContentAsString();
        assertThat(body).contains("请求频率超限");
    }

    @Test
    @DisplayName("BlockExceptionHandler 处理 DegradeException 返回 503")
    void blockHandler_degradeException() throws Exception {
        SentinelAutoConfiguration config = new SentinelAutoConfiguration();
        BlockExceptionHandler handler = config.sentinelBlockExceptionHandler();

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(null, response,
                new com.alibaba.csp.sentinel.slots.block.degrade.DegradeException("test", "default", 1));

        assertThat(response.getStatus()).isEqualTo(503);
        String body = response.getContentAsString();
        assertThat(body).contains("服务降级");
    }

    @Test
    @DisplayName("BlockExceptionHandler 返回 JSON 含 code 字段")
    void blockHandler_jsonFormat() throws Exception {
        SentinelAutoConfiguration config = new SentinelAutoConfiguration();
        BlockExceptionHandler handler = config.sentinelBlockExceptionHandler();

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(null, response, new FlowException("test", "default"));

        String body = response.getContentAsString();
        assertThat(body).contains("\"code\"");
        assertThat(body).contains("\"message\"");
    }
}
