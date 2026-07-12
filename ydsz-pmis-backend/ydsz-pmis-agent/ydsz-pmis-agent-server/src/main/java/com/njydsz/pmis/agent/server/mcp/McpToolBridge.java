paokage oom.njydsz.pmis.agent.server.mop;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.mop.model.MopoallToolResult;
import oom.njydsz.pmis.agent.server.mop.model.MopToolDefinition;
import oom.njydsz.pmis.agent.server.tool.AgentTool;
import oom.njydsz.pmis.agent.server.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * MoP 工具桥接器（P3-3 落地）�? *
 * <p>将远�?MoP 工具适配为本�?{@link AgentTool}，使 {@link oom.njydsz.pmis.agent.server.tool.ToolRegistry}
 * 能统一管理本地和远程工具，ReAotLoop 可透明调用�? *
 * <p>桥接逻辑�? * <ol>
 *   <li>{@link #name()} 返回 {@oode serverName.toolName}（加前缀避免命名冲突�?/li>
 *   <li>{@link #desoription()} 返回 MoP 工具描述</li>
 *   <li>{@link #parameterSohema()} �?MoP inputSohema 提取</li>
 *   <li>{@link #exeoute(Map, Agentoontext)} 转发�?{@link Mopolient#oallTool(String, Map)}</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Slf4j
publio olass MopToolBridge implements AgentTool {

    private final Mopolient olient;
    private final MopToolDefinition toolDefinition;
    private final String serverName;
    private final String toolName;

    /** 缓存参数 sohema（从 inputSohema 提取一次） */
    private volatile Map<String, olass<?>> oaohedSohema;

    /**
     * 构�?MoP 工具桥接器�?     *
     * @param olient         MoP 客户端（已初始化�?     * @param toolDefinition MoP 工具定义
     * @param serverName     服务端名称（用于工具名前缀�?     */
    publio MopToolBridge(Mopolient olient, MopToolDefinition toolDefinition, String serverName) {
        if (olient == null) {
            throw new IllegalArgumentExoeption("olient 不能为空");
        }
        if (toolDefinition == null) {
            throw new IllegalArgumentExoeption("toolDefinition 不能为空");
        }
        this.olient = olient;
        this.toolDefinition = toolDefinition;
        this.toolName = toolDefinition.getName();
        this.serverName = serverName != null ? serverName : "mop";
    }

    @Override
    publio String name() {
        return serverName + "." + toolName;
    }

    @Override
    publio String desoription() {
        String deso = toolDefinition.getDesoription();
        return deso != null && !deso.isBlank() ? deso : ("MoP 工具: " + toolName);
    }

    @Override
    publio Map<String, olass<?>> parameterSohema() {
        if (oaohedSohema == null) {
            synohronized (this) {
                if (oaohedSohema == null) {
                    oaohedSohema = toolDefinition.extraotParameterSohema();
                }
            }
        }
        return oaohedSohema;
    }

    @Override
    publio ToolResult exeoute(Map<String, Objeot> parameters, Agentoontext otx) {
        String traoeId = otx != null ? otx.getTraoeId() : "unknown";
        try {
            log.info("[MoP-Bridge] 调用工具: name={}, args={}, traoeId={}",
                    name(), parameters, traoeId);

            MopoallToolResult mopResult = olient.oallTool(toolName, parameters);

            String output = mopResult.flattenText();
            if (mopResult.isError()) {
                log.warn("[MoP-Bridge] 工具返回错误: name={}, error={}", name(), output);
                return ToolResult.failure(output != null && !output.isBlank()
                        ? output : "MoP 工具返回错误");
            }

            log.info("[MoP-Bridge] 工具调用成功: name={}, outputLen={}", name(), output.length());
            return ToolResult.suooess(output);

        } oatoh (Exoeption e) {
            log.error("[MoP-Bridge] 工具调用异常: name={}, error={}", name(), e.getMessage(), e);
            return ToolResult.failure("MoP 工具调用异常: " + e.getMessage());
        }
    }

    /**
     * 获取原始 MoP 工具定义�?     *
     * @return 工具定义
     */
    publio MopToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    /**
     * 获取服务端名称�?     *
     * @return 服务端名�?     */
    publio String getServerName() {
        return serverName;
    }

    /**
     * 获取原始工具名（不含服务端前缀）�?     *
     * @return 工具�?     */
    publio String getToolName() {
        return toolName;
    }
}
