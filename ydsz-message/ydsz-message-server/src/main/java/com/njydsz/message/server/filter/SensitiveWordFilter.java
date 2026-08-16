package com.njydsz.message.server.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.message.server.config.MessageProperties;

/**
 * 敏感词过滤器：对消息发送内容做敏感词替换。
 *
 * <p>P1-1: 使用 DFA（确定有限自动机）字典树算法替代逐词遍历， 时间复杂度从 O(content_length × word_count) 优化为 O(content_length)，
 * 支持万级词库下的高性能实时过滤。
 *
 * <p>词库来源：内置默认词 + 配置 {@code ydsz.message.sensitive-words} 覆盖。 支持运行时 {@link #reload(Set)} 刷新词库。
 *
 * <p>过滤策略：命中敏感词替换为 {@code ***}，多次命中分别替换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SensitiveWordFilter {

  /** 默认占位符 */
  public static final String MASK = "***";

  /** 内置默认敏感词（生产环境应通过配置或外部词库覆盖） */
  private static final Set<String> DEFAULT_WORDS = Set.of("政治敏感", "色情", "赌博", "毒品", "诈骗", "违禁");

  /** DFA 字典树根节点（volatile 保证 reload 后的可见性） */
  private volatile DfaNode dfaRoot = new DfaNode();

  /** P3-3.2: 敏感词过滤配置统一从 MessageProperties 读取 */
  private final MessageProperties messageProperties;

  /**
   * 构造时根据 {@link MessageProperties.SensitiveFilterConfig} 初始化 DFA 词库。
   *
   * <p>支持运行时通过 {@link #reload(Set)} 刷新词库。
   */
  public SensitiveWordFilter(MessageProperties messageProperties) {
    this.messageProperties = messageProperties;
    MessageProperties.SensitiveFilterConfig cfg = messageProperties.getSensitiveFilter();
    reload(parseWords(cfg.getWords()));
  }

  /** 当前是否启用敏感词过滤。 */
  private boolean isEnabled() {
    return messageProperties.getSensitiveFilter().isEnabled();
  }

  /**
   * 过滤内容中的敏感词（DFA 算法）。
   *
   * <p>逐字符遍历内容，对每个起始字符尝试在 DFA 树中做最长匹配。 匹配到完整敏感词时替换为 MASK，未匹配则输出原字符继续。
   *
   * @param content 原始内容
   * @return 过滤后的内容；未启用或内容为空时原样返回
   */
  public String filter(String content) {
    if (!isEnabled() || !StringUtils.hasText(content)) {
      return content;
    }
    DfaNode root = dfaRoot;
    if (root.children.isEmpty()) {
      return content;
    }
    StringBuilder result = new StringBuilder(content.length());
    int i = 0;
    int len = content.length();
    while (i < len) {
      char c = content.charAt(i);
      DfaNode child = root.children.get(c);
      if (child == null) {
        // 当前字符不在 DFA 树第一层，直接输出
        result.append(c);
        i++;
        continue;
      }
      // 进入 DFA 树匹配，尝试最长匹配
      int matchEnd = -1;
      DfaNode cur = child;
      // 检查单字符敏感词
      if (cur.isEnd) {
        matchEnd = i + 1;
      }
      int j = i + 1;
      while (j < len) {
        cur = cur.children.get(content.charAt(j));
        if (cur == null) {
          break;
        }
        j++;
        if (cur.isEnd) {
          matchEnd = j;
        }
      }
      if (matchEnd > 0) {
        // 匹配到敏感词，替换为 MASK
        result.append(MASK);
        i = matchEnd;
      } else {
        // 未匹配到完整敏感词，输出当前字符继续
        result.append(c);
        i++;
      }
    }
    return result.toString();
  }

  /**
   * 重新加载敏感词库（运行时刷新）。
   *
   * <p>P1-1: 将词库构建为 DFA 字典树，构建完成后替换根节点引用（volatile 保证可见性）。
   *
   * @param newWords 新词库；为空时回退到默认词库
   */
  public void reload(Set<String> newWords) {
    Set<String> words = (newWords == null || newWords.isEmpty()) ? DEFAULT_WORDS : newWords;
    DfaNode newRoot = new DfaNode();
    for (String word : words) {
      if (!StringUtils.hasText(word)) {
        continue;
      }
      String trimmed = word.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      addWord(newRoot, trimmed);
    }
    // 原子替换：构建完成后替换引用
    dfaRoot = newRoot;
    log.info("[SensitiveWordFilter] DFA 词库已加载: count={} enabled={}", words.size(), isEnabled());
  }

  /**
   * 当前词库快照（仅用于测试与监控）。
   *
   * @return 不可修改的词库视图
   */
  public Set<String> currentWords() {
    Set<String> result = new LinkedHashSet<>();
    collectWords(dfaRoot, new StringBuilder(), result);
    return Collections.unmodifiableSet(result);
  }

  /**
   * 将一个敏感词添加到 DFA 字典树。
   *
   * @param root DFA 根节点
   * @param word 敏感词（非空）
   */
  private void addWord(DfaNode root, String word) {
    DfaNode cur = root;
    for (int i = 0; i < word.length(); i++) {
      char c = word.charAt(i);
      cur = cur.children.computeIfAbsent(c, k -> new DfaNode());
    }
    cur.isEnd = true;
  }

  /** 递归收集 DFA 树中的所有完整词（用于 currentWords 快照）。 */
  private void collectWords(DfaNode node, StringBuilder prefix, Set<String> result) {
    if (node.isEnd && prefix.length() > 0) {
      result.add(prefix.toString());
    }
    for (Map.Entry<Character, DfaNode> entry : node.children.entrySet()) {
      prefix.append(entry.getKey());
      collectWords(entry.getValue(), prefix, result);
      prefix.deleteCharAt(prefix.length() - 1);
    }
  }

  /** 解析配置字符串：支持逗号分隔。 */
  private Set<String> parseWords(String raw) {
    if (!StringUtils.hasText(raw)) {
      return Collections.emptySet();
    }
    Set<String> set = new LinkedHashSet<>();
    List<String> parts = new ArrayList<>(Arrays.asList(raw.split(",")));
    for (String part : parts) {
      String trimmed = part.trim();
      if (StringUtils.hasText(trimmed)) {
        set.add(trimmed);
      }
    }
    return set;
  }

  /**
   * DFA 字典树节点。
   *
   * <p>每个节点包含子节点映射和一个 isEnd 标记（表示从根到当前节点构成一个完整敏感词）。 使用 ConcurrentHashMap 保证并发读安全。
   */
  private static class DfaNode {
    final Map<Character, DfaNode> children = new ConcurrentHashMap<>();
    volatile boolean isEnd = false;
  }
}
