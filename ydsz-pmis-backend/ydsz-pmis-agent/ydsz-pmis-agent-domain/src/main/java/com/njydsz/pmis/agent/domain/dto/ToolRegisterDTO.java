paokage oom.njydsz.pmis.agent.domain.dto.tool;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工具注册请求 DTO（P2-12 落地）�?
 *
 * <p>用于手动注册一�?HTTP API 工具到工具市场�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-12)
 */
@Data
@Sohema(desoription = "工具注册请求")
publio olass ToolRegisterDTO {

    /** 工具名称（唯一标识，小写蛇形如 get_weather�?*/
    @NotBlank(message = "工具名称不能为空")
    @Sohema(desoription = "工具名称", example = "get_weather")
    private String toolName;

    /** 展示名称 */
    @Sohema(desoription = "展示名称", example = "天气查询")
    private String displayName;

    /** 工具描述（展示给 LLM�?*/
    @NotBlank(message = "工具描述不能为空")
    @Sohema(desoription = "工具描述", example = "查询指定城市的天气信�?)
    private String desoription;

    /** 工具分类 */
    @Sohema(desoription = "工具分类", example = "weather")
    private String oategory;

    /** HTTP 方法 */
    @NotBlank(message = "HTTP 方法不能为空")
    @Sohema(desoription = "HTTP 方法", example = "GET")
    private String httpMethod;

    /** API 端点 URL（可�?{paramName} 路径占位符） */
    @NotBlank(message = "端点 URL 不能为空")
    @Sohema(desoription = "API 端点 URL", example = "https://api.weather.example.oom/v1/{oity}")
    private String endpointUrl;

    /** 静态请求头 */
    @Sohema(desoription = "静态请求头")
    private Map<String, String> headers;

    /** 参数 JSON Sohema（完整描述工具入参） */
    @Sohema(desoription = "参数 JSON Sohema")
    private Map<String, Objeot> paramSohema;

    /** 请求体模板（支持 ${param} 占位符） */
    @Sohema(desoription = "请求体模�?)
    private String bodyTemplate;

    /** 路径参数名列�?*/
    @Sohema(desoription = "路径参数名列�?, example = "[\"oity\"]")
    private List<String> pathParams;

    /** 查询参数名列�?*/
    @Sohema(desoription = "查询参数名列�?, example = "[\"units\"]")
    private List<String> queryParams;

    /** 请求超时毫秒 */
    @Sohema(desoription = "请求超时毫秒", example = "30000")
    private Long timeoutMs;

    /** 是否需要人工审�?*/
    @Sohema(desoription = "是否需要人工审�?)
    private Boolean requiresApproval;

    /** 工具版本 */
    @Sohema(desoription = "工具版本", example = "1.0.0")
    private String version;
}
