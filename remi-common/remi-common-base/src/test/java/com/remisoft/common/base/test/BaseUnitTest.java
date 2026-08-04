package com.remisoft.common.base.test;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 后端单元测试基类。
 *
 * <p>所有纯逻辑单元测试（不需要 Spring 容器）继承此类，获得：
 * <ul>
 *   <li>JUnit 5 生命周期管理（@TestInstance(Lifecycle.PER_CLASS)）</li>
 *   <li>Mockito 严格模式（自动校验 stubbing 是否被使用）</li>
 *   <li>统一的 @DisplayName 生成策略</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @DisplayName("用户认证服务测试")
 * class AuthServiceImplTest extends BaseUnitTest {
 *     @Mock UserRepository userRepository;
 *     @InjectMocks AuthServiceImpl authService;
 *
 *     @Test
 *     @DisplayName("登录成功应返回有效 JWT Token")
 *     void login_shouldReturnValidToken() {
 *         // given
 *         when(userRepository.findByUsername("admin")).thenReturn(Optional.of(testUser()));
 *         // when
 *         LoginVO result = authService.login(loginDTO);
 *         // then
 *         assertThat(result.getAccessToken()).isNotBlank();
 *     }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public abstract class BaseUnitTest {

    /**
     * 子类可重写，提供测试数据初始化。
     *
     * <p>等同于 @BeforeEach 的语义，但避免了 @BeforeEach 注解的样板代码。
     */
    protected void setUp() {
        // 默认空实现，子类按需重写
    }
}
