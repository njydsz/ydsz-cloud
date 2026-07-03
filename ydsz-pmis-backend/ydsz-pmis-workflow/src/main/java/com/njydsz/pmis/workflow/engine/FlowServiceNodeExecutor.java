package com.njydsz.pmis.workflow.engine;

import com.alibaba.fastjson2.JSON;
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
import java.util.Map;

/**
 * P1-4: 服务节点执行器
 *
 * <p>负责执行 {@link com.njydsz.pmis.workflow.enums.FlowNodeType#SERVICE} 类型节点的自动逻辑，
 * 不创建人工任务。执行方式由节点 ext JSON 中的 {@code serviceType} 决定：
 * <ul>
 *   <li><b>HTTP</b> — 通过 RestTemplate 调用外部 HTTP 接口，2xx 视为成功</li>
 *   <li><b>SCRIPT</b> — 脚本执行（暂未实现，记录日志后视为成功）</li>
 *   <li><b>AUTO_PASS</b> — 直接自动通过（默认）</li>
 * </ul>
 *
 * <p>ext JSON 配置示例：
 * <pre>
 * {
 *   "serviceType": "HTTP",
 *   "url": "http://example.com/api/notify",
 *   "method": "POST",
 *   "script": "...（SCRIPT 类型使用）"
 * }
 * </pre>
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
            case "SCRIPT" -> executeScript(node, config);
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
     * SCRIPT 类型：脚本执行（暂未实现，记录日志后视为成功）
     */
    private ServiceExecutionResult executeScript(FlowNodeDO node, Map<String, Object> config) {
        String script = String.valueOf(config.getOrDefault("script", ""));
        log.info("[Flow-Service] 脚本执行（待实现）: node={} script={}", node.getNodeCode(), script);
        return new ServiceExecutionResult(true, "脚本执行（待实现）");
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
