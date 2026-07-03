package com.njydsz.pmis.workflow.engine;

import com.alibaba.fastjson2.JSON;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * P1-4: 服务节点执行器
 *
 * <p>负责执行 {@link com.njydsz.pmis.workflow.enums.FlowNodeType#SERVICE} 类型节点的自动逻辑，
 * 不创建人工任务。执行方式由节点 ext JSON 中的 {@code serviceType} 决定：
 * <ul>
 *   <li><b>HTTP</b> — 通过 RestTemplate 调用外部 HTTP 接口，2xx 视为成功</li>
 *   <li><b>SCRIPT</b> — 使用 Aviator 表达式引擎执行脚本，返回 Boolean 决定成功/失败</li>
 *   <li><b>AUTO_PASS</b> — 直接自动通过（默认）</li>
 * </ul>
 *
 * <p>ext JSON 配置示例：
 * <pre>
 * {
 *   "serviceType": "HTTP",
 *   "url": "http://example.com/api/notify",
 *   "method": "POST",
 *   "script": "...（SCRIPT 类型使用，Aviator 语法）"
 * }
 * </pre>
 *
 * <p>SCRIPT 类型使用 Aviator 表达式引擎执行脚本，支持流程变量作为环境传入。
 * 沙箱模式默认启用，禁用 NewInstance/Module 等危险 Feature。
 * 脚本返回 Boolean 时决定执行成功/失败，返回 null 视为成功。
 *
 * <p>RestTemplate 不通过构造器注入，直接 new 出默认实例（与 FlowNotificationServiceImpl 一致），
 * 避免 Spring 容器中必须存在 RestTemplate Bean。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Component
public class FlowServiceNodeExecutor {

    /**
     * WEBHOOK / HTTP 通道使用的 RestTemplate。
     *
     * <p>不通过构造器/字段注入，避免强制要求容器中存在 RestTemplate Bean。
     * 此处直接 new 出默认实例即可满足 best-effort 调用需求；
     * final + 内联初始化使 Lombok @RequiredArgsConstructor 跳过该字段。
     */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Aviator 脚本引擎实例（沙箱模式）。
     *
     * <p>禁用 NewInstance/Module 等危险 Feature，防止脚本创建任意对象或加载模块。
     * 表达式编译结果自带缓存（AviatorEvaluatorInstance 内部 ConcurrentHashMap）。
     */
    private final com.googlecode.aviator.AviatorEvaluatorInstance aviatorInstance;

    /**
     * 构造器：初始化 Aviator 沙箱实例。
     */
    public FlowServiceNodeExecutor() {
        this.aviatorInstance = AviatorEvaluator.newInstance();
        // 浮点数解析为 Decimal，避免精度丢失
        this.aviatorInstance.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true);
        // 禁用危险 Feature
        this.aviatorInstance.disableFeature(Feature.NewInstance);
        this.aviatorInstance.disableFeature(Feature.Module);
        this.aviatorInstance.disableFeature(Feature.Lambda);
        log.info("[Flow-Service] Aviator 脚本引擎已初始化（沙箱模式）");
    }

    /**
     * 执行服务节点
     *
     * @param node      服务节点
     * @param variables 流程变量（HTTP 调用时作为请求体传递）
     * @return 执行结果（成功/失败 + 消息）
     */
    public ServiceExecutionResult execute(FlowNodeDO node, Map<String, Object> variables) {
        Map<String, Object> config = parseExtConfig(node.getExt());
        String serviceType = String.valueOf(config.getOrDefault("serviceType", "AUTO_PASS")).toUpperCase();

        log.info("[Flow-Service] 执行服务节点: node={} serviceType={}", node.getNodeCode(), serviceType);

        return switch (serviceType) {
            case "HTTP" -> executeHttp(node, config, variables);
            case "SCRIPT" -> executeScript(node, config, variables);
            case "AUTO_PASS" -> new ServiceExecutionResult(true, "自动通过");
            default -> {
                log.warn("[Flow-Service] 未知服务类型 {}，默认自动通过: node={}", serviceType, node.getNodeCode());
                yield new ServiceExecutionResult(true, "未知服务类型(" + serviceType + ")，默认自动通过");
            }
        };
    }

    /**
     * HTTP 类型：通过 RestTemplate 调用外部接口
     */
    private ServiceExecutionResult executeHttp(FlowNodeDO node, Map<String, Object> config,
                                                Map<String, Object> variables) {
        String url = String.valueOf(config.getOrDefault("url", ""));
        if (!StringUtils.hasText(url) || "null".equals(url)) {
            log.warn("[Flow-Service] HTTP 服务节点未配置 url，标记为失败: node={}", node.getNodeCode());
            return new ServiceExecutionResult(false, "HTTP 服务节点未配置 url");
        }
        String method = String.valueOf(config.getOrDefault("method", "GET")).toUpperCase();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(variables, headers);

            HttpMethod httpMethod = switch (method) {
                case "POST" -> HttpMethod.POST;
                case "PUT" -> HttpMethod.PUT;
                case "DELETE" -> HttpMethod.DELETE;
                default -> HttpMethod.GET;
            };

            ResponseEntity<String> response = restTemplate.exchange(
                    url, httpMethod, entity, String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            String msg = "HTTP " + method + " " + url + " -> " + response.getStatusCode();
            if (success) {
                log.info("[Flow-Service] HTTP 调用成功: node={} {}", node.getNodeCode(), msg);
            } else {
                log.error("[Flow-Service] HTTP 调用失败: node={} {}", node.getNodeCode(), msg);
            }
            return new ServiceExecutionResult(success, msg);
        } catch (Exception e) {
            log.error("[Flow-Service] HTTP 调用异常: node={} url={} err={}",
                    node.getNodeCode(), url, e.getMessage(), e);
            return new ServiceExecutionResult(false, "HTTP 调用异常: " + e.getMessage());
        }
    }

    /**
     * SCRIPT 类型：使用 Aviator 表达式引擎执行脚本
     *
     * <p>脚本可引用流程变量（如 {@code amount > 5000}），返回值规则：
     * <ul>
     *   <li>返回 Boolean → true 视为成功，false 视为失败</li>
     *   <li>返回 null → 视为成功</li>
     *   <li>返回其他值 → 视为成功，返回值转为消息</li>
     * </ul>
     *
     * @param node      服务节点
     * @param config    ext 配置（包含 script 字段）
     * @param variables 流程变量（作为脚本执行环境）
     */
    private ServiceExecutionResult executeScript(FlowNodeDO node, Map<String, Object> config,
                                                    Map<String, Object> variables) {
        String script = String.valueOf(config.getOrDefault("script", ""));
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
            log.error("[Flow-Service] 脚本执行异常: node={} script={} err={}",
                    node.getNodeCode(), script, e.getMessage(), e);
            return new ServiceExecutionResult(false, "脚本执行异常: " + e.getMessage());
        }
    }

    /**
     * 解析 ext JSON 为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseExtConfig(String ext) {
        if (!StringUtils.hasText(ext)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JSON.parseObject(ext, Map.class);
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            log.warn("[Flow-Service] 解析 ext JSON 失败: {} err={}", ext, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 服务节点执行结果
     *
     * @param success 是否成功
     * @param message 结果消息（用于审计日志）
     */
    public record ServiceExecutionResult(boolean success, String message) {
    }
}
