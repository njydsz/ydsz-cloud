package com.njydsz.workflow.server.engine;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.njydsz.workflow.domain.vo.FlowNodeVO;

/**
 * P1-4: 服务节点执行器
 *
 * <p>负责执行 {@link com.njydsz.workflow.domain.enums.FlowNodeType#SERVICE} 类型节点的自动逻辑， 不创建人工任务。执行方式由节点
 * ext JSON 中的 {@code serviceType} 决定：
 *
 * <ul>
 *   <li><b>HTTP</b> — 通过 RestTemplate 调用外部 HTTP 接口，2xx 视为成功
 *   <li><b>SCRIPT</b> — 使用 Aviator 表达式引擎执行脚本，返回 Boolean 决定成功/失败
 *   <li><b>AUTO_PASS</b> — 直接自动通过（默认）
 * </ul>
 *
 * <p>ext JSON 配置示例：
 *
 * <pre>
 * {
 *   "serviceType": "HTTP",
 *   "url": "http://example.com/api/notify",
 *   "method": "POST",
 *   "script": "...（SCRIPT 类型使用，Aviator 语法）"
 * }
 * </pre>
 *
 * <p>SCRIPT 类型使用 Aviator 表达式引擎执行脚本，支持流程变量作为环境传入。 沙箱模式默认启用，禁用 NewInstance/Module 等危险 Feature。 脚本返回
 * Boolean 时决定执行成功/失败，返回 null 视为成功。
 *
 * <p>RestTemplate 由 ydsz-common-notify 统一提供，通过构造器注入。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowServiceNodeExecutor {


  /** HTTP 连接超时时间（秒） */
  private static final int CONNECT_TIMEOUT_SECONDS = 5;

  /** HTTP 读取超时时间（秒） */
  private static final int READ_TIMEOUT_SECONDS = 30;

  /** WEBHOOK / HTTP 通道使用的 RestTemplate（带超时配置）。 */
  private final RestTemplate restTemplate;

  /**
   * Aviator 脚本引擎实例（沙箱模式）。
   *
   * <p>禁用 NewInstance/Module 等危险 Feature，防止脚本创建任意对象或加载模块。 表达式编译结果自带缓存（AviatorEvaluatorInstance 内部
   * ConcurrentHashMap）。
   */
  private final AviatorEvaluatorInstance aviatorInstance;

  /**
   * 构造器：构建带超时的 RestTemplate，并初始化 Aviator 沙箱实例。
   *
   * <p>使用 {@link SimpleClientHttpRequestFactory} 配置超时，替代 Spring Boot 4.x 中已移除的
   * {@code RestTemplateBuilder}。
   */
  public FlowServiceNodeExecutor() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));
    factory.setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS));
    this.restTemplate = new RestTemplate(factory);
    this.aviatorInstance = AviatorEvaluator.newInstance();
    // 浮点数解析为 Decimal，避免精度丢失
    this.aviatorInstance.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true);
    // 禁用危险 Feature
    this.aviatorInstance.disableFeature(Feature.NewInstance);
    this.aviatorInstance.disableFeature(Feature.Module);
    this.aviatorInstance.disableFeature(Feature.Lambda);
    log.info(
        "[Flow-Service] Aviator 脚本引擎已初始化（沙箱模式），HTTP 超时配置: connect={}s read={}s",
        CONNECT_TIMEOUT_SECONDS,
        READ_TIMEOUT_SECONDS);
  }

  /**
   * 执行服务节点
   *
   * @param node 服务节点
   * @param variables 流程变量（HTTP 调用时作为请求体传递）
   * @return 执行结果（成功/失败 + 消息）
   */
  public ServiceExecutionResult execute(FlowNodeVO node, Map<String, Object> variables) {
    String serviceType = FlowNodeExt.getServiceType(node.getExt());

    log.info("[Flow-Service] 执行服务节点: node={} serviceType={}", node.getNodeCode(), serviceType);

    return switch (serviceType) {
      case "HTTP" -> executeHttp(node, variables);
      case "SCRIPT" -> executeScript(node, variables);
      case "AUTO_PASS" -> new ServiceExecutionResult(true, "自动通过");
      default -> {
        log.warn("[Flow-Service] 未知服务类型 {}，默认自动通过: node={}", serviceType, node.getNodeCode());
        yield new ServiceExecutionResult(true, "未知服务类型(" + serviceType + ")，默认自动通过");
      }
    };
  }

  /**
   * P2-4 (GAP-14): 在沙箱环境内求值 Aviator 表达式
   *
   * <p>复用 {@link #aviatorInstance}（已禁用 NewInstance/Module/Lambda 危险 Feature），
   * 供自动审批节点（autoApprove.expr）等场景安全地基于流程变量做布尔求值。
   *
   * @param expr 表达式（如 {@code amount < 1000}），空表达式返回 false
   * @param variables 流程变量环境
   * @return 表达式求值结果（Boolean/数值/字符串等）；求值异常时返回 false
   */
  public Object evalExpr(String expr, Map<String, Object> variables) {
    if (expr == null || expr.isBlank()) {
      return false;
    }
    try {
      Expression expression = aviatorInstance.compile(expr, true);
      Map<String, Object> env = new HashMap<>();
      if (variables != null) {
        env.putAll(variables);
      }
      return expression.execute(env);
    } catch (Exception e) {
      log.warn("[Flow-Service] 表达式求值异常 expr={} err={}", expr, e.getMessage());
      return false;
    }
  }

  /**
   * HTTP 类型：通过 RestTemplate 调用外部接口
   *
   * @param node 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private ServiceExecutionResult executeHttp(
      FlowNodeVO node, Map<String, Object> variables) {
    String url = FlowNodeExt.getServiceUrl(node.getExt());
    if (!StringUtils.hasText(url) || "null".equals(url)) {
      log.warn("[Flow-Service] HTTP 服务节点未配置 url，标记为失败: node={}", node.getNodeCode());
      return new ServiceExecutionResult(false, "HTTP 服务节点未配置 url");
    }
    String method = FlowNodeExt.getServiceMethod(node.getExt());

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(variables, headers);

      HttpMethod httpMethod =
          switch (method) {
            case "POST" -> HttpMethod.POST;
            case "PUT" -> HttpMethod.PUT;
            case "DELETE" -> HttpMethod.DELETE;
            default -> HttpMethod.GET;
          };

      ResponseEntity<String> response =
          restTemplate.exchange(url, httpMethod, entity, String.class);

      boolean success = response.getStatusCode().is2xxSuccessful();
      String msg = "HTTP " + method + " " + url + " -> " + response.getStatusCode();
      if (success) {
        log.info("[Flow-Service] HTTP 调用成功: node={} {}", node.getNodeCode(), msg);
      } else {
        log.error("[Flow-Service] HTTP 调用失败: node={} {}", node.getNodeCode(), msg);
      }
      return new ServiceExecutionResult(success, msg);
    } catch (HttpServerErrorException e) {
      // 5xx 服务端错误：可重试异常
      log.warn(
          "[Flow-Service] HTTP 服务端错误(可重试): node={} url={} status={} err={}",
          node.getNodeCode(),
          url,
          e.getStatusCode(),
          e.getMessage());
      return new ServiceExecutionResult(
          false, "HTTP 服务端错误[" + e.getStatusCode() + "]，建议重试");
    } catch (ResourceAccessException e) {
      // 网络/超时异常：可重试异常
      log.warn(
          "[Flow-Service] HTTP 网络异常(可重试): node={} url={} err={}",
          node.getNodeCode(),
          url,
          e.getMessage());
      return new ServiceExecutionResult(false, "HTTP 网络超时或连接失败，建议重试");
    } catch (HttpClientErrorException e) {
      // 4xx 客户端错误：不可重试（请求本身有误）
      log.error(
          "[Flow-Service] HTTP 客户端错误(不可重试): node={} url={} status={} err={}",
          node.getNodeCode(),
          url,
          e.getStatusCode(),
          e.getMessage(),
          e);
      return new ServiceExecutionResult(
          false, "HTTP 客户端错误[" + e.getStatusCode() + "]: " + e.getStatusText());
    } catch (Exception e) {
      // 兜底：未知异常
      log.error(
          "[Flow-Service] HTTP 调用未知异常: node={} url={} err={}",
          node.getNodeCode(),
          url,
          e.getMessage(),
          e);
      return new ServiceExecutionResult(false, "HTTP 调用未知异常: " + e.getMessage());
    }
  }

  /**
   * SCRIPT 类型：使用 Aviator 表达式引擎执行脚本
   * 
   * <p>脚本可引用流程变量（如 {@code amount > 5000}），返回值规则：
   * 
   * <ul>
   * <li>返回 Boolean → true 视为成功，false 视为失败
   * <li>返回 null → 视为成功
   * <li>返回其他值 → 视为成功，返回值转为消息
   * </ul>
   * 
   *
   * @param node 参数说明
   * @param config 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  private ServiceExecutionResult executeScript(
      FlowNodeVO node, Map<String, Object> variables) {
    String script = FlowNodeExt.getServiceScript(node.getExt());
    if (!StringUtils.hasText(script) || "null".equals(script)) {
      log.warn("[Flow-Service] SCRIPT 节点未配置 script，标记为失败: node={}", node.getNodeCode());
      return new ServiceExecutionResult(false, "SCRIPT 节点未配置 script");
    }

    try {
      // 编译脚本（Aviator 以表达式文本作为缓存 key，自带 ConcurrentHashMap 缓存）
      Expression expression = aviatorInstance.compile(script, true);

      // 构建执行环境（传入流程变量）
      Map<String, Object> env = new HashMap<>();
      if (variables != null) {
        env.putAll(variables);
      }

      // 执行脚本
      Object result = expression.execute(env);

      // 处理结果
      if (result == null) {
        log.info("[Flow-Service] 脚本执行完成（返回 null）: node={}", node.getNodeCode());
        return new ServiceExecutionResult(true, "脚本执行完成");
      }

      if (result instanceof Boolean boolResult) {
        String msg = "脚本结果: " + boolResult;
        if (boolResult) {
          log.info("[Flow-Service] 脚本执行成功: node={} result={}", node.getNodeCode(), result);
        } else {
          log.warn("[Flow-Service] 脚本执行返回 false: node={} script={}", node.getNodeCode(), script);
        }
        return new ServiceExecutionResult(boolResult, msg);
      }

      // 非 Boolean 结果视为成功
      log.info("[Flow-Service] 脚本执行完成: node={} result={}", node.getNodeCode(), result);
      return new ServiceExecutionResult(true, "脚本结果: " + result);
    } catch (Exception e) {
      log.error(
          "[Flow-Service] 脚本执行异常: node={} script={} err={}",
          node.getNodeCode(),
          script,
          e.getMessage(),
          e);
      return new ServiceExecutionResult(false, "脚本执行异常: " + e.getMessage());
    }
  }

  /**
   * 服务节点执行结果
   *
   * @param success 是否成功
   * @param message 结果消息（用于审计日志）
   */
  public record ServiceExecutionResult(boolean success, String message) {}
}
