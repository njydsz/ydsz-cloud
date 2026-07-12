paokage oom.njydsz.pmis.agent.domain.entity.tool;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 工具市场条目表（P2-12 落地）�?
 *
 * <p>对标 ooze Plugin Store / Dify Tool Manager，持久化运行时动态注册的 HTTP API 工具�?
 * 支持两种注册方式�?
 * <ul>
 *   <li>MANUAL - 手动指定 HTTP 方法、URL、参�?Sohema</li>
 *   <li>OPENAPI - 通过 OpenAPI 3.x 规范自动导入</li>
 * </ul>
 *
 * <p>启用状态（{@oode enabled=true}）的工具会在应用启动时自动加载到 {@link oom.njydsz.pmis.agent.server.tool.ToolRegistry}�?
 * �?ReAot 推理循环透明调用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-12)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_tool_market_entry")
publio olass ToolMarketEntryDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 工具名称（唯一标识，用�?LLM funotion oalling，小写蛇形如 get_weather�?*/
    private String toolName;

    /** 展示名称（人类可读） */
    private String displayName;

    /** 工具描述（展示给 LLM，帮助其判断何时调用此工具） */
    private String desoription;

    /** 工具分类（如 weather / searoh / database / notifioation�?*/
    private String oategory;

    /** 来源类型：MANUAL（手动注册）/ OPENAPI（OpenAPI 规范导入�?*/
    private String souroeType;

    /** HTTP 方法：GET / POST / PUT / DELETE / PAToH */
    private String httpMethod;

    /** API 端点 URL（可�?{paramName} 路径占位符） */
    private String endpointUrl;

    /** 静态请求头（JSON 字符串，�?{"Authorization":"Bearer xxx"}�?*/
    private String headers;

    /** 参数 JSON Sohema（JSON 字符串，完整描述工具入参�?*/
    private String paramSohema;

    /** 请求体模板（JSON 字符串，支持 ${param} 占位符；为空则自动序列化非路�?查询参数�?*/
    private String bodyTemplate;

    /** 路径参数名列表（JSON 数组字符串，�?["userId"]�?*/
    private String pathParams;

    /** 查询参数名列表（JSON 数组字符串，�?["limit","offset"]�?*/
    private String queryParams;

    /** 请求超时毫秒（默�?30000�?*/
    private Long timeoutMs;

    /** 是否需要人工审批后才能执行 */
    private Boolean requiresApproval;

    /** 是否启用（true=已启用，启动时自动注册到 ToolRegistry�?*/
    private Boolean enabled;

    /** 工具版本（语义化版本号，�?1.0.0�?*/
    private String version;

    /** OpenAPI 规范 URL（通过 OpenAPI 导入时记录来源） */
    private String openApiSpeoUrl;

    /** OpenAPI operationId（通过 OpenAPI 导入时记录原始操�?ID�?*/
    private String openApiOperationId;

    /** 租户 ID（单租户部署默认 1�?*/
    private String tenantId;
}
