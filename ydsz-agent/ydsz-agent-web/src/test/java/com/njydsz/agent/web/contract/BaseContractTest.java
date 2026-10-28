package com.njydsz.agent.web.contract;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.server.agent.AgentFacade;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.agent.web.controller.AgentController;
import com.njydsz.common.json.spring.JsonHttpMessageConverter;
import com.njydsz.common.socket.push.SsePushChannelFactory;

/**
 * Spring Cloud Contract 生成测试的基类（`src/test/resources/contracts/*.yml` → 生成测试 → 继承本类）。
 *
 * <p><b>为什么不用 {@code @SpringBootTest}：</b>agent-web 的启动链需要 Nacos 配置/服务发现与数据库，
 * 在单测环境里起不来（仓库里也没有 {@code application-contract.yml}）；即便能起来，契约测试要断言的也只是
 * "HTTP 契约 + 序列化形状"，不该被中间件可用性绑架。故改用 {@code standaloneSetup} + 受控协作者。
 *
 * <p><b>为什么必须显式设置 {@link JsonHttpMessageConverter}：</b>生产链路里 MVC 序列化走 YdszJson
 * （见 {@code JsonAutoConfiguration}），它对未标注 {@code @JsonView} 的字段按 Jackson
 * {@code DEFAULT_VIEW_INCLUSION=true} 语义输出 —— 这决定了 {@code code}/{@code msg}/{@code data} 这层信封
 * 是否会出现。而 {@code MockMvcBuilders.standaloneSetup()} 默认装配的是 Jackson 转换器（该特性为 false），
 * 若不显式替换，测试断言的就不是线上真实形状。
 *
 * <p><b>协作者是 mock：</b>本测试钉住的是 Controller ↔ HTTP 报文这一层（路径、方法、信封、字段名、视图裁剪）；
 * Service 内部行为由 `ydsz-agent-server` 的单测覆盖。mock 的返回值是契约里约定的响应内容，不是"实现照抄期望"——
 * 一旦 Controller 改名/改包/改视图，生成测试会立刻红。
 *
 * @author ydsz-team
 * @since 26.10.14
 */
public abstract class BaseContractTest {

  /** 与 {@code agent-chat.yml} 中 {@code data.content} 的期望值保持一致 */
  protected static final String CONTRACT_REPLY = "你好！有什么可以帮你的？";

  /** 契约里约定的模型名，用来证明 model 字段确实透传 */
  protected static final String CONTRACT_MODEL = "contract-model";

  /**
   * 为每个生成测试准备 MockMvc：真实 Controller + 平台 JSON 转换器 + 受控协作者。
   */
  @BeforeEach
  void setUpContractContext() {
    AgentFacade agentFacade = mock(AgentFacade.class);
    when(agentFacade.chat(anyString(), nullable(String.class), nullable(String.class)))
        .thenReturn(
            new ChatResponse(
                "contract-response",
                CONTRACT_MODEL,
                ChatMessage.assistant(CONTRACT_REPLY, "conv-001", null),
                null,
                "stop",
                List.of()));

    AgentController controller =
        new AgentController(
            mock(SsePushChannelFactory.class), agentFacade, guardProvider(mock(AgentRequestGuard.class)));

    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new JsonHttpMessageConverter())
            .build();
    RestAssuredMockMvc.mockMvc(mockMvc);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<AgentRequestGuard> guardProvider(AgentRequestGuard guard) {
    ObjectProvider<AgentRequestGuard> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(guard);
    return provider;
  }
}
