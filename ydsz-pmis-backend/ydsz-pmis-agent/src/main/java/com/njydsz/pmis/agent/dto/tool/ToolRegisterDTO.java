package com.njydsz.pmis.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工具注册请求 DTO（P2-12 落地）。
 *
 * <p>用于手动注册一个 HTTP API 工具到工具市场。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-12)
 */
@Data
@Schema(description = "工具注册请求")
public class ToolRegisterDTO {

    /** 工具名称（唯一标识，小写蛇形如 get_weather） */
    @NotBlank(message = "工具名称不能为空")
    @Schema(description = "工具名称", example = "get_weather")
    private String toolName;

    /** 展示名称 */
    @Schema(description = "展示名称", example = "天气查询")
    private String displayName;

    /** 工具描述（展示给 LLM） */
    @NotBlank(message = "工具描述不能为空")
    @Schema(description = "工具描述", example = "查询指定城市的天气信息")
    private String description;

    /** 工具分类 */
    @Schema(description = "工具分类", example = "weather")
    private String category;

    /** HTTP 方法 */
    @NotBlank(message = "HTTP 方法不能为空")
    @Schema(description = "HTTP 方法", example = "GET")
    private String httpMethod;

    /** API 端点 URL（可含 {paramName} 路径占位符） */
    @NotBlank(message = "端点 URL 不能为空")
    @Schema(description = "API 端点 URL", example = "https://api.weather.example.com/v1/{city}")
    private String endpointUrl;

    /** 静态请求头 */
    @Schema(description = "静态请求头")
    private Map<String, String> headers;

    /** 参数 JSON Schema（完整描述工具入参） */
    @Schema(description = "参数 JSON Schema")
    private Map<String, Object> paramSchema;

    /** 请求体模板（支持 ${param} 占位符） */
    @Schema(description = "请求体模板")
    private String bodyTemplate;

    /** 路径参数名列表 */
    @Schema(description = "路径参数名列表", example = "[\"city\"]")
    private List<String> pathParams;

    /** 查询参数名列表 */
    @Schema(description = "查询参数名列表", example = "[\"units\"]")
    private List<String> queryParams;

    /** 请求超时毫秒 */
    @Schema(description = "请求超时毫秒", example = "30000")
    private Long timeoutMs;

    /** 是否需要人工审批 */
    @Schema(description = "是否需要人工审批")
    private Boolean requiresApproval;

    /** 工具版本 */
    @Schema(description = "工具版本", example = "1.0.0")
    private String version;
}
