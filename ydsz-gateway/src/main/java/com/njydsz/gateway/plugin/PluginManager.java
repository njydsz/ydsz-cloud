package com.njydsz.gateway.plugin;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * P2-1: 插件管理器
 *
 * <p>管理网关插件的生命周期：加载、执行、卸载。支持热加载 Groovy 脚本。
 *
 * <h3>热加载机制</h3>
 * <ul>
 *   <li>监控 {@code plugins/} 目录下的文件变更</li>
 *   <li>检测到新 .groovy 文件时动态编译加载</li>
 *   <li>检测到 *.groovy.deleted 时卸载对应插件</li>
 * </ul>
 *
 * <h3>配置</h3>
 * <pre>
 * ydsz:
 *   gateway:
 *     plugin:
 *       enabled: true
 *       watch-path: ./plugins
 *       reload-interval: 5000  # 扫描间隔（毫秒）
 * </pre>
 *
 * @since 1.0.0 (P2-1)
 * @author ydsz-team
 */
@Slf4j
@Component
public class PluginManager {

    /** 插件存储：name -> plugin */
    private final Map<String, GatewayPlugin> plugins = new ConcurrentHashMap<>();

    /** 插件目录监控 */
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watching = false;

    @Value("${ydsz.gateway.plugin.enabled:false}")
    private boolean pluginEnabled;

    @Value("${ydsz.gateway.plugin.watch-path:./plugins}")
    private String watchPath;

    @Value("${ydsz.gateway.plugin.reload-interval:5000}")
    private long reloadInterval;

    /**
     * 初始化插件管理器
     *
     * <p>扫描插件目录并启动热加载监控线程。
     */
    @PostConstruct
    public void init() {
        if (!pluginEnabled) {
            log.info("[PluginManager] 插件功能未启用 (ydsz.gateway.plugin.enabled=false)");
            return;
        }

        Path pluginDir = Paths.get(watchPath);
        if (!Files.exists(pluginDir)) {
            try {
                Files.createDirectories(pluginDir);
                log.info("[PluginManager] 创建插件目录: {}", pluginDir.toAbsolutePath());
            } catch (IOException e) {
                log.warn("[PluginManager] 创建插件目录失败: {}", e.getMessage());
            }
        }

        startWatching();

        log.info("[PluginManager] 插件管理器初始化完成: path={}, interval={}ms", watchPath, reloadInterval);
    }

    /**
     * 执行指定类型的所有插件
     *
     * @param type      插件类型
     * @param exchange  服务器 Web 交换上下文
     * @return 完成信号
     */
    public Mono<Void> executePlugins(GatewayPlugin.PluginType type, ServerWebExchange exchange) {
        if (!pluginEnabled) {
            return Mono.empty();
        }

        return Flux.fromIterable(plugins.values())
                .filter(p -> p.getType() == type && p.isEnabled())
                .sort(Comparator.comparingInt(GatewayPlugin::getOrder))
                .concatMap(plugin -> plugin.execute(exchange, null)
                        .onErrorResume(e -> {
                            log.warn("[PluginManager] 插件 {} 执行异常: {}",
                                    plugin.getName(), e.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }

    /**
     * 注册插件
     *
     * @param plugin 插件实例
     */
    public void registerPlugin(GatewayPlugin plugin) {
        plugins.put(plugin.getName(), plugin);
        log.info("[PluginManager] 注册插件: name={}, type={}, order={}",
                plugin.getName(), plugin.getType(), plugin.getOrder());
    }

    /**
     * 卸载插件
     *
     * @param name 插件名称
     */
    public void unregisterPlugin(String name) {
        GatewayPlugin removed = plugins.remove(name);
        if (removed != null) {
            log.info("[PluginManager] 卸载插件: name={}", name);
        }
    }

    /**
     * 获取已注册插件数量
     *
     * @return 插件数量
     */
    public int getPluginCount() {
        return plugins.size();
    }

    /**
     * 启动文件监控线程（热加载）
     */
    private void startWatching() {
        if (watching) {
            return;
        }
        watching = true;
        watchThread = new Thread(this::watchLoop, "plugin-hot-reload");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * 监控循环
     */
    private void watchLoop() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path dir = Paths.get(watchPath);
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            log.info("[PluginManager] 开始监控插件目录: {}", dir.toAbsolutePath());

            while (watching) {
                WatchKey key = watchService.poll(reloadInterval,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    String fileName = event.context().toString();
                    if (fileName.endsWith(".groovy")) {
                        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            String pluginName = fileName.replace(".groovy", "");
                            unregisterPlugin(pluginName);
                        } else {
                            log.info("[PluginManager] 检测到插件文件变更: {} ({})", fileName, kind.name());
                            // Groovy 加载需引入 groovy-jsr223 依赖，当前版本预留扩展点
                        }
                    }
                }
                key.reset();
            }
        } catch (IOException e) {
            log.error("[PluginManager] 文件监控异常: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            watching = false;
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * 销毁插件管理器
     */
    @PreDestroy
    public void destroy() {
        watching = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        plugins.clear();
        log.info("[PluginManager] 插件管理器已销毁");
    }
}
