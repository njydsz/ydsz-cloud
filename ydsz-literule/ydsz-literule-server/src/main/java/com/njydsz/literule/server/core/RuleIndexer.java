package com.njydsz.literule.server.core;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleEnvironment;

/**
 * 规则索引器（P0-1 轻量 RETE 优化，P1-2 倒排索引优化）
 *
 * <p>解决大规则量（>500）场景下的线性扫描性能瓶颈。通过多维度索引将候选规则集缩小：
 *
 * <ul>
 *   <li><b>租户索引</b>：tenantId -> rules，避免遍历其他租户的规则
 *   <li><b>环境索引</b>：tenantId + "|" + environment -> rules，跳过不匹配的环境（1.6.0 起，P1-5）
 *   <li><b>场景索引</b>：tenantId + scope -> rules，跳过不匹配的 scope
 *   <li><b>互斥组索引</b>：tenantId + mutexGroup -> rules（按优先级排序），O(1) 查找
 *   <li><b>倒排索引（P1-2）</b>：field -> ruleCodes，按 facts 字段过滤候选规则， 减少不必要的表达式求值，对标银行风控 4 倍性能提升
 * </ul>
 *
 * <p>1.6.0 起新增环境维度索引（P1-5）：与 tenantId 维度正交，支持 dev/staging/prod 环境隔离。 规则的 environment 为 {@link
 * RuleEnvironment#DEFAULT "default"} 时匹配任何上下文环境（向后兼容）； 非 "default" 时必须与 {@code
 * context.getEnvironment()} 完全匹配。
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>索引构建与 {@link DefaultRuleEngine} 的 CopyOnWriteArrayList 并行维护，保持一致
 *   <li>索引数据结构使用 {@link ConcurrentHashMap}，线程安全
 *   <li>索引为可选优化：当规则数 < 阈值（默认 200）时，直接返回全量规则，不使用索引
 *   <li>索引命中后的候选规则集仍保持按优先级排序
 *   <li>倒排索引作为第二层过滤：在 tenant/environment/scope/mutexGroup 过滤之后， 按 facts
 *       字段集合进一步缩小候选集，仅对条件表达式引用的字段全部存在于 facts 中的规则求值
 * </ul>
 *
 * <p>性能预期：规则数 1000+ 时，单次评估候选规则数降至 10-100 条，性能提升 10-20x。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class RuleIndexer {

  /** 索引启用的最小规则数阈值（低于此值不启用索引） */
  private static final int INDEX_THRESHOLD = 200;

  /** 租户索引：tenantId -> 规则列表（按优先级排序） */
  private final Map<String, List<Rule>> tenantIndex = new ConcurrentHashMap<>();

  /** 环境索引：tenantId + "|" + environment -> 规则列表（1.6.0 起，P1-5） */
  private final Map<String, List<Rule>> environmentIndex = new ConcurrentHashMap<>();

  /** 场景索引：tenantId + "|" + scope -> 规则列表 */
  private final Map<String, List<Rule>> scopeIndex = new ConcurrentHashMap<>();

  /** 互斥组索引：tenantId + "|" + mutexGroup -> 规则列表（按优先级排序） */
  private final Map<String, List<Rule>> mutexGroupIndex = new ConcurrentHashMap<>();

  /**
   * 倒排索引：字段名 -> 引用该字段的规则编码集合（P1-2）
   *
   * <p>key=字段名（如 "amount"、"score"），value=引用该字段的规则编码集合。 用于按 facts 字段快速过滤候选规则。
   */
  private final Map<String, Set<String>> fieldToRules = new ConcurrentHashMap<>();

  /**
   * 正排索引：规则编码 -> 该规则引用的全部字段名集合（P1-2）
   *
   * <p>key=规则编码，value=该规则条件表达式中引用的字段名集合。 用于检查规则的字段集合是否是 factKeys 的子集。
   */
  private final Map<String, Set<String>> ruleToFields = new ConcurrentHashMap<>();

  /**
   * α 节点共享索引（P2 轻量 RETE α 网络）：字段|操作符 -> 规则编码集合
   *
   * <p>同一字段的同一比较操作（如 {@code amount|>}）共享一个 α 节点，
   * 查询时按 "字段|操作符" 直接定位引用该模式的规则集合， 减少逐条表达式匹配开销。
   */
  private final Map<String, Set<String>> fieldOpIndex = new ConcurrentHashMap<>();

  /** 全局规则列表（兼容无场景过滤的场景） */
  private volatile List<Rule> allRules = Collections.emptyList();

  /** 是否启用索引 */
  private volatile boolean indexEnabled = false;

  /** 分布式锁服务（P1-3：替代 synchronized，支持集群部署） */
  private volatile LockService lockService;

  /** LiteExpr 关键字与内置函数，字段提取时不应作为变量返回。 与 {@code LiteExprEngine.EXPR_KEYWORDS} 保持一致。 */
  private static final Set<String> EXPR_KEYWORDS =
      Set.of(
          "true",
          "false",
          "nil",
          "null",
          "RED",
          "YELLOW",
          "INFO",
          "GREEN",
          "if",
          "else",
          "return",
          "seq",
          "lambda",
          "fn",
          "let",
          "for",
          "while",
          "break",
          "continue",
          "println",
          "print",
          "p",
          "string",
          "long",
          "double",
          "boolean",
          "int",
          "math",
          "Math",
          "max",
          "min",
          "abs",
          "round",
          "floor",
          "ceil",
          "sqrt",
          "pow",
          "log",
          "contains",
          "startsWith",
          "endsWith",
          "length",
          "count",
          "sum",
          "avg",
          "rand",
          "now",
          "date",
          "tuple",
          "map",
          "set",
          "sorted",
          "sort");

  /**
   * 字段名提取正则（支持英文/下划线/中文标识符）
   *
   * <p>首字符：英文字母、下划线或中文（CJK 统一汉字）； 后续字符：英文字母、数字、下划线或中文。
   */
  private static final Pattern FIELD_PATTERN =
      Pattern.compile("([a-zA-Z_\\u4e00-\\u9fa5][a-zA-Z0-9_\\u4e00-\\u9fa5]*)");

  /** 比较表达式模式：var OP value（P2 α 节点提取用） */
  private static final Pattern COMPARISON_PATTERN =
      Pattern.compile("^([a-zA-Z_]\\w*)\\s*(>=|<=|>|<|==|!=)\\s*(.+)$");

  /**
   * 重建索引
   *
   * <p>在规则批量注册/注销后调用，重建全部索引。 单条注册时使用 {@link #addToIndex(Rule)} 增量更新。
   *
   * <p>P1-3：集群部署时使用分布式锁保障互斥，单节点部署时使用本地 synchronized（向后兼容）。
   *
   * @param rules 当前全部规则列表（已按优先级排序）
   */
  public void rebuildIndex(List<Rule> rules) {
    executeWithLock("literule:index:rebuild", () -> {
      // 清空旧索引
      tenantIndex.clear();
      environmentIndex.clear();
      scopeIndex.clear();
      mutexGroupIndex.clear();
      fieldToRules.clear();
      ruleToFields.clear();
      fieldOpIndex.clear();

      allRules = new ArrayList<>(rules);
      indexEnabled = rules.size() >= INDEX_THRESHOLD;

      if (!indexEnabled) {
        log.debug("[LiteRule-Indexer] 规则数 {} < 阈值 {}，索引未启用", rules.size(), INDEX_THRESHOLD);
        return;
      }

      // 构建索引
      for (Rule rule : rules) {
        addToIndexInternal(rule);
      }

      log.info(
          "[LiteRule-Indexer] 索引重建完成: totalRules={}, tenants={}, envs={}, scopes={}, "
              + "mutexGroups={}, fieldIndexSize={}, alphaNodes={}",
          rules.size(),
          tenantIndex.size(),
          environmentIndex.size(),
          scopeIndex.size(),
          mutexGroupIndex.size(),
          fieldToRules.size(),
          fieldOpIndex.size());
    });
  }

  /**
   * 增量添加规则到索引
   *
   * <p>P1-3：集群部署时使用分布式锁保障互斥，单节点部署时使用本地 synchronized（向后兼容）。
   *
   * @param rule 新注册的规则
   */
  public void addToIndex(Rule rule) {
    executeWithLock("literule:index:modify", () -> {
      if (!indexEnabled) {
        return;
      }
      addToIndexInternal(rule);
    });
  }

  /**
   * 从索引中移除规则
   *
   * <p>P1-3：集群部署时使用分布式锁保障互斥，单节点部署时使用本地 synchronized（向后兼容）。
   *
   * @param ruleCode 规则编码
   */
  public void removeFromIndex(String ruleCode) {
    executeWithLock("literule:index:modify", () -> {
      if (!indexEnabled) {
        return;
      }
      // 由于索引按引用存储，需要遍历移除
      tenantIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
      environmentIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
      scopeIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
      mutexGroupIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
      allRules.removeIf(r -> ruleCode.equals(r.getCode()));
      // 同步清除倒排索引（P1-2）
      Set<String> fields = ruleToFields.remove(ruleCode);
      if (fields != null) {
        for (String field : fields) {
          Set<String> ruleCodes = fieldToRules.get(field);
          if (ruleCodes != null) {
            ruleCodes.remove(ruleCode);
            if (ruleCodes.isEmpty()) {
              fieldToRules.remove(field);
            }
          }
        }
      }
      // 同步清除 α 节点共享索引（P2）
      fieldOpIndex.entrySet().removeIf(entry -> {
        Set<String> codes = entry.getValue();
        codes.remove(ruleCode);
        return codes.isEmpty();
      });
    });
  }

  /**
   * 查找候选规则集
   *
   * <p>按租户、环境、场景、互斥组过滤，返回按优先级排序的候选规则列表。 调用方仍需做互斥组短路逻辑（因为短路依赖运行时命中状态）。
   *
   * <p>1.6.0 起增加 environment 过滤（P1-5）： 规则 environment 为 {@link RuleEnvironment#DEFAULT "default"}
   * 时匹配任何上下文环境； 非 "default" 时必须与 {@code environment} 完全匹配。
   *
   * @param tenantId 租户 ID
   * @param environment 环境标识（dev/staging/prod/default）
   * @param scenario 场景（null 或 "DEFAULT" 表示全部）
   * @param triggeredMutexGroups 已命中的互斥组集合（用于排除）
   * @return 候选规则列表（按优先级排序）
   * @since 26.09.01
   */
  public List<Rule> findCandidates(
      String tenantId, String environment, String scenario, Set<String> triggeredMutexGroups) {
    if (!indexEnabled) {
      return allRules;
    }

    // 1. 环境过滤（1.6.0 起，P1-5）
    // 合并 default 环境（匹配任何上下文）+ 当前环境的规则
    String tenantKey = tenantId != null ? tenantId : "1";
    String envKey =
        (environment != null && !environment.isBlank()) ? environment : RuleEnvironment.DEFAULT;
    List<Rule> defaultEnvRules = environmentIndex.get(tenantKey + "|" + RuleEnvironment.DEFAULT);
    List<Rule> exactEnvRules =
        RuleEnvironment.DEFAULT.equals(envKey)
            ? null
            : environmentIndex.get(tenantKey + "|" + envKey);

    List<Rule> envFilteredRules;
    if (defaultEnvRules == null && exactEnvRules == null) {
      return Collections.emptyList();
    }
    if (exactEnvRules == null) {
      envFilteredRules = new ArrayList<>(defaultEnvRules);
    } else if (defaultEnvRules == null) {
      envFilteredRules = new ArrayList<>(exactEnvRules);
    } else {
      // 合并两个列表并去重（按规则编码）— O(n) 去重（P1-4 优化）
      envFilteredRules = new ArrayList<>(defaultEnvRules.size() + exactEnvRules.size());
      envFilteredRules.addAll(defaultEnvRules);
      // 构建已有编码集合，将去重从 O(n²) 降为 O(n)
      Set<String> existingCodes = new HashSet<>(defaultEnvRules.size());
      for (Rule existing : defaultEnvRules) {
        if (existing.getCode() != null) {
          existingCodes.add(existing.getCode());
        }
      }
      for (Rule rule : exactEnvRules) {
        String code = rule.getCode();
        if (code != null && existingCodes.add(code)) {
          // Set.add 返回 true 表示不存在重复
          envFilteredRules.add(rule);
        } else if (code == null) {
          // 无编码规则直接添加（无法去重）
          envFilteredRules.add(rule);
        }
      }
    }
    // 按优先级排序（合并后可能乱序）
    envFilteredRules.sort((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()));

    // 2. 场景过滤
    List<Rule> scopedRules;
    if (scenario == null || "DEFAULT".equals(scenario)) {
      // DEFAULT 场景：评估全部规则
      scopedRules = envFilteredRules;
    } else {
      // 精确场景：取 scope 匹配 + scope=ALL/null 的规则
      scopedRules = new ArrayList<>(16);
}
}
}