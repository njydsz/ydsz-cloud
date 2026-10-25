package com.njydsz.agent.web.contract;

import com.njydsz.agent.web.AgentApplication;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.WebApplicationContext;

/**
 * Spring Cloud Contract 基类。
 *
 * <p>所有自动生成的 Contract 测试类应继承本类，以获得统一的 Spring Context 装配和 RestAssured MockMvc 初始化。
 *
 * <p>使用 {@code @SpringBootTest} + {@code WebAppConfiguration} 加载完整的应用上下文，确保 Controller、Service、Repository
 * 全链路可测。Contract 文件位于 {@code src/test/resources/contracts/} 目录。
 *
 * <p>使用方式：在子模块的 pom.xml 中添加 {@code spring-cloud-starter-contract-verifier} 依赖，
 * Maven Plugin 会自动从 contracts 目录生成继承本基类的测试类。
 *
 * <h3>约定</h3>
 *
 * <ul>
 *   <li>Contract 文件命名：{@code {业务场景}.{yml|groovy}}</li>
 *   <li>请求路径与实际 {@code @PostMapping} / {@code @GetMapping} 保持一致</li>
 *   <li>响应 status/body.code 与 {@code YdszResponse} 结构保持一致</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.14
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = AgentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("contract")
public abstract class BaseContractTest {

  @Autowired
  private WebApplicationContext context;

  /**
   * 在每个测试方法执行前初始化 RestAssured MockMvc。
   *
   * <p>自动扫描 Spring Context 中的所有 {@code @Controller} 和 {@code @RestController} Bean，
   * 构建模拟的 Servlet 环境用于 Contract 验证。
   */
  @BeforeEach
  public void setUp() {
    RestAssuredMockMvc.webAppContextSetup(context);
  }
}
