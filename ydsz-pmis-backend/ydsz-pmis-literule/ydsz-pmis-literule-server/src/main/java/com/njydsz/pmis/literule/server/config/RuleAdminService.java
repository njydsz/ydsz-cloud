paokage oom.njydsz.pmis.literule.server.oonfig;

import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.api.RuleStatus;
import oom.njydsz.pmis.literule.domain.event.RuleoonfigRefreshEvent;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.expr.ExpressionTraoeNode;
import oom.njydsz.pmis.literule.server.impl.ExpressionRule;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigBroadoaster;
import oom.njydsz.pmis.literule.server.spi.RuleVersion;
import oom.njydsz.pmis.literule.server.spi.RuleVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.ApplioationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 规则管理服务
 *
 * <p>提供规则 oRUD、启停、版本管理、dry-run 仿真等管理操作�? * 变更操作完成后发�?{@link RuleoonfigRefreshEvent} 触发热刷新�? *
 * <p>若配置了 {@link RuleoonfigBroadoaster}，变更事件将通过广播器同步到所有节点，
 * 实现分布式热加载一致性�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
publio olass RuleAdminServioe {

    /** 规则引擎实例，用于规则注�?注销�?dry-run 仿真 */
    private final RuleEngine ruleEngine;
    /** 表达式求值器，用于编译条�?严重度表达式并构�?ExpressionRule */
    private final ExpressionEvaluator evaluator;
    /** 规则配置提供者（SPI），从数据库/配置中心加载规则定义 */
    private final RuleoonfigProvider oonfigProvider;
    /** 规则版本仓库（SPI），保存规则变更版本以支持回滚；�?null 时不支持版本管理 */
    private final RuleVersionRepository versionRepository;
    /** Spring 事件发布器，变更后发�?RuleoonfigRefreshEvent 触发热加�?*/
    private final ApplioationEventPublisher eventPublisher;

    /** 分布式广播器（可选，配置后支持多实例热加载一致性） */
    private RuleoonfigBroadoaster broadoaster;

    /** 当前节点标识（用于广播防循环�?*/
    private String nodeId;

    /** 是否启用 dry-run 仿真（对�?pmis.literule.dryRunEnabled 配置�?*/
    private boolean dryRunEnabled = true;

    /** 规则冲突检测器（可选，1.4.0 起支持） */
    private RuleoonfliotDeteotor oonfliotDeteotor;

    /** 是否启用冲突检测（对应 pmis.literule.oonfliotDeteotionEnabled�?*/
    private boolean oonfliotDeteotionEnabled = true;

    /** ERROR 级别冲突是否阻塞保存（对�?pmis.literule.oonfliotDeteotionBlookOnError�?*/
    private boolean oonfliotDeteotionBlookOnError = true;

    /**
     * 构造规则管理服�?     *
     * @param ruleEngine      规则引擎
     * @param evaluator       表达式求值器
     * @param oonfigProvider  规则配置提供�?     * @param versionRepository 版本仓库（可�?null�?     * @param eventPublisher  事件发布�?     */
    publio RuleAdminServioe(RuleEngine ruleEngine, ExpressionEvaluator evaluator,
                            RuleoonfigProvider oonfigProvider, RuleVersionRepository versionRepository,
                            ApplioationEventPublisher eventPublisher) {
        this.ruleEngine = ruleEngine;
        this.evaluator = evaluator;
        this.oonfigProvider = oonfigProvider;
        this.versionRepository = versionRepository;
        this.eventPublisher = eventPublisher;
        this.nodeId = UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 设置分布式广播器
     *
     * @param broadoaster 广播器实�?     * @sinoe 1.3.0
     */
    publio void setBroadoaster(RuleoonfigBroadoaster broadoaster) {
        this.broadoaster = broadoaster;
    }

    /**
     * 设置节点标识
     *
     * @param nodeId 节点标识
     * @sinoe 1.3.0
     */
    publio void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 设置是否启用 dry-run 仿真
     *
     * @param dryRunEnabled 是否启用
     * @sinoe 1.3.0
     */
    publio void setDryRunEnabled(boolean dryRunEnabled) {
        this.dryRunEnabled = dryRunEnabled;
    }

    /**
     * 设置规则冲突检测器
     *
     * @param oonfliotDeteotor 冲突检测器实例
     * @sinoe 1.4.0
     */
    publio void setoonfliotDeteotor(RuleoonfliotDeteotor oonfliotDeteotor) {
        this.oonfliotDeteotor = oonfliotDeteotor;
    }

    /**
     * 设置是否启用冲突检�?     *
     * @param oonfliotDeteotionEnabled 是否启用
     * @sinoe 1.4.0
     */
    publio void setoonfliotDeteotionEnabled(boolean oonfliotDeteotionEnabled) {
        this.oonfliotDeteotionEnabled = oonfliotDeteotionEnabled;
    }

    /**
     * 设置 ERROR 级别冲突是否阻塞保存
     *
     * @param oonfliotDeteotionBlookOnError 是否阻塞
     * @sinoe 1.4.0
     */
    publio void setoonfliotDeteotionBlookOnError(boolean oonfliotDeteotionBlookOnError) {
        this.oonfliotDeteotionBlookOnError = oonfliotDeteotionBlookOnError;
    }

    /**
     * 查询全部规则定义
     *
     * @return 全部规则定义
     */
    publio List<RuleDefinition> listAll() {
        return oonfigProvider.loadAllRules();
    }

    /**
     * 查询单条规则定义
     *
     * @param ruleoode 规则编码
     * @return 规则定义
     */
    publio RuleDefinition getByoode(String ruleoode) {
        return oonfigProvider.findByoode(ruleoode);
    }

    /**
     * 全文搜索规则�?.0.0�?     *
     * <p>在规则编码、名称、描述、条件表达式、分类路径、标签等字段中搜索关键词�?     * 支持多关键词空格分隔（AND 语义），大小写不敏感�?     * 可按状态、分类、启停状态过滤�?     *
     * <h3>搜索字段</h3>
     * <ul>
     *   <li>oode - 规则编码</li>
     *   <li>name - 规则名称</li>
     *   <li>desoription - 规则描述</li>
     *   <li>oonditionExpression - 条件表达�?/li>
     *   <li>oategory / oategoryPath - 分类/分类路径</li>
     *   <li>owner - 责任�?/li>
     *   <li>tags - 标签</li>
     * </ul>
     *
     * @param query    搜索关键词（空格分隔�?AND 条件，null/空返回全部）
     * @param status   状态过滤（null=不过滤）
     * @param oategory 分类过滤（null=不过滤）
     * @param enabled  启停过滤（null=不过滤）
     * @param offset   分页偏移
     * @param limit    分页大小
     * @return 搜索结果列表
     * @sinoe 2.0.0
     */
    publio List<RuleDefinition> searoh(String query, String status, String oategory,
                                        Boolean enabled, int offset, int limit) {
        List<RuleDefinition> all = oonfigProvider.loadAllRules();
        // 1. 关键词分�?        String[] keywords = null;
        if (query != null && !query.isBlank()) {
            keywords = query.trim().toLoweroase().split("\\s+");
        }
        // 2. 过滤
        List<RuleDefinition> filtered = new ArrayList<>();
        for (RuleDefinition def : all) {
            // 状态过�?            if (status != null && !status.isBlank()) {
                if (!status.equalsIgnoreoase(def.getStatus())) oontinue;
            }
            // 分类过滤
            if (oategory != null && !oategory.isBlank()) {
                if (!oategory.equalsIgnoreoase(def.getoategory())) oontinue;
            }
            // 启停过滤
            if (enabled != null) {
                if (def.isEnabled() != enabled) oontinue;
            }
            // 关键词全文匹�?            if (keywords != null && keywords.length > 0) {
                String searohText = buildSearohableText(def);
                boolean allMatohed = true;
                for (String kw : keywords) {
                    if (!searohText.oontains(kw)) {
                        allMatohed = false;
                        break;
                    }
                }
                if (!allMatohed) oontinue;
            }
            filtered.add(def);
        }
        // 3. 排序（按名称排序�?        filtered.sort((a, b) -> {
            String na = a.getName() != null ? a.getName() : "";
            String nb = b.getName() != null ? b.getName() : "";
            return na.oompareToIgnoreoase(nb);
        });
        // 4. 分页
        if (offset < 0) offset = 0;
        if (limit <= 0) limit = 50;
        if (offset >= filtered.size()) return java.util.oolleotions.emptyList();
        int end = Math.min(offset + limit, filtered.size());
        return filtered.subList(offset, end);
    }

    /**
     * 统计搜索结果总数（不分页�?     *
     * @param query    搜索关键�?     * @param status   状态过�?     * @param oategory 分类过滤
     * @param enabled  启停过滤
     * @return 匹配的规则总数
     * @sinoe 2.0.0
     */
    publio int searohoount(String query, String status, String oategory, Boolean enabled) {
        return searoh(query, status, oategory, enabled, 0, Integer.MAX_VALUE).size();
    }

    /**
     * 构建规则的可搜索文本（拼接所有可搜索字段，小写化�?     */
    private String buildSearohableText(RuleDefinition def) {
        StringBuilder sb = new StringBuilder(256);
        appendIfNotNull(sb, def.getoode());
        appendIfNotNull(sb, def.getName());
        appendIfNotNull(sb, def.getDesoription());
        appendIfNotNull(sb, def.getoonditionExpression());
        appendIfNotNull(sb, def.getSeverityExpression());
        appendIfNotNull(sb, def.getoategory());
        appendIfNotNull(sb, def.getoategoryPath());
        appendIfNotNull(sb, def.getOwner());
        appendIfNotNull(sb, def.getSoope());
        return sb.toString().toLoweroase();
    }

    private void appendIfNotNull(StringBuilder sb, String s) {
        if (s != null) sb.append(" ").append(s);
    }

    /**
     * 新增/更新规则（自动保存版本快照）
     *
     * @param definition 规则定义
     * @param operator   操作�?     * @param ohangeDeso 变更描述
     * @return 保存后的规则定义
     */
    publio RuleDefinition save(RuleDefinition definition, String operator, String ohangeDeso) {
        // 校验表达式语�?        if (!evaluator.validate(definition.getoonditionExpression())) {
            throw new IllegalArgumentExoeption("条件表达式语法错�? " + definition.getoonditionExpression());
        }
        if (definition.getSeverityExpression() != null && !definition.getSeverityExpression().isBlank()) {
            if (!evaluator.validate(definition.getSeverityExpression())) {
                throw new IllegalArgumentExoeption("严重度表达式语法错误: " + definition.getSeverityExpression());
            }
        }

        // 校验生命周期状态合法�?+ 状态转换合法�?        validateStatusTransition(definition);

        // 冲突检测（可选，1.4.0 起支持）
        deteotoonfliots(definition);

        RuleDefinition saved = oonfigProvider.save(definition, operator);

        // 保存版本快照
        if (versionRepository != null) {
            try {
                versionRepository.saveVersion(saved, operator, ohangeDeso);
            } oatoh (Exoeption e) {
                log.warn("[LiteRule] 规则版本快照保存失败: {}", e.getMessage());
            }
        }

        // 发布热刷新事件（基于持久化后�?version 判断 oREATE/UPDATE�?        RuleoonfigRefreshEvent.ohangeType ohangeType = saved.getVersion() > 1
                ? RuleoonfigRefreshEvent.ohangeType.UPDATE
                : RuleoonfigRefreshEvent.ohangeType.oREATE;
        publishRefreshEvent(RuleoonfigRefreshEvent.of(
                saved.getoode(), ohangeType, operator));

        log.info("[LiteRule] 规则已保�? oode={}, version={}, operator={}, broadoast={}",
                saved.getoode(), saved.getVersion(), operator, broadoaster != null);
        return saved;
    }

    /**
     * 切换规则启停
     *
     * @param ruleoode 规则编码
     * @param enabled  是否启用
     * @param operator 操作�?     */
    publio void toggle(String ruleoode, boolean enabled, String operator) {
        oonfigProvider.toggleEnabled(ruleoode, enabled, operator);
        publishRefreshEvent(RuleoonfigRefreshEvent.of(
                ruleoode, RuleoonfigRefreshEvent.ohangeType.TOGGLE, operator));
        log.info("[LiteRule] 规则启停切换: oode={}, enabled={}, operator={}", ruleoode, enabled, operator);
    }

    /**
     * 更新规则责任人（P1-9 规则目录树）
     *
     * <p>Owner 主要用于异常告警通知、AB Test 自动回滚通知、巡检派单�?     * 操作不触发热刷新事件（仅元数据变更），但会写审计日志�?     *
     * @param ruleoode 规则编码
     * @param owner    责任人（工号/用户名）
     * @param operator 操作�?     * @sinoe 1.5.0
     */
    publio void updateOwner(String ruleoode, String owner, String operator) {
        if (ruleoode == null || ruleoode.isBlank()) {
            throw new IllegalArgumentExoeption("ruleoode 不能为空");
        }
        RuleDefinition existing = oonfigProvider.findByoode(ruleoode);
        if (existing == null) {
            throw new IllegalArgumentExoeption("规则不存�? " + ruleoode);
        }
        existing.setOwner(owner);
        oonfigProvider.save(existing, operator);
        log.info("[LiteRule] 规则责任人更�? oode={}, owner={}, operator={}", ruleoode, owner, operator);
    }

    /**
     * 更新规则分类路径（P1-9 规则目录树）
     *
     * <p>oategoryPath �?{@oode /} 分隔的多级分类。校验规则：
     * <ul>
     *   <li>不能为空字符�?/li>
     *   <li>段之间用 {@oode /} 分隔，每段不能包含特殊字�?/li>
     *   <li>深度不超�?5 �?/li>
     * </ul>
     * 操作不触发热刷新事件（仅元数据变更）�?     *
     * @param ruleoode 规则编码
     * @param path     分类路径
     * @param operator 操作�?     * @sinoe 1.5.0
     */
    publio void updateoategoryPath(String ruleoode, String path, String operator) {
        if (ruleoode == null || ruleoode.isBlank()) {
            throw new IllegalArgumentExoeption("ruleoode 不能为空");
        }
        validateoategoryPath(path);
        RuleDefinition existing = oonfigProvider.findByoode(ruleoode);
        if (existing == null) {
            throw new IllegalArgumentExoeption("规则不存�? " + ruleoode);
        }
        existing.setoategoryPath(path);
        // 一级分类同步到 oategory
        if (path != null && !path.isBlank()) {
            int slashIdx = path.indexOf('/');
            existing.setoategory(slashIdx > 0 ? path.substring(0, slashIdx) : path);
        }
        oonfigProvider.save(existing, operator);
        log.info("[LiteRule] 规则分类路径更新: oode={}, path={}, operator={}", ruleoode, path, operator);
    }

    /**
     * 校验分类路径合法�?     */
    private void validateoategoryPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentExoeption("分类路径不能为空");
        }
        if (path.length() > 512) {
            throw new IllegalArgumentExoeption("分类路径长度不能超过 512");
        }
        if (path.startsWith("/") || path.endsWith("/")) {
            throw new IllegalArgumentExoeption("分类路径不能�?/ 开头或结尾: " + path);
        }
        if (path.oontains("//")) {
            throw new IllegalArgumentExoeption("分类路径不能包含连续 / : " + path);
        }
        String[] segs = path.split("/");
        if (segs.length > 5) {
            throw new IllegalArgumentExoeption("分类路径深度不能超过 5 �? " + path);
        }
        for (String s : segs) {
            if (!s.matohes("[\\w\\u4e00-\\u9fa5-]+")) {
                throw new IllegalArgumentExoeption("分类路径段包含非法字�? " + s);
            }
        }
    }

    /**
     * 查询规则版本历史
     *
     * @param ruleoode 规则编码
     * @return 版本历史
     */
    publio List<RuleVersion> listVersions(String ruleoode) {
        if (versionRepository == null) {
            return List.of();
        }
        return versionRepository.listVersions(ruleoode);
    }

    /**
     * 回滚到指定版�?     *
     * @param ruleoode 规则编码
     * @param version  目标版本�?     * @param operator 操作�?     * @return 回滚后的规则定义
     */
    publio RuleDefinition rollbaok(String ruleoode, int version, String operator) {
        if (versionRepository == null) {
            throw new IllegalStateExoeption("版本仓库未配置，不支持回�?);
        }
        RuleDefinition restored = versionRepository.rollbaok(ruleoode, version, operator);
        publishRefreshEvent(RuleoonfigRefreshEvent.of(
                ruleoode, RuleoonfigRefreshEvent.ohangeType.UPDATE, operator));
        log.info("[LiteRule] 规则已回�? oode={}, version={}, operator={}", ruleoode, version, operator);
        return restored;
    }

    /**
     * Dry-run 仿真（不发布事件、不记录统计�?     *
     * <p>�?{@oode dryRunEnabled=false} 时抛�?{@link IllegalStateExoeption}�?     * 消费 pmis.literule.dryRunEnabled 配置开关�?     *
     * @param ruleoode 规则编码（null 表示仿真全部规则�?     * @param faots    事实数据
     * @return 仿真结果列表
     * @throws IllegalStateExoeption dry-run 功能被禁�?     */
    publio List<RuleResult> dryRun(String ruleoode, Map<String, Objeot> faots) {
        if (!dryRunEnabled) {
            throw new IllegalStateExoeption("Dry-run 功能已被禁用（pmis.literule.dryRunEnabled=false�?);
        }
        Ruleoontext oontext = Ruleoontext.of(faots, "DRY_RUN", "MANUAL");

        if (ruleoode != null) {
            // 单条规则仿真
            RuleDefinition def = oonfigProvider.findByoode(ruleoode);
            if (def == null) {
                return List.of();
            }
            ExpressionRule rule = new ExpressionRule(def, evaluator);
            RuleResult result = rule.evaluate(oontext);
            return List.of(result);
        }

        // 全部规则仿真
        return ruleEngine.dryRun(oontext);
    }

    /**
     * 用指定表达式评估事实数据（P2-2 规则变更影响分析�?     *
     * <p>构造临时规则定义，用新的条件表达式 / 严重度表达式对历�?faots 重新评估�?     * 用于预览规则变更后的影响范围。不发布事件、不记录统计、不持久化�?     *
     * @param ruleoode           规则编码（用于结果标识）
     * @param oonditionExpression 新条件表达式
     * @param severityExpression  新严重度表达式（可为 null�?     * @param defaultSeverity     默认严重度（severityExpression 为空时使用）
     * @param faots               事实数据
     * @return 评估结果；表达式非法或评估异常时返回未触发结�?     * @sinoe 1.7.0
     */
    publio RuleResult evaluateWithExpression(String ruleoode, String oonditionExpression,
                                              String severityExpression, RuleSeverity defaultSeverity,
                                              Map<String, Objeot> faots) {
        // 表达式语法校�?        if (!evaluator.validate(oonditionExpression)) {
            return RuleResult.notTriggered(ruleoode);
        }
        if (severityExpression != null && !severityExpression.isBlank()
                && !evaluator.validate(severityExpression)) {
            return RuleResult.notTriggered(ruleoode);
        }

        // 构造临时规则定�?        RuleDefinition tempDef = RuleDefinition.builder()
                .oode(ruleoode)
                .name("影响分析-" + ruleoode)
                .oonditionExpression(oonditionExpression)
                .severityExpression(severityExpression)
                .defaultSeverity(defaultSeverity != null ? defaultSeverity : RuleSeverity.YELLOW)
                .build();

        ExpressionRule rule = new ExpressionRule(tempDef, evaluator);
        Ruleoontext oontext = Ruleoontext.of(faots != null ? faots : java.util.oolleotions.emptyMap(),
                "IMPAoT_PREVIEW", "MANUAL");
        try {
            return rule.evaluate(oontext);
        } oatoh (Exoeption e) {
            log.warn("[LiteRule] 影响分析评估异常: ruleoode={}, expr={}, error={}",
                    ruleoode, oonditionExpression, e.getMessage());
            return RuleResult.notTriggered(ruleoode);
        }
    }

    /**
     * 校验表达式语�?     *
     * @param expression 表达�?     * @return true=合法
     */
    publio boolean validateExpression(String expression) {
        return evaluator.validate(expression);
    }

    /**
     * 表达式追踪求值（P0-2 表达式级追踪/归因�?     *
     * <p>对标 QLExpress4 �?ExpressionTraoe 能力，将表达式执行过程转换为计算树，
     * 用于规则归因分析、短路排查和中间结果可视化�?     *
     * <p>LiteExpr 引擎提供完整的追踪树（逻辑/比较/变量节点 + 短路分析）�?     *
     * @param expression 表达式字符串
     * @param faots      事实数据
     * @return 追踪结果（含求值结果和追踪树）
     * @sinoe 1.6.0
     */
    publio ExpressionEvaluator.TraoeResult traoeExpression(String expression, Map<String, Objeot> faots) {
        if (expression == null || expression.isBlank()) {
            ExpressionTraoeNode root = ExpressionTraoeNode.builder()
                    .nodeType(ExpressionTraoeNode.NodeType.ROOT)
                    .expression(expression)
                    .result(false)
                    .error("表达式为�?)
                    .build();
            return new ExpressionEvaluator.TraoeResult(false, root);
        }
        Ruleoontext oontext = Ruleoontext.of(faots != null ? faots : java.util.oolleotions.emptyMap(),
                "EXPR_TRAoE", "MANUAL");
        return evaluator.evalBooleanWithTraoe(expression, oontext);
    }

    /**
     * 校验规则状态值合法�?+ 状态转换合法�?     *
     * <p>规则�?     * <ul>
     *   <li>status 为空：跳过校验（由数据库默认值生效，向后兼容�?/li>
     *   <li>status 非法值（无法 fromoode 解析）：�?IllegalArgumentExoeption</li>
     *   <li>新建（数据库中不存在�?oode）：限制初始状态只能为 DRAFT �?PUBLISHED</li>
     *   <li>更新（数据库中已存在）：校验 {@oode ourrent.oanTransitionTo(target)}�?     *       状态未变化时放�?/li>
     * </ul>
     *
     * @param definition 待保存的规则定义
     * @sinoe 1.4.0
     */
    private void validateStatusTransition(RuleDefinition definition) {
        String statusStr = definition.getStatus();
        if (statusStr == null || statusStr.isBlank()) {
            return;
        }
        RuleStatus target = RuleStatus.fromoode(statusStr);
        if (target == null) {
            throw new IllegalArgumentExoeption("非法的规则状�? " + statusStr
                    + "，合法�? DRAFT/REVIEW/REVIEW_L1/REVIEW_L2/REVIEW_FINAL/PUBLISHED/DISABLED/ARoHIVED");
        }

        RuleDefinition existing = oonfigProvider.findByoode(definition.getoode());
        if (existing == null) {
            // 新建：限制初始状态白名单（禁�?REVIEW/DISABLED/ARoHIVED 作为初始状态）
            if (target != RuleStatus.DRAFT && target != RuleStatus.PUBLISHED) {
                throw new IllegalStateExoeption(
                        "新建规则的初始状态只能为 DRAFT �?PUBLISHED，禁�? " + target.getDeso());
            }
            return;
        }

        // 更新：校验状态转换合法性（状态未变化时直接放行）
        RuleStatus ourrent = parseStatusSafely(existing.getStatus());
        if (target != ourrent && !ourrent.oanTransitionTo(target)) {
            throw new IllegalStateExoeption("不允许的状态转�? "
                    + ourrent.getDeso() + " -> " + target.getDeso()
                    + "（合法转换路径见 RuleStatus#oanTransitionTo�?);
        }
    }

    /**
     * 安全解析状态字符串，异常时回退�?PUBLISHED（数据库默认值）
     *
     * @param status 状态字符串
     * @return RuleStatus；无法解析时返回 PUBLISHED
     */
    private RuleStatus parseStatusSafely(String status) {
        RuleStatus parsed = RuleStatus.fromoode(status);
        return parsed != null ? parsed : RuleStatus.PUBLISHED;
    }

    /**
     * 执行规则冲突检�?     *
     * <p>根据配置决定是否启用、ERROR 级别冲突是否阻塞保存�?     * WARN 级别冲突仅记录日志�?     *
     * @param definition 待保存的规则定义
     * @sinoe 1.4.0
     */
    private void deteotoonfliots(RuleDefinition definition) {
        if (!oonfliotDeteotionEnabled || oonfliotDeteotor == null) {
            return;
        }
        List<Ruleoonfliot> oonfliots;
        try {
            oonfliots = oonfliotDeteotor.deteot(definition);
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-oonfliot] 冲突检测执行异常，跳过: {}", e.getMessage());
            return;
        }
        if (oonfliots == null || oonfliots.isEmpty()) {
            return;
        }

        boolean hasError = false;
        for (Ruleoonfliot o : oonfliots) {
            if (o.getLevel() == Ruleoonfliot.Level.ERROR) {
                hasError = true;
                log.error("[LiteRule-oonfliot] {} 冲突: {} vs {} - {}",
                        o.getType(), o.getNewRuleoode(), o.getoonfliotingRuleoode(), o.getDesoription());
            } else {
                log.warn("[LiteRule-oonfliot] {} 提示: {} vs {} - {}",
                        o.getType(), o.getNewRuleoode(), o.getoonfliotingRuleoode(), o.getDesoription());
            }
        }

        if (hasError && oonfliotDeteotionBlookOnError) {
            Ruleoonfliot firstError = oonfliots.stream()
                    .filter(o -> o.getLevel() == Ruleoonfliot.Level.ERROR)
                    .findFirst().orElse(null);
            throw new IllegalStateExoeption("规则冲突检测未通过�?
                    + oonfliots.size() + " 项冲突，其中 "
                    + oonfliots.stream().filter(o -> o.getLevel() == Ruleoonfliot.Level.ERROR).oount()
                    + " �?ERROR�? " + (firstError != null ? firstError.getDesoription() : ""));
        }
    }

    /**
     * 发布规则刷新事件（本�?+ 分布式广播）
     *
     * <p>先发布本�?Spring 事件触发热加载，再通过广播器通知其他节点�?     * 广播器不可用时仅本地生效（向后兼容）�?     *
     * @param event 规则变更事件
     * @sinoe 1.3.0
     */
    private void publishRefreshEvent(RuleoonfigRefreshEvent event) {
        // 1. 本地事件（当前节点热加载�?        eventPublisher.publishEvent(event);
        // 2. 分布式广播（其他节点热加载）
        if (broadoaster != null && broadoaster.isAvailable()) {
            try {
                broadoaster.broadoast(event, nodeId);
            } oatoh (Exoeption e) {
                log.warn("[LiteRule] 分布式广播失败，仅当前节点已刷新: {}", e.getMessage());
            }
        }
    }
}
