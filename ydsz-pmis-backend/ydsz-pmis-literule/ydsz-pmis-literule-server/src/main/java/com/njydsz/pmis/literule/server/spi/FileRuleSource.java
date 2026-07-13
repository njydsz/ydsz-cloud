package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.server.dsl.RuleDsl;
import com.njydsz.pmis.literule.server.dsl.RuleDslEntry;
import com.njydsz.pmis.literule.server.dsl.RuleDslParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.Enumeration;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * 文件规则数据源（P2-3 DSL YAML/JSON 规则文件加载）
 *
 * <p>从 classpath 或文件系统加载 YAML/JSON 规则文件，转换为 {@link RuleDefinition} 列表。
 * 适用于 GitOps 场景：规则以 YAML 文件形式存储在 Git 仓库中，应用启动时从
 * classpath 或本地磁盘加载，文件变更后通过 {@code WatchService} 触发热刷新。
 *
 * <p><b>支持的 location 格式</b>：
 * <ul>
 *   <li>{@code classpath:rules/} - 从 classpath 目录加载全部 {@code *.yml}/{@code *.yaml}/{@code *.json}</li>
 *   <li>{@code classpath:rules/risk.yml} - 加载单个 classpath 文件</li>
 *   <li>{@code file:/etc/rules/} - 从文件系统目录加载</li>
 *   <li>{@code file:/etc/rules/risk.yml} - 加载单个文件系统文件</li>
 *   <li>{@code rules/} - 不带前缀时默认按 classpath 处理</li>
 * </ul>
 *
 * <p><b>使用示例</b>：
 * <pre>
 * FileRuleSource source = new FileRuleSource("classpath:rules/", true);
 * source.init();
 * List&lt;RuleDefinition&gt; rules = source.loadEnabledRules();
 * </pre>
 *
 * <p>WatchService 监听为可选能力（{@link #supportsWatch()} 返回 true），
 * 文件变更时回调已注册的 {@link Consumer} 监听器。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
public class FileRuleSource implements RuleSource {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_PREFIX = "file:";

    private final String location;
    private final boolean watchEnabled;

    /** 已加载的规则定义（init 后填充） */
    private volatile List<RuleDefinition> cachedRules = Collections.emptyList();

    /** 变更监听器列表 */
    private final List<Consumer<List<RuleDefinition>>> listeners = new ArrayList<>();

    /** 文件监听线程（watchEnabled=true 时启动） */
    private Thread watchThread;

    /** 是否已初始化 */
    private volatile boolean initialized = false;

    /**
     * 构造文件规则数据源
     *
     * @param location 文件位置（classpath: 或 file: 前缀）
     * @param watchEnabled 是否启用文件变更监听
     */
    public FileRuleSource(String location, boolean watchEnabled) {
        this.location = location == null || location.isBlank() ? "classpath:rules/" : location;
        this.watchEnabled = watchEnabled;
    }

    /**
     * 构造文件规则数据源（默认不启用文件监听）
     *
     * @param location 文件位置
     */
    public FileRuleSource(String location) {
        this(location, false);
    }

    @Override
    public SourceType getType() {
        return SourceType.FILE;
    }

    @Override
    public boolean supportsWatch() {
        return watchEnabled;
    }

    @Override
    public boolean isAvailable() {
        return initialized;
    }

    @Override
    public List<RuleDefinition> loadEnabledRules() {
        if (!initialized) {
            log.warn("[FileRuleSource] 未初始化，返回空列表");
            return List.of();
        }
        // 仅返回启用的规则
        return cachedRules.stream()
                .filter(r -> r.isEnabled())
                .toList();
    }

    /**
     * 加载全部规则定义（含禁用）
     *
     * @return 全部规则定义列表
     */
    public List<RuleDefinition> loadAllRules() {
        if (!initialized) {
            return List.of();
        }
        return Collections.unmodifiableList(cachedRules);
    }

    @Override
    public void addChangeListener(Consumer<List<RuleDefinition>> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void init() throws Exception {
        try {
            cachedRules = loadFromLocation(location);
            initialized = true;
            log.info("[FileRuleSource] 已加载 {} 条规则定义 from {} (watch={})",
                    cachedRules.size(), location, watchEnabled);
            if (watchEnabled) {
                startWatchThread();
            }
        } catch (Exception e) {
            log.error("[FileRuleSource] 初始化失败: location={}, err={}", location, e.getMessage(), e);
            initialized = false;
            throw e;
        }
    }

    @Override
    public void destroy() throws Exception {
        if (watchThread != null && watchThread.isAlive()) {
            watchThread.interrupt();
            try {
                watchThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            watchThread = null;
        }
        initialized = false;
        log.info("[FileRuleSource] 资源已释放: location={}", location);
    }

    // ============ 内部加载逻辑 ============

    /**
     * 从指定位置加载全部规则定义
     *
     * @param location 位置字符串
     * @return 规则定义列表
     */
    private List<RuleDefinition> loadFromLocation(String location) throws IOException {
        List<RuleDsl> dsls = new ArrayList<>();
        if (location.startsWith(CLASSPATH_PREFIX)) {
            String path = location.substring(CLASSPATH_PREFIX.length());
            dsls.addAll(loadFromClasspath(path));
        } else if (location.startsWith(FILE_PREFIX)) {
            String path = location.substring(FILE_PREFIX.length());
            dsls.addAll(loadFromFilesystem(path));
        } else {
            // 无前缀默认按 classpath 处理
            dsls.addAll(loadFromClasspath(location));
        }
        // 合并全部 DSL 的规则定义
        List<RuleDefinition> rules = new ArrayList<>();
        for (RuleDsl dsl : dsls) {
            if (dsl == null || dsl.getRules() == null) continue;
            for (RuleDslEntry entry : dsl.getRules()) {
                RuleDefinition def = toRuleDefinition(entry);
                if (def != null) {
                    rules.add(def);
                }
            }
        }
        return rules;
    }

    /**
     * 从 classpath 加载规则文件
     *
     * @param path classpath 路径（目录或文件）
     * @return DSL 列表
     */
    private List<RuleDsl> loadFromClasspath(String path) throws IOException {
        List<RuleDsl> dsls = new ArrayList<>();
        ClassLoader cl = getClass().getClassLoader();
        // 尝试作为目录加载全部规则文件
        URL dirUrl = cl.getResource(path);
        if (dirUrl == null) {
            // 路径不存在，尝试作为单文件加载
            URL fileUrl = cl.getResource(path);
            if (fileUrl != null) {
                RuleDsl dsl = loadFromUrl(fileUrl);
                if (dsl != null) dsls.add(dsl);
            } else {
                log.warn("[FileRuleSource] classpath 路径不存在: {}", path);
            }
            return dsls;
        }
        if (dirUrl.getProtocol().equals("file")) {
            // classpath 指向文件系统目录
            Path dirPath = Paths.get(dirUrl.getPath());
            dsls.addAll(loadFromFilesystem(dirPath.toString()));
        } else {
            // jar 内部资源，通过 ClassLoader.getResources 枚举
            Enumeration<URL> resources = cl.getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                RuleDsl dsl = loadFromUrl(url);
                if (dsl != null) dsls.add(dsl);
            }
        }
        return dsls;
    }

    /**
     * 从文件系统加载规则文件
     *
     * @param path 文件系统路径（目录或文件）
     * @return DSL 列表
     */
    private List<RuleDsl> loadFromFilesystem(String path) throws IOException {
        List<RuleDsl> dsls = new ArrayList<>();
        Path fsPath = Paths.get(path);
        if (!Files.exists(fsPath)) {
            log.warn("[FileRuleSource] 文件系统路径不存在: {}", path);
            return dsls;
        }
        if (Files.isDirectory(fsPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fsPath, this::isRuleFile)) {
                for (Path file : stream) {
                    RuleDsl dsl = loadFromPath(file);
                    if (dsl != null) dsls.add(dsl);
                }
            }
        } else if (isRuleFile(fsPath)) {
            RuleDsl dsl = loadFromPath(fsPath);
            if (dsl != null) dsls.add(dsl);
        }
        return dsls;
    }

    /**
     * 从 URL 加载 DSL（classpath 资源）
     */
    private RuleDsl loadFromUrl(URL url) throws IOException {
        try (InputStream is = url.openStream()) {
            String fileName = url.getPath();
            String format = detectFormat(fileName);
            if (format == null) return null;
            return RuleDslParser.loadFromStream(is, format);
        } catch (Exception e) {
            log.warn("[FileRuleSource] 加载失败: url={}, err={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 从文件路径加载 DSL
     */
    private RuleDsl loadFromPath(Path path) {
        try {
            return RuleDslParser.loadFromFile(path);
        } catch (Exception e) {
            log.warn("[FileRuleSource] 加载失败: path={}, err={}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 判断文件是否为规则文件
     */
    private boolean isRuleFile(Path path) {
        if (!Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json");
    }

    /**
     * 根据文件名检测格式
     */
    private String detectFormat(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".json")) return "json";
        return null;
    }

    // ============ DSL 条目转 RuleDefinition ============

    /**
     * 将 DSL 条目转换为规则定义
     *
     * <p>主要支持 expression 类型（最常见的规则类型），其他类型仅填充通用字段。
     *
     * @param entry DSL 条目
     * @return 规则定义；entry 为 null 或缺少 code 时返回 null
     */
    private RuleDefinition toRuleDefinition(RuleDslEntry entry) {
        if (entry == null || entry.getCode() == null || entry.getCode().isBlank()) {
            return null;
        }
        RuleDefinition.RuleDefinitionBuilder b = RuleDefinition.builder()
                .code(entry.getCode())
                .name(entry.getName())
                .category(entry.getCategory())
                .categoryPath(entry.getCategoryPath())
                .owner(entry.getOwner())
                .description(entry.getDescription())
                .priority(entry.getPriority())
                .scope(entry.getScope())
                .enabled(entry.isEnabled())
                .conditionExpression(entry.getCondition())
                .severityExpression(entry.getSeverityExpression())
                .titleTemplate(entry.getTitle())
                .descriptionTemplate(entry.getDescriptionTemplate());
        // 默认严重度
        if (entry.getSeverity() != null && !entry.getSeverity().isBlank()) {
            try {
                b.defaultSeverity(RuleSeverity.valueOf(entry.getSeverity().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("[FileRuleSource] 规则 {} 的 severity 非法: {}，忽略",
                        entry.getCode(), entry.getSeverity());
            }
        }
        return b.build();
    }

    // ============ 文件监听（可选） ============

    /**
     * 启动文件变更监听线程
     *
     * <p>仅当 location 指向文件系统目录且 watchEnabled=true 时启动。
     * classpath 内资源（jar 包内）无法监听，跳过。
     */
    private void startWatchThread() {
        Path watchPath = resolveWatchPath();
        if (watchPath == null || !Files.isDirectory(watchPath)) {
            log.info("[FileRuleSource] watch 启用但 location 非 filesystem 目录，跳过监听: {}", location);
            return;
        }
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            watchPath.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchThread = new Thread(() -> {
                log.info("[FileRuleSource] 文件监听已启动: path={}", watchPath);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        WatchKey key = watcher.take();
                        boolean changed = false;
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.context() instanceof Path ctx && isRuleFile(watchPath.resolve(ctx))) {
                                changed = true;
                            }
                        }
                        if (changed) {
                            reloadAndNotify();
                        }
                        if (!key.reset()) {
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.warn("[FileRuleSource] 监听异常: {}", e.getMessage());
                    }
                }
                try {
                    watcher.close();
                } catch (IOException e) {
                    log.debug("[FileRuleSource] watcher 关闭异常: {}", e.getMessage());
                }
            }, "FileRuleSource-Watcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            log.warn("[FileRuleSource] 启动文件监听失败: {}", e.getMessage());
        }
    }

    /**
     * 解析监听路径（仅文件系统路径有效）
     */
    private Path resolveWatchPath() {
        if (location.startsWith(FILE_PREFIX)) {
            return Paths.get(location.substring(FILE_PREFIX.length()));
        }
        if (location.startsWith(CLASSPATH_PREFIX)) {
            String path = location.substring(CLASSPATH_PREFIX.length());
            URL url = getClass().getClassLoader().getResource(path);
            if (url != null && "file".equals(url.getProtocol())) {
                return Paths.get(url.getPath());
            }
        }
        return null;
    }

    /**
     * 重新加载并通知监听器
     */
    private synchronized void reloadAndNotify() {
        try {
            List<RuleDefinition> newRules = loadFromLocation(location);
            List<RuleDefinition> oldRules = cachedRules;
            cachedRules = newRules;
            log.info("[FileRuleSource] 文件变更触发重载: {} -> {} 条规则",
                    oldRules.size(), newRules.size());
            for (Consumer<List<RuleDefinition>> listener : listeners) {
                try {
                    listener.accept(loadEnabledRules());
                } catch (Exception e) {
                    log.warn("[FileRuleSource] 监听器回调异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[FileRuleSource] 文件变更重载失败: {}", e.getMessage(), e);
        }
    }
}
