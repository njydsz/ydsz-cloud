package com.njydsz.agent.infra.tool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.tool.Tool;
import com.njydsz.agent.domain.tool.ToolExecutor;
import com.njydsz.agent.domain.tool.ToolParam;
import com.njydsz.agent.domain.tool.ToolRegistration;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.common.json.YdszJson;

/**
 * 工具注解（{@code @Tool}）自动扫描注册器。
 *
 * <p>在 Spring Bean 初始化后扫描所有 Bean 的 public 方法， 发现带有 {@link Tool} 注解的方法时自动注册为 LLM 可调用的工具。
 *
 * <p>扫描逻辑：
 *
 * <ol>
 *   <li>遍历所有 Spring Bean
 *   <li>反射获取 Bean 的所有声明方法
 *   <li>检查方法是否标注 {@code @Tool} 注解
 *   <li>提取工具名称、描述、参数 Schema
 *   <li>创建反射调用的 {@link ToolExecutor} 实现
 *   <li>注册到 {@link ToolRegistry}
 * </ol>
 *
 * <p>参数 Schema 构建规则：
 *
 * <ul>
 *   <li>方法参数标注 {@code @ToolParam} 的，提取描述和 required
 *   <li>未标注的参数，使用参数名作为名称，类型推断
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ToolAnnotationScanner implements BeanPostProcessor {

  /** 工具注册中心 */
  private final ToolRegistry toolRegistry;

  public ToolAnnotationScanner(ToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    Class<?> beanClass = bean.getClass();
    // 跳过 Spring 框架内部 Bean
    if (beanClass.getName().startsWith("org.springframework")) {
      return bean;
    }
    for (Method method : beanClass.getDeclaredMethods()) {
      Tool toolAnnotation = AnnotationUtils.findAnnotation(method, Tool.class);
      if (toolAnnotation == null || !toolAnnotation.enabled()) {
        continue;
      }
      registerTool(bean, method, toolAnnotation);
    }
    return bean;
  }

  private void registerTool(Object bean, Method method, Tool toolAnnotation) {
    method.setAccessible(true);

    String toolName = toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name();
    String description = toolAnnotation.description();

    Map<String, Object> parametersSchema = buildParametersSchema(method);
    ToolDefinition definition = new ToolDefinition(toolName, description, parametersSchema);

    ToolExecutor executor =
        arguments -> {
          Object[] args = bindArguments(method, arguments);
          try {
            Object result = method.invoke(bean, args);
            if (result == null) {
              return "{}";
            }
            if (result instanceof String str) {
              return str;
            }
            return YdszJson.toJson(result);
          } catch (IllegalAccessException e) {
            log.error("工具方法访问权限不足: tool={}, error={}", toolName, e.getMessage());
            throw new RuntimeException("工具调用失败（权限不足）: " + toolName, e);
          } catch (Exception e) {
            log.error("工具执行异常: tool={}, error={}", toolName, e.getMessage());
            throw new RuntimeException("工具调用失败: " + toolName, e);
          }
        };

    ToolRegistration registration = new ToolRegistration(definition, executor);
    if (toolRegistry instanceof DefaultToolRegistry defaultRegistry) {
      defaultRegistry.register(registration);
    } else {
      toolRegistry.register(toolName, executor);
    }
    log.info(
        "[Tool-Scanner] 自动注册工具: {} (from {}.{}())",
        toolName,
        bean.getClass().getSimpleName(),
        method.getName());
  }

  /** 从方法参数构建 JSON Schema */
  private Map<String, Object> buildParametersSchema(Method method) {
    Map<String, Object> properties = new HashMap<>(16);
    Parameter[] parameters = method.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      Parameter param = parameters[i];
      String paramName = param.isNamePresent() ? param.getName() : "arg" + i;
      String paramDesc = "";
      boolean required = true;

      ToolParam paramAnnotation = param.getAnnotation(ToolParam.class);
      if (paramAnnotation != null) {
        paramDesc = paramAnnotation.value();
        required = paramAnnotation.required();
      }

      Map<String, Object> paramSchema = new HashMap<>(16);
      paramSchema.put("type", mapJavaTypeToJsonType(param.getType()));
      paramSchema.put("description", paramDesc);
      paramSchema.put("required", required);
      properties.put(paramName, paramSchema);
    }

    Map<String, Object> schema = new HashMap<>(16);
    schema.put("type", "object");
    schema.put("properties", properties);
    return schema;
  }

  /** 将 Java 类型映射到 JSON Schema 类型 */
  private String mapJavaTypeToJsonType(Class<?> javaType) {
    if (javaType == String.class || javaType == char.class || javaType == Character.class) {
      return "string";
    }
    if (javaType == int.class
        || javaType == Integer.class
        || javaType == long.class
        || javaType == Long.class
        || javaType == short.class
        || javaType == Short.class) {
      return "integer";
    }
    if (javaType == double.class
        || javaType == Double.class
        || javaType == float.class
        || javaType == Float.class) {
      return "number";
    }
    if (javaType == boolean.class || javaType == Boolean.class) {
      return "boolean";
    }
    return "object";
  }

  /** 将参数 Map 绑定到方法参数数组 */
  private Object[] bindArguments(Method method, Map<String, Object> arguments) {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      String paramName = parameters[i].isNamePresent() ? parameters[i].getName() : "arg" + i;
      Object value = arguments != null ? arguments.get(paramName) : null;
      args[i] = convertValue(value, parameters[i].getType());
    }
    return args;
  }

  /** 将参数值转换为目标 Java 类型 */
  private Object convertValue(Object value, Class<?> targetType) {
    if (value == null) {
      return getDefaultValue(targetType);
    }
    if (targetType.isInstance(value)) {
      return value;
    }
    String strValue = value.toString();
    if (targetType == String.class) {
      return strValue;
    }
    if (targetType == int.class || targetType == Integer.class) {
      return Integer.parseInt(strValue);
    }
    if (targetType == long.class || targetType == Long.class) {
      return Long.parseLong(strValue);
    }
    if (targetType == double.class || targetType == Double.class) {
      return Double.parseDouble(strValue);
    }
    if (targetType == boolean.class || targetType == Boolean.class) {
      return Boolean.parseBoolean(strValue);
    }
    if (targetType == float.class || targetType == Float.class) {
      return Float.parseFloat(strValue);
    }
    return value;
  }

  private Object getDefaultValue(Class<?> targetType) {
    if (targetType == int.class) {
      return 0;
    }
    if (targetType == long.class) {
      return 0L;
    }
    if (targetType == double.class) {
      return 0.0;
    }
    if (targetType == float.class) {
      return 0.0f;
    }
    if (targetType == boolean.class) {
      return false;
    }
    if (targetType == short.class) {
      return (short) 0;
    }
    if (targetType == byte.class) {
      return (byte) 0;
    }
    if (targetType == char.class) {
      return '\0';
    }
    return null;
  }
}
