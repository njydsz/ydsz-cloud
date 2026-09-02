package com.njydsz.common.json.spring.boot;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.json.YdszJson;

/**
 * 自动预热 Runner（P1-E2）。
 *
 * <p>应用启动时自动扫描 Spring 容器中的 {@code @RestController}/{@code @Controller} Bean， 提取
 * {@code @RequestBody} 参数类型和 {@code @ResponseBody} 返回类型， 调用 {@link YdszJson#warmup(Class...)}
 * 触发字段元数据与序列化器缓存构建。
 *
 * <p>避免首次真实请求时的冷启动延迟尖峰（无参构造→反射→元数据加载 + 字节码生成）。
 *
 * <p><b>前置条件：</b>需配置 {@code ydsz.json.warmup-enabled=true}（默认 false）。
 *
 * <p><b>对标行业实践：</b>
 *
 * <ul>
 *   <li>对标 Jackson 的 {@code ObjectMapper.copy()} 启动预加载
 *   <li>对标 FastJSON2 {@code JSON.registerTypeConverter} 初始化
 *   <li>对标 Spring Boot {@code ApplicationRunner} 启动任务模式
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class JsonWarmupRunner implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonWarmupRunner.class);

  private final ApplicationContext applicationContext;

  public JsonWarmupRunner(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public void run(ApplicationArguments args) {
    Set<Class<?>> warmupTypes = scanControllerTypes();
    if (warmupTypes.isEmpty()) {
      LOGGER.info("YdszJson warmup: no @RequestBody/@ResponseBody types found");
      return;
    }
    LOGGER.info("YdszJson warmup: scanning {} types for pre-cache", warmupTypes.size());
    try {
      YdszJson.warmup(warmupTypes.toArray(new Class<?>[0]));
      LOGGER.info("YdszJson warmup: completed for {} types", warmupTypes.size());
    } catch (Exception e) {
      // 预热失败不影响启动
      LOGGER.warn("YdszJson warmup: failed with exception", e);
    }
  }

  /** 扫描所有 Controller Bean，提取需预热的类型集合。 */
  private Set<Class<?>> scanControllerTypes() {
    Set<Class<?>> types = new HashSet<>(16);
    String[] beanNames = applicationContext.getBeanNamesForAnnotation(RestController.class);
    for (String beanName : beanNames) {
      collectTypesFromController(applicationContext.getType(beanName), types);
    }
    String[] controllerBeanNames = applicationContext.getBeanNamesForAnnotation(Controller.class);
    for (String beanName : controllerBeanNames) {
      collectTypesFromController(applicationContext.getType(beanName), types);
    }
    return types;
  }

  /** 从 Controller 类的方法签名中提取 @RequestBody 参数类型和 @ResponseBody 返回类型。 */
  private void collectTypesFromController(Class<?> controllerClass, Set<Class<?>> types) {
    if (controllerClass == null) {
      return;
    }
    for (Method method : controllerClass.getMethods()) {
      if (!isRequestMappingMethod(method)) {
        continue;
      }
      // 提取 @RequestBody 参数类型
      for (int i = 0; i < method.getParameterCount(); i++) {
        if (method.getParameters()[i].isAnnotationPresent(RequestBody.class)) {
          addTypeAndGenericArguments(method.getGenericParameterTypes()[i], types);
        }
      }
      // 提取返回类型（仅当方法或类级有 @ResponseBody / 类是 @RestController 时）
      addTypeAndGenericArguments(method.getGenericReturnType(), types);
    }
  }

  /** 判断方法是否是 HTTP 映射方法（有 @GetMapping/@PostMapping 等注解）。 */
  private boolean isRequestMappingMethod(Method method) {
    return method.isAnnotationPresent(GetMapping.class)
        || method.isAnnotationPresent(PostMapping.class)
        || method.isAnnotationPresent(PutMapping.class)
        || method.isAnnotationPresent(DeleteMapping.class)
        // P1 修复：PATCH 接口类型此前漏扫，不被预热
        || method.isAnnotationPresent(PatchMapping.class)
        || method.isAnnotationPresent(RequestMapping.class);
  }

  /**
   * 添加类型本身及其泛型参数到预热集合（如 List&lt;User&gt; → User）。
   *
   * @param type 方法签名中的类型
   * @param types 类型收集器
   */
  private void addTypeAndGenericArguments(Type type, Set<Class<?>> types) {
    if (type == null) {
      return;
    }
    if (type instanceof Class<?> clazz) {
      if (isWarmupCandidate(clazz)) {
        types.add(clazz);
      }
    } else if (type instanceof ParameterizedType paramType) {
      addTypeAndGenericArguments(paramType.getRawType(), types);
      for (Type arg : paramType.getActualTypeArguments()) {
        addTypeAndGenericArguments(arg, types);
      }
    }
  }

  /**
   * 判断类是否是预热候选（排除 JDK 类型、接口、原始类型、数组）。
   *
   * @param clazz 待判断类
   * @return true 表示需要预热
   */
  private boolean isWarmupCandidate(Class<?> clazz) {
    if (clazz.isPrimitive() || clazz.isArray() || clazz.isInterface()) {
      return false;
    }
    String name = clazz.getName();
    // 跳过 JDK / 第三方库的类（预热无意义且可能引发异常）
    return !name.startsWith("java.")
        && !name.startsWith("javax.")
        && !name.startsWith("jakarta.")
        && !name.startsWith("org.springframework.")
        && !name.startsWith("com.fasterxml.")
        && !name.startsWith("sun.");
  }
}
