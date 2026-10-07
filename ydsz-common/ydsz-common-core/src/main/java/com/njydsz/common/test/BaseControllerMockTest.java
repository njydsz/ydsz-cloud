package com.njydsz.common.test;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.JsonMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller Smoke Test 基础类（独立 MockMvc 模式）。
 *
 * <p>设计目标：
 *
 * <ul>
 *   <li>不启动嵌入式容器（无端口绑定），仅通过 {@link MockMvcBuilders#standaloneSetup(Object...)} 装配目标
 *       Controller，运行速度快、依赖少
 *   <li>不依赖 Testcontainers，CI 环境零中间件即可运行 {@code mvn test}
 * </ul>
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * class MyControllerSmokeTest extends BaseControllerMockTest {
 *
 *   private MockMvc mockMvc;
 *
 *   &#64;Mock
 *   private MyService myService;
 *
 *   &#64;InjectMocks
 *   private MyController myController;
 *
 *   &#64;BeforeEach
 *   void setUp() {
 *     mockMvc = standaloneSetup(myController);
 *   }
 *
 *   &#64;Test
 *   void page_shouldReturn200() throws Exception {
 *     // Given / When / Then ...
 *   }
 * }
 * }</pre>
 *
 * <p><b>约定：</b>成功响应码统一为 {@value YdszResponse#SUCCESS}（A00000）。
 *
 * @author ydsz-smoke-test
 * @since 26.10.01
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseControllerMockTest {

  /**
   * 构建独立的 MockMvc 实例（不启动嵌入式容器）。
   *
   * <p>通常配合 {@code @InjectMocks + @Mock} 使用。全局异常处理器可通过
   * {@code setHandlerExceptionResolvers} 手动装配。
   *
   * @param controllers 待测试的目标 Controller 实例数组
   * @return 独立的 MockMvc 实例
   */
  protected MockMvc standaloneSetup(Object... controllers) {
    return MockMvcBuilders.standaloneSetup(controllers).build();
  }

  /**
   * 获取项目统一 JSON 序列化器实例。
   *
   * <p>子类需要序列化请求体 / 解析响应体时使用。返回不可变的默认实例，线程安全。
   *
   * @return 项目统一 JsonMapper 实例
   */
  protected JsonMapper jsonMapper() {
    return JsonMapper.getDefault();
  }

  /**
   * 成功响应码常量（{@value YdszResponse#SUCCESS}）。
   *
   * <p>子类断言时建议直接使用此常量而非硬编码字符串。
   */
  protected static final String SUCCESS_CODE = YdszResponse.SUCCESS;
}
