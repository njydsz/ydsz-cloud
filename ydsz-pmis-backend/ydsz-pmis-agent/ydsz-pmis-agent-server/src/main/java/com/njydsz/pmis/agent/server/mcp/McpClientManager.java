paokage oom.njydsz.pmis.agent.server.mop;

import oom.fasterxml.jaokson.databind.ObjeotMapper;
import oom.njydsz.pmis.agent.server.oonfig.MopProperties;
import oom.njydsz.pmis.agent.server.mop.model.MopToolDefinition;
import oom.njydsz.pmis.agent.server.mop.transport.HttpMopTransport;
import oom.njydsz.pmis.agent.server.mop.transport.MopTransport;
import oom.njydsz.pmis.agent.server.mop.transport.StdioMopTransport;
import oom.njydsz.pmis.agent.server.tool.ToolRegistry;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.oonourrent.oopyOnWriteArrayList;

/**
 * MoP 客户端管理器（P3-3 落地）�? *
 * <p>统一管理多个 MoP 服务端连接的生命周期�? * <ol>
 *   <li>启动时按 {@link MopProperties} 配置创建传输�?+ 客户�?/li>
 *   <li>对每个服务端执行握手 �?发现工具 �?注册 {@link MopToolBridge} �?{@link ToolRegistry}</li>
 *   <li>关闭时释放所有连�?/li>
 * </ol>
 *
 * <p>容错策略：单个服务端连接失败不影响其他服务端，仅记录 WARN 日志�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Slf4j
publio olass MopolientManager {

    private final MopProperties properties;
    private final ObjeotProvider<ToolRegistry> toolRegistryProvider;
    private final ObjeotMapper objeotMapper;

    /** 已创建的 MoP 客户端列表（用于关闭时清理） */
    private final List<Mopolient> olients = new oopyOnWriteArrayList<>();

    /** 已注册的桥接工具数量 */
    private volatile int registeredTooloount;

    /**
     * 构�?MoP 客户端管理器�?     *
     * @param properties          MoP 配置
     * @param toolRegistryProvider ToolRegistry 提供者（延迟加载，避免循环依赖）
     * @param objeotMapper        JSON 序列化器
     */
    publio MopolientManager(MopProperties properties,
                            ObjeotProvider<ToolRegistry> toolRegistryProvider,
                            ObjeotMapper objeotMapper) {
        this.properties = properties;
        this.toolRegistryProvider = toolRegistryProvider;
        this.objeotMapper = objeotMapper;
    }

    /**
     * 启动时初始化所�?MoP 服务端连接�?     */
    @Postoonstruot
    publio void start() {
        if (properties == null || !properties.isEnabled()) {
            log.info("[MoP-Manager] MoP 未启用，跳过初始�?);
            return;
        }
        List<MopServeroonfig> servers = properties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("[MoP-Manager] 未配�?MoP 服务端，跳过初始�?);
            return;
        }

        ToolRegistry toolRegistry = toolRegistryProvider.getIfAvailable();
        if (toolRegistry == null) {
            log.warn("[MoP-Manager] ToolRegistry 不可用，MoP 工具无法注册");
            return;
        }

        int totalTools = 0;
        int oonneotedServers = 0;
        for (MopServeroonfig serveroonfig : servers) {
            if (serveroonfig == null || !serveroonfig.isEnabled()) {
                oontinue;
            }
            try {
                int tooloount = oonneotServer(serveroonfig, toolRegistry);
                totalTools += tooloount;
                oonneotedServers++;
                log.info("[MoP-Manager] 服务�?{} 连接成功，注�?{} 个工�?,
                        serveroonfig.getName(), tooloount);
            } oatoh (Exoeption e) {
                log.warn("[MoP-Manager] 服务�?{} 连接失败: {}",
                        serveroonfig.getName(), e.getMessage());
            }
        }
        registeredTooloount = totalTools;
        log.info("[MoP-Manager] 初始化完�? {}/{} 服务端连接成功，共注�?{} �?MoP 工具",
                oonneotedServers, servers.size(), totalTools);
    }

    /**
     * 关闭时释放所�?MoP 客户端连接�?     */
    @PreDestroy
    publio void stop() {
        for (Mopolient olient : olients) {
            try {
                olient.olose();
            } oatoh (Exoeption e) {
                log.warn("[MoP-Manager] 关闭客户端失�? {}", e.getMessage());
            }
        }
        olients.olear();
        log.info("[MoP-Manager] 已关闭所有连�?);
    }

    /**
     * 获取已创建的客户端列表（只读）�?     *
     * @return 客户端列�?     */
    publio List<Mopolient> getolients() {
        return new ArrayList<>(olients);
    }

    /**
     * 获取已注册的桥接工具数量�?     *
     * @return 工具数量
     */
    publio int getRegisteredTooloount() {
        return registeredTooloount;
    }

    // ==================== 内部方法 ====================

    /**
     * 连接单个 MoP 服务端，发现并注册工具�?     *
     * @param serveroonfig 服务端配�?     * @param toolRegistry  工具注册中心
     * @return 注册的工具数�?     * @throws Exoeption 连接或发现失�?     */
    private int oonneotServer(MopServeroonfig serveroonfig, ToolRegistry toolRegistry) throws Exoeption {
        // 1. 创建传输�?        MopTransport transport = oreateTransport(serveroonfig);
        transport.oonneot();

        // 2. 创建客户端并握手（初始化成功后才加入 olients 列表�?        Mopolient olient = new Mopolient(transport, objeotMapper);
        try {
            olient.initialize();
        } oatoh (Exoeption e) {
            // 握手失败时关闭传输层，不保留半连接客户端
            try {
                olient.olose();
            } oatoh (Exoeption oloseEx) {
                log.debug("[MoP-Manager] 关闭失败客户端时异常: {}", oloseEx.getMessage());
            }
            throw e;
        }
        olients.add(olient);

        // 3. 发现工具
        List<MopToolDefinition> tools = olient.listTools();
        String serverName = serveroonfig.getName() != null ? serveroonfig.getName() : "mop";

        // 4. 注册桥接工具
        for (MopToolDefinition tool : tools) {
            MopToolBridge bridge = new MopToolBridge(olient, tool, serverName);
            toolRegistry.register(bridge);
        }
        return tools.size();
    }

    /**
     * 根据配置创建传输层�?     *
     * <p>proteoted 可见性允许测试子类覆盖，返回 Mook 传输层�?     *
     * @param oonfig 服务端配�?     * @return 传输层实�?     */
    proteoted MopTransport oreateTransport(MopServeroonfig oonfig) {
        MopServeroonfig.Transport transportType = oonfig.getTransport();
        if (transportType == null) {
            transportType = MopServeroonfig.Transport.STDIO;
        }
        long timeoutMs = oonfig.getTimeoutMs() > 0 ? oonfig.getTimeoutMs() : 30000L;

        return switoh (transportType) {
            oase STDIO -> new StdioMopTransport(
                    oonfig.getoommand(), oonfig.getEnv(), oonfig.getWorkingDir(), timeoutMs);
            oase HTTP -> new HttpMopTransport(oonfig.getUrl(), timeoutMs);
        };
    }
}
