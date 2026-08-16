package com.njydsz.common.json.provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jackson 注解兼容桥（P1-8）。
 *
 * <p><b>解决迁移期静默失效问题：</b>本模块注解与 Jackson 同名 （{@code @JsonProperty} / {@code @JsonIgnore} 等），从 Jackson
 * 迁移到 YdszJson 的 业务代码若未批量替换 import，注解会因包名不同而静默失效（字段名、忽略规则不生效）。 本桥通过反射读取 Jackson 注解（类名反射解析，<b>不
 * import 任何 Jackson 类</b>， 不触发 checkstyle JSON 生态红线），在原生注解缺失时以 Jackson 注解语义兜底。
 *
 * <p><b>优先级规则：</b>原生注解（{@code com.njydsz.common.json.annotation}）优先， Jackson 注解仅在原生注解未标注时生效。对标
 * FastJSON2 的 {@code JSONReader.Feature.SupportAutoType} 式兼容策略。
 *
 * <p><b>兼容范围（Jackson annotations 2.x）：</b>
 *
 * <ul>
 *   <li>{@code @JsonProperty.value()} - 字段重命名（读/写双向）
 *   <li>{@code @JsonIgnore} - 字段忽略（读/写双向）
 *   <li>{@code @JsonIgnoreProperties.value()} - 类级字段忽略
 *   <li>{@code @JsonValue} - 方法级整体值序列化（枚举常用）
 *   <li>{@code @JsonAlias.value()} - 反序列化多命名兼容
 * </ul>
 *
 * <p><b>运行期可选：</b>jackson-annotations 不在 classpath 时桥自动禁用 （{@link #isAvailable()} 返回
 * false），所有方法返回空值，零开销降级。
 *
 * @author ydsz-team
 * @since 1.2.3
 */
public final class JacksonAnnotationBridge {

  private static final Logger LOGGER = LoggerFactory.getLogger(JacksonAnnotationBridge.class);

  /** Jackson annotations 包前缀（仅用于类名拼接，不构成依赖） */
  private static final String JACKSON_ANNOTATION_PACKAGE = "com.fasterxml.jackson.annotation.";

  /** 桥生效提示只打印一次 */
  private static final AtomicBoolean AVAILABILITY_LOGGED = new AtomicBoolean(false);

  private static final Class<? extends Annotation> JSON_PROPERTY = resolve("JsonProperty");
  private static final Class<? extends Annotation> JSON_IGNORE = resolve("JsonIgnore");
  private static final Class<? extends Annotation> JSON_IGNORE_PROPERTIES =
      resolve("JsonIgnoreProperties");
  private static final Class<? extends Annotation> JSON_VALUE = resolve("JsonValue");
  private static final Class<? extends Annotation> JSON_ALIAS = resolve("JsonAlias");

  /** jackson-annotations 是否在运行期 classpath 上 */
  private static final boolean AVAILABLE = JSON_PROPERTY != null;

  private JacksonAnnotationBridge() {
    throw new UnsupportedOperationException("JacksonAnnotationBridge is a utility class");
  }

  /**
   * 判断 Jackson 注解是否可用（classpath 上存在 jackson-annotations）。
   *
   * <p>首次检测到可用时打印一次 INFO 日志，提示兼容桥已激活及优先级规则。
   *
   * @return true 表示桥可用，所有读取方法按 Jackson 注解兜底
   */
  public static boolean isAvailable() {
    if (AVAILABLE && AVAILABILITY_LOGGED.compareAndSet(false, true)) {
      LOGGER.info(
          "Jackson annotations detected on classpath; "
              + "JacksonAnnotationBridge enabled (native ydsz annotations take precedence)");
    }
    return AVAILABLE;
  }

  /**
   * 读取字段上的 Jackson {@code @JsonProperty.value()}。
   *
   * @param field 目标字段
   * @return 重命名值；未标注或 value 为空返回 null
   */
  public static String propertyName(Field field) {
    return stringValue(field, JSON_PROPERTY, "value");
  }

  /**
   * 判断字段是否标注了 Jackson {@code @JsonIgnore}。
   *
   * @param field 目标字段
   * @return true 表示应忽略该字段
   */
  public static boolean isIgnored(Field field) {
    return AVAILABLE && field.getAnnotation(JSON_IGNORE) != null;
  }

  /**
   * 判断构造器/方法参数是否标注了 Jackson {@code @JsonProperty}。
   *
   * @param param 目标参数
   * @return true 表示已标注
   */
  public static boolean hasJsonProperty(AnnotatedElement param) {
    return AVAILABLE && param.getAnnotation(JSON_PROPERTY) != null;
  }

  /**
   * 读取参数上 Jackson {@code @JsonProperty.value()}。
   *
   * @param param 目标参数
   * @return 参数名；未标注或 value 为空返回 null
   */
  public static String parameterName(AnnotatedElement param) {
    return stringValue(param, JSON_PROPERTY, "value");
  }

  /**
   * 读取类上 Jackson {@code @JsonIgnoreProperties.value()} 忽略列表。
   *
   * @param clazz 目标类
   * @return 忽略的字段名数组；未标注返回空数组（不为 null）
   */
  public static String[] ignoreProperties(Class<?> clazz) {
    if (!AVAILABLE) {
      return new String[0];
    }
    Annotation annotation = clazz.getAnnotation(JSON_IGNORE_PROPERTIES);
    if (annotation == null) {
      return new String[0];
    }
    try {
      String[] value = (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
      return value != null ? value : new String[0];
    } catch (Exception e) {
      LOGGER.debug(
          "JacksonAnnotationBridge failed to read @JsonIgnoreProperties on {}: {}",
          clazz.getName(),
          e.toString());
      return new String[0];
    }
  }

  /**
   * 查找类中标注了 Jackson {@code @JsonValue} 的方法（不含父类，与原生扫描策略一致）。
   *
   * @param clazz 目标类
   * @return 标注方法，未找到返回 null
   */
  public static Method findJsonValueMethod(Class<?> clazz) {
    if (!AVAILABLE) {
      return null;
    }
    for (Method method : clazz.getDeclaredMethods()) {
      if (method.isAnnotationPresent(JSON_VALUE)) {
        try {
          method.setAccessible(true);
        } catch (Exception ignored) {
          // setAccessible 失败（模块封闭等）时仍返回方法，调用期再失败
        }
        return method;
      }
    }
    return null;
  }

  /**
   * 读取字段上 Jackson {@code @JsonAlias.value()} 别名列表。
   *
   * @param field 目标字段
   * @return 别名数组；未标注返回空数组（不为 null）
   */
  public static String[] aliases(Field field) {
    if (!AVAILABLE) {
      return new String[0];
    }
    Annotation annotation = field.getAnnotation(JSON_ALIAS);
    if (annotation == null) {
      return new String[0];
    }
    try {
      String[] value = (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
      return value != null ? value : new String[0];
    } catch (Exception e) {
      LOGGER.debug(
          "JacksonAnnotationBridge failed to read @JsonAlias on {}.{}: {}",
          field.getDeclaringClass().getName(),
          field.getName(),
          e.toString());
      return new String[0];
    }
  }

  /**
   * 反射解析 Jackson 注解类（不存在返回 null，触发桥禁用降级）。
   *
   * @param simpleName Jackson 注解简单类名（如 {@code "JsonProperty"}）
   * @return 注解 Class；无法解析时返回 null
   */
  private static Class<? extends Annotation> resolve(String simpleName) {
    try {
      Class<?> clazz = Class.forName(JACKSON_ANNOTATION_PACKAGE + simpleName);
      return clazz.asSubclass(Annotation.class);
    } catch (ClassNotFoundException e) {
      return null;
    } catch (Exception e) {
      LOGGER.debug("JacksonAnnotationBridge failed to resolve {}: {}", simpleName, e.toString());
      return null;
    }
  }

  /**
   * 读取注解的 String 成员值（异常与缺注解统一降级返回 null）。
   *
   * @param element 被注解的元素（字段/方法/类）
   * @param annotationType 目标注解类型
   * @param member 注解成员名（如 {@code "value"}）
   * @return 成员字符串值；缺失、空串或读取异常时返回 null
   */
  private static String stringValue(
      AnnotatedElement element, Class<? extends Annotation> annotationType, String member) {
    if (!AVAILABLE || annotationType == null || element == null) {
      return null;
    }
    Annotation annotation = element.getAnnotation(annotationType);
    if (annotation == null) {
      return null;
    }
    try {
      String value = (String) annotation.annotationType().getMethod(member).invoke(annotation);
      return (value != null && !value.isEmpty()) ? value : null;
    } catch (Exception e) {
      LOGGER.debug(
          "JacksonAnnotationBridge failed to read @{} on {}: {}",
          annotationType.getSimpleName(),
          element,
          e.toString());
      return null;
    }
  }
}
