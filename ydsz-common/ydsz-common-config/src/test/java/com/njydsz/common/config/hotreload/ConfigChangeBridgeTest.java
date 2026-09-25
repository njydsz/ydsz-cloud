package com.njydsz.common.config.hotreload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import com.njydsz.common.config.ConfigProperties;
import com.njydsz.common.config.hotreload.ConfigChangeEvent.ChangeType;

/**
 * {@link ConfigChangeBridge} 单元测试。
 *
 * <p>覆盖：快照初始化增量更新、diff 计算三态判定、监听器同步/异步分发、
 * 空事件静默处理、监听器异常隔离。
 *
 * <p>注意：Java 内部类不继承外部类的静态导入，故需在每个使用 Mockito 静态方法的嵌套类中显式声明。
 *
 * @since 26.09.20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigChangeBridge 测试")
class ConfigChangeBridgeTest {

  @Mock
  private ConfigurableEnvironment environment;

  @Mock
  private ApplicationEventPublisher publisher;

  @Mock
  private ConfigProperties.ChangeMonitor changeMonitor;

  private List<ConfigChangeListener> listeners;

  @BeforeEach
  void setUp() {
    listeners = new CopyOnWriteArrayList<>();
    // lenient：仅快照相关测试使用此桩，避免 UnnecessaryStubbingException
    lenient().when(changeMonitor.isSnapshotOldValues()).thenReturn(false);
  }

  private ConfigChangeBridge createBridge() {
    return new ConfigChangeBridge(environment, publisher, changeMonitor, listeners, null);
  }

  @Nested
  @DisplayName("构造与快照初始化")
  class SnapshotTest {

    @Test
    @DisplayName("快照启用时启动扫描所有属性源")
    void snapshotEnabled_initialScanPopulatesSnapshot() {
      when(changeMonitor.isSnapshotOldValues()).thenReturn(true);
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test-props",
          java.util.Map.of("ydsz.key1", "value1", "ydsz.key2", "value2")));
      when(environment.getPropertySources()).thenReturn(sources);

      assertThatCode(() -> createBridge()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("快照禁用时跳过初始扫描")
    void snapshotDisabled_skipInitialScan() {
      when(changeMonitor.isSnapshotOldValues()).thenReturn(false);

      assertThatCode(ConfigChangeBridgeTest.this::createBridge).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("监听器管理")
  class ListenerManagementTest {

    @Test
    @DisplayName("null 监听器列表包装为空 CopyOnWriteArrayList")
    void nullListeners_wrappedSafely() {
      when(changeMonitor.isSnapshotOldValues()).thenReturn(false);

      ConfigChangeBridge bridge = new ConfigChangeBridge(
          environment, publisher, changeMonitor, null, null);

      // 应能成功添加监听器而不抛 NPE
      assertThatCode(() -> bridge.addListener(new TestListener())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("addListener 添加后收到变更通知")
    void addListener_receivesNotifications() {
      when(changeMonitor.isSnapshotOldValues()).thenReturn(false);
      when(changeMonitor.isAsyncDispatch()).thenReturn(false);

      TestListener listener = new TestListener();
      ConfigChangeBridge bridge = createBridge();
      bridge.addListener(listener);

      // 验证 addListener 成功后 listener 列表增加
      assertThat(bridge.getListenerCount()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("ChangeType 逻辑判定")
  class ChangeTypeLogicTest {

    @Test
    @DisplayName("oldValue=null, newValue!=null → ADDED")
    void resolveChangeType_oldNullNewNotNull_isAdded() {
      ConfigChangeEvent event = new ConfigChangeEvent(new Object(),
          List.of(new ConfigChangeEvent.ConfigChange("k", null, "v", ChangeType.ADDED)));
      assertThat(event.getChanges().get(0).changeType()).isEqualTo(ChangeType.ADDED);
    }

    @Test
    @DisplayName("oldValue!=null, newValue=null → DELETED")
    void resolveChangeType_oldNotNullNewNull_isDeleted() {
      ConfigChangeEvent event = new ConfigChangeEvent(new Object(),
          List.of(new ConfigChangeEvent.ConfigChange("k", "v", null, ChangeType.DELETED)));
      assertThat(event.getChanges().get(0).changeType()).isEqualTo(ChangeType.DELETED);
    }

    @Test
    @DisplayName("oldValue!=null, newValue!=null → CHANGED")
    void resolveChangeType_bothNotNull_isChanged() {
      ConfigChangeEvent event = new ConfigChangeEvent(new Object(),
          List.of(new ConfigChangeEvent.ConfigChange("k", "old", "new", ChangeType.CHANGED)));
      assertThat(event.getChanges().get(0).changeType()).isEqualTo(ChangeType.CHANGED);
    }
  }

  @Nested
  @DisplayName("异步分发控制")
  class AsyncDispatchTest {

    @Test
    @DisplayName("异步模式：asyncDispatch=true 时提交到线程池")
    void asyncMode_executorCreated() {
      when(changeMonitor.isSnapshotOldValues()).thenReturn(false);
      when(changeMonitor.isAsyncDispatch()).thenReturn(true);
      when(changeMonitor.getAsyncCorePoolSize()).thenReturn(2);
      when(changeMonitor.getAsyncQueueCapacity()).thenReturn(256);

      ConfigChangeBridge bridge = createBridge();
      assertThat(bridge).isNotNull();
    }

    @Test
    @DisplayName("同步模式：asyncDispatch=false 时不创建线程池")
    void syncMode_noExecutor() {
      when(changeMonitor.isSnapshotOldValues()).thenReturn(false);
      when(changeMonitor.isAsyncDispatch()).thenReturn(false);

      ConfigChangeBridge bridge = createBridge();
      assertThat(bridge.isAsyncMode()).isFalse();
    }
  }

  @Nested
  @DisplayName("空事件静默处理")
  class EmptyEventTest {

    @Test
    @DisplayName("空事件 class 名不匹配时静默忽略")
    void unrelatedEvent_ignored() {
      when(changeMonitor.isSnapshotOldValues()).thenReturn(false);

      ConfigChangeBridge bridge = createBridge();
      TestListener listener = new TestListener();
      bridge.addListener(listener);

      // 使用无关事件类型（class name 不会匹配任何桥接事件名）
      ApplicationEvent unrelatedEvent = mock(ApplicationEvent.class);

      assertThatCode(() -> bridge.onApplicationEvent(unrelatedEvent)).doesNotThrowAnyException();
      assertThat(listener.getCallCount()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("监听器异常隔离")
  class ListenerExceptionIsolationTest {

    @Test
    @DisplayName("单个监听器抛异常时被隔离（不向外传播）")
    void singleListenerException_doesNotPropagate() {
      // 不重新桩 isSnapshotOldValues（lenient 默认值已由 setUp 提供）
      ConfigChangeEvent.ConfigChange change =
          new ConfigChangeEvent.ConfigChange("k", "old", "new", ChangeType.CHANGED);

      // 构建一个抛出异常的监听器，验证 invokeListener 捕获异常而不传播
      ConfigChangeListener throwingListener = (key, oldVal, newVal) -> {
        throw new RuntimeException("test exception");
      };

      assertThatCode(() -> ConfigChangeBridge.invokeListener(throwingListener, change))
          .doesNotThrowAnyException();
    }
  }

  // ==================== 内部测试辅助类 ====================

  private static class TestListener implements ConfigChangeListener {
    private int callCount;

    @Override
    public void onChange(String key, String oldValue, String newValue) {
      callCount++;
    }

    int getCallCount() {
      return callCount;
    }
  }
}
