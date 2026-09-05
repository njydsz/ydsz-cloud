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
 *   <li><b>倒排索引（P1-2）</b>：field -> ruleCodes，按 facts 字段过滤候选规则，减少不必要的表达式求值
 * </ul>
 *
 * <p>1.6.0 起新增环境维度索引（P1-5）：与 tenantId 维度正交，支持 dev/staging/prod 环境隔离。
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

  /** 倒排索引：字段名 -> 引用该字段的规则编码集合（P1-2） */
  private final Map<String, Set<String>> fieldToRules = new ConcurrentHashMap<>();

  /** 正排索引：规则编码 -> 该规则引用的全部字段名集合（P1-2） */
  private final Map<String, Set<String>> ruleToFields = new ConcurrentHashMap<>();

  /** α 节点共享索引（P2 轻量 RETE α 网络）：字段|操作符 -> 规则编码集合 */
  private final Map<String, Set<String>> fieldOpIndex = new ConcurrentHashMap<>();

  /** 全局规则列表（兼容无场景过滤的场景） */
  private volatile List<Rule> allRules = Collections.emptyList();

  /** 是否启用索引 */
  private volatile boolean indexEnabled = false;

  /** 分布式锁服务（P1-3） */
  private volatile LockService lockService;

  /** LiteExpr 关键字与内置函数，字段提取时不应作为变量返回 */
  private static final Set<String> EXPR_KEYWORDS = Set.of(
      "true", "false", "nil", "null", "RED", "YELLOW", "INFO", "GREEN",
      "if", "else", "return", "seq", "lambda", "fn", "let", "for", "while",
      "break", "continue", "println", "print", "p", "string", "long", "double",
      "boolean", "int", "math", "Math", "max", "min", "abs", "round", "floor",
      "ceil", "sqrt", "pow", "log", "contains", "startsWith", "endsWith",
      "length", "count", "sum", "avg", "rand", "now", "date", "tuple", "map",
      "set", "sorted", "sort");

  /** 字段名提取正则 */
  private static final Pattern FIELD_PATTERN =
      Pattern.compile("([a-zA-Z_\\u4e00-\\u9fa5][a-zA-Z0-9_\\u4e00-\\u9fa5]*)");

  /** 比较表达式模式：var OP value（P2 α 节点提取用） */
  private static final Pattern COMPARISON_PATTERN =
      Pattern.compile("^([a-zA-Z_]\\w*)\\s*(>=|<=|>|<|==|!=)\\s*(.+)$");

  /**
   * 注入分布式锁服务
   *
   * @param lockService 锁服务实例
   */
  public void setLockService(LockService lockService) {
    this.lockService = lockService;
  }

  /**
   * 重建索引
   *
   * @param rules 当前全部规则列表（已按优先级排序）
   */
  public void rebuildIndex(List<Rule> rules) {
    executeWithLock("literule:index:rebuild", () -> {
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

      for (Rule rule : rules) {
        addToIndexInternal(rule);
      }

      log.info(
          "[LiteRule-Indexer] 索引重建完成: totalRules={}, tenants={}, envs={}, scopes={}, "
              + "mutexGroups={}, fieldIndexSize={}, alphaNodes={}",
          rules.size(), tenantIndex.size(), environmentIndex.size(), scopeIndex.size(),
          mutexGroupIndex.size(), fieldToRules.size(), fieldOpIndex.size());
    });
  }

  /**
   * 增量添加规则到索引
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
   * @param ruleCode 规则编码
   */
  public void removeFromIndex(String ruleCode) {
    executeWithLock("literule:index:modify", () -> {
      if (!indexEnabled) {
        return;
      }
      tenantIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
      environmentIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
      scopeIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
      mutexGroupIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
      allRules.removeIf(r -> ruleCode.equals(r.getCode()));
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
      fieldOpIndex.entrySet().removeIf(entry -> {
        Set<String> codes = entry.getValue();
        codes.remove(ruleCode);
        return codes.isEmpty();
      });
    });
  }

  /**
   * 查找候选规则集（按租户、环境、场景、互斥组过滤）
   *
   * @param tenantId               租户 ID
   * @param environment            环境标识
   * @param scenario               场景
   * @param triggeredMutexGroups   已命中的互斥组集合
   * @return 候选规则列表
   */
  public List<Rule> findCandidates(
      String tenantId, String environment, String scenario, Set<String> triggeredMutexGroups) {
    if (!indexEnabled) {
      return allRules;
    }

    // 1. 环境过滤
    String tenantKey = tenantId != null ? tenantId : "1";
    String envKey = (environment != null && !environment.isBlank()) ? environment : RuleEnvironment.DEFAULT;
    List<Rule> defaultEnvRules = environmentIndex.get(tenantKey + "|" + RuleEnvironment.DEFAULT);
    List<Rule> exactEnvRules = RuleEnvironment.DEFAULT.equals(envKey)
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
      envFilteredRules = new ArrayList<>(defaultEnvRules.size() + exactEnvRules.size());
      envFilteredRules.addAll(defaultEnvRules);
      Set<String> existingCodes = new HashSet<>(defaultEnvRules.size());
      for (Rule existing : defaultEnvRules) {
        if (existing.getCode() != null) {
          existingCodes.add(existing.getCode());
        }
      }
      for (Rule rule : exactEnvRules) {
        String code = rule.getCode();
        if (code != null && existingCodes.add(code)) {
          envFilteredRules.add(rule);
        } else if (code == null) {
          envFilteredRules.add(rule);
        }
      }
    }
    envFilteredRules.sort((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()));

    // 2. 场景过滤
    List<Rule> scopedRules;
    if (scenario == null || "DEFAULT".equals(scenario)) {
      scopedRules = envFilteredRules;
    } else {
      scopedRules = new ArrayList<>(16);
      for (Rule rule : envFilteredRules) {
        String scopeVal = rule.getScope();
        if (scopeVal == null || scopeVal.isBlank() || "ALL".equals(scopeVal) || scopeVal.equals(scenario)) {
          scopedRules.add(rule);
        }
      }
    }

    // 3. 互斥组过滤
    if (triggeredMutexGroups != null && !triggeredMutexGroups.isEmpty()) {
      List<Rule> mutexFiltered = new ArrayList<>(scopedRules.size());
      for (Rule rule : scopedRules) {
        String mutexGroup = rule.getMutexGroup();
        if (mutexGroup == null || mutexGroup.isBlank() || !triggeredMutexGroups.contains(mutexGroup)) {
          mutexFiltered.add(rule);
        }
      }
      return mutexFiltered;
    }

    return scopedRules;
  }

  /**
   * 按 facts 字段进行二级过滤（P1-2 增强）
   *
   * @param candidates 候选规则列表
   * @param factKeys   facts 中存在的字段名集合
   * @return 过滤后的候选规则列表
   */
  public List<Rule> filterByFactKeys(List<Rule> candidates, Set<String> factKeys) {
    if (candidates == null || candidates.isEmpty() || factKeys == null || factKeys.isEmpty()) {
      return candidates;
    }
    List<Rule> filtered = new ArrayList<>(candidates.size());
    for (Rule rule : candidates) {
      Set<String> ruleFields = ruleToFields.get(rule.getCode());
      // 未建立倒排索引的规则（无表达式 / 未解析）保守保留
      if (ruleFields == null || ruleFields.isEmpty()) {
        filtered.add(rule);
        continue;
      }
      // 规则的所有字段都在 facts 中时才保留
      if (factKeys.containsAll(ruleFields)) {
        filtered.add(rule);
      }
    }
    return filtered;
  }

  // ════════════════════════════════════════════════════════════
  // 内部方法
  // ════════════════════════════════════════════════════════════

  /**
   * 增量将单条规则加入索引（无锁版本，调用方需加锁）
   *
   * @param rule 待索引规则
   */
  private void addToIndexInternal(Rule rule) {
    if (rule == null || rule.getTenantId() == null) {
      return;
    }
    String tenantKey = rule.getTenantId();
    String env = rule.getEnvironment() != null ? rule.getEnvironment() : RuleEnvironment.DEFAULT;
    String envKey = tenantKey + "|" + env;

    // 租户索引
    tenantIndex.computeIfAbsent(tenantKey, k -> new ArrayList<>(32)).add(rule);
    // 环境索引
    environmentIndex.computeIfAbsent(envKey, k -> new ArrayList<>(32)).add(rule);
    // 场景索引
    String scope = rule.getScope();
    if (scope != null && !scope.isBlank()) {
      String scopeKey = tenantKey + "|" + scope;
      scopeIndex.computeIfAbsent(scopeKey, k -> new ArrayList<>(16)).add(rule);
    }
    // 互斥组索引
    if (rule.getMutexGroup() != null && !rule.getMutexGroup().isBlank()) {
      String mutexKey = tenantKey + "|" + rule.getMutexGroup();
      mutexGroupIndex.computeIfAbsent(mutexKey, k -> new ArrayList<>(8)).add(rule);
    }

    // 倒排索引（P1-2）
    Set<String> fields = extractFields(rule);
    if (!fields.isEmpty()) {
      ruleToFields.put(rule.getCode(), fields);
      for (String field : fields) {
        fieldToRules.computeIfAbsent(field, k -> new LinkedHashSet<>()).add(rule.getCode());
      }
      // α 节点索引（P2）
      Set<String> fieldOps = extractFieldOps(rule);
      for (String fieldOp : fieldOps) {
        fieldOpIndex.computeIfAbsent(fieldOp, k -> new LinkedHashSet<>()).add(rule.getCode());
      }
    }
  }

  /**
   * 从规则表达式中提取引用的字段名
   *
   * @param rule 规则
   * @return 字段名集合
   */
  private Set<String> extractFields(Rule rule) {
    Set<String> fields = new HashSet<>(8);
    RuleDefinitionDTO def = rule.getRuleDefinition();
    if (def == null) {
      return fields;
    }
    // 从条件表达式提取
    if (def.getConditionExpression() != null) {
      extractFieldNames(def.getConditionExpression(), fields);
    }
    // 从严重度表达式提取（可能引用额外字段）
    if (def.getSeverityExpression() != null) {
      extractFieldNames(def.getSeverityExpression(), fields);
    }
    return fields;
  }

  /**
   * 递归从文本中提取字段名
   *
   * @param text   输入文本
   * @param fields 收集集合
   */
  private void extractFieldNames(String text, Set<String> fields) {
    if (text == null || text.isBlank()) {
      return;
    }
    Matcher matcher = FIELD_PATTERN.matcher(text);
    while (matcher.find()) {
      String name = matcher.group(1);
      if (!EXPR_KEYWORDS.contains(name)) {
        fields.add(name);
      }
    }
  }

  /**
   * 从规则条件表达式中提取 α 节点标识（字段|操作符）
   *
   * @param rule 规则
   * @return "字段|操作符" 集合
   */
  private Set<String> extractFieldOps(Rule rule) {
    Set<String> fieldOps = new HashSet<>(4);
    RuleDefinitionDTO def = rule.getRuleDefinition();
    if (def == null || def.getConditionExpression() == null) {
      return fieldOps;
    }
    // 按行提取比较表达式
    for (String line : def.getConditionExpression().split("[\\r\\n;]")) {
      Matcher m = COMPARISON_PATTERN.matcher(line.trim());
      if (m.matches()) {
        fieldOps.add(m.group(1) + "|" + m.group(2));
      }
    }
    return fieldOps;
  }

  /**
   * 加锁执行（分布式锁或本地 synchronized）
   *
   * @param lockKey 锁 key
   * @param action  操作
   */
  private void executeWithLock(String lockKey, Runnable action) {
    if (lockService != null) {
      lockService.executeWithLock(lockKey, action);
    } else {
      synchronized (this) {
        action.run();
      }
    }
  }

  /**
   * 加锁执行（带返回值）
   *
   * @param lockKey 锁 key
   * @param action  操作
   * @param <T>     返回类型
   * @return 操作结果
   */
  private <T> T executeWithLock(String lockKey, Supplier<T> action) {
    if (lockService != null) {
      return lockService.executeWithLock(lockKey, action);
    }
    synchronized (this) {
      return action.get();
    }
  }

  /**
   * 获取当前索引的规则总数
   *
   * @return 规则总数
   */
  public int size() {
    return allRules.size();
  }

  /**
   * 判断索引是否启用
   *
   * @return true 表示启用
   */
  public boolean isIndexEnabled() {
    return indexEnabled;
  }

  /**
   * 判断是否存在倒排索引（P1-2 二级索引）
   *
   * @return true 表示倒排索引非空
   */
  public boolean hasFieldIndex() {
    return indexEnabled && !fieldToRules.isEmpty();
  }

  /**
   * 按 facts 字段进行二级过滤（P1-2 增强）
   *
   * <p>{@code filterByFacts} 是 {@link #filterByFactKeys(List, Set)} 的别名，语义更清晰。
   *
   * @param candidates 候选规则列表
   * @param factKeys   facts 中存在的字段名集合
   * @return 过滤后的候选规则列表
   */
  public List<Rule> filterByFacts(List<Rule> candidates, Set<String> factKeys) {
    return filterByFactKeys(candidates, factKeys);
  }
}
