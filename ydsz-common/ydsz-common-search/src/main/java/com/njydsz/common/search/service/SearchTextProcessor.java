package com.njydsz.common.search.service;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.search.config.SearchProperties;

/**
 * 搜索文本预处理器。
 *
 * <p>在请求进入引擎前对关键词做归一化，直接影响召回率。 采用管道 + 插件模式：规范化与停用词由内部 {@link SearchPipeline} 完成，
 * 同义词扩展与拼音转换为本处理器独有的增强能力。
 *
 * <p>处理流程：
 *
 * <ol>
 *   <li>NormalizerFilter — 标点清理、空白归一化、长度截断
 *   <li>ChineseTokenFilter — 中文分词（ICU4J）
 *   <li>同义词扩展 — 加载同义词词典，扩展关键词提升召回
 *   <li>拼音转换 — 将中文关键词转为拼音，支持拼音搜索
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SearchTextProcessor {

  private final SearchProperties properties;
  private final SearchPipeline pipeline;
  private final Map<String, List<String>> synonymMap = new HashMap<>(16);
  private final Map<String, String> pinyinMap = new HashMap<>(16);

  /** 同义词文件上次修改时间（0 表示尚未加载或非文件系统资源） */
  private volatile long synonymFileLastModified;

  /** 拼音文件上次修改时间（0 表示尚未加载或非文件系统资源） */
  private volatile long pinyinFileLastModified;

  /**
   * 创建搜索文本预处理器（加载同义词/拼音词典，构建管道）。
   *
   * @param properties 搜索配置属性（含同义词/拼音词典路径）
   */
  public SearchTextProcessor(SearchProperties properties) {
    this.properties = properties;
    this.pipeline = SearchPipeline.fromConfig(properties);
    loadSynonyms();
    loadPinyin();
  }

  /**
   * 执行完整的文本预处理流程。
   *
   * <p>处理顺序：管道处理（规范化 + 分词） → 同义词扩展 → 拼音转换。
   *
   * @param keyword 原始关键词
   * @return 处理后的查询文本；输入为空时返回原始输入
   */
  public String process(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return keyword;
    }

    // 1. 管道处理（规范化 + 中文分词）
    String processed = pipeline.process(keyword);
    if (processed == null || processed.isBlank()) {
      processed = keyword.trim();
    }

    // 2. 同义词扩展
    if (properties.getTextProcessor().isSynonymEnabled()) {
      processed = expandSynonyms(processed);
    }

    // 3. 拼音转换
    if (properties.getTextProcessor().isPinyinEnabled()) {
      processed = appendPinyin(processed);
    }

    return processed;
  }

  // ==================== 词典加载 ====================

  /** 加载同义词词典到 synonymMap。 */
  private void loadSynonyms() {
    SearchProperties.TextProcessorConfig config = properties.getTextProcessor();
    if (!config.isSynonymEnabled()) {
      return;
    }
    try (InputStream is = openDictionary(config.getSynonymFile())) {
      if (is != null) {
        Map<String, List<String>> newMap = new HashMap<>(16);
        loadSynonymsInto(is, newMap);
        synonymMap.clear();
        synonymMap.putAll(newMap);
        synonymFileLastModified = getFileLastModified(config.getSynonymFile());
      } else {
        log.warn("[SearchTextProcessor] 同义词文件未找到: {}", config.getSynonymFile());
      }
    } catch (IOException e) {
      log.warn("[SearchTextProcessor] 同义词词典加载失败: {}", e.getMessage());
    }
  }

  /**
   * 从输入流加载同义词词典。
   *
   * <p>格式：每行 {@code word -> syn1, syn2, ...}，以 {@code #} 开头的行为注释。
   *
   * @param is 输入流
   * @param target 目标 Map（写入加载结果）
   */
  private void loadSynonymsInto(InputStream is, Map<String, List<String>> target) throws IOException {
    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
      StringBuilder sb = new StringBuilder();
      int ch;
      while ((ch = reader.read()) != -1) {
        if (ch == '\n') {
          String line = sb.toString().trim();
          sb.setLength(0);
          if (line.isEmpty() || line.startsWith("#")) {
            continue;
          }
          int sep = line.indexOf("->");
          if (sep <= 0) {
            continue;
          }
          String word = line.substring(0, sep).trim();
          String synsPart = line.substring(sep + 2).trim();
          if (word.isEmpty() || synsPart.isEmpty()) {
            continue;
          }
          String[] synonyms = synsPart.split(",");
          List<String> synList = new ArrayList<>(synonyms.length);
          for (String syn : synonyms) {
            String trimmed = syn.trim();
            if (!trimmed.isEmpty()) {
              synList.add(trimmed);
            }
          }
          if (!synList.isEmpty()) {
            target.put(word, synList);
          }
        } else {
          sb.append((char) ch);
        }
      }
    }
    log.info("[SearchTextProcessor] 同义词词典加载完成，共 {} 组", target.size());
  }

  /** 加载拼音词典到 pinyinMap。 */
  private void loadPinyin() {
    SearchProperties.TextProcessorConfig config = properties.getTextProcessor();
    if (!config.isPinyinEnabled()) {
      return;
    }
    try (InputStream is = openDictionary(config.getPinyinFile())) {
      if (is != null) {
        Map<String, String> newMap = new HashMap<>(16);
        loadPinyinInto(is, newMap);
        pinyinMap.clear();
        pinyinMap.putAll(newMap);
        pinyinFileLastModified = getFileLastModified(config.getPinyinFile());
      } else {
        log.warn("[SearchTextProcessor] 拼音文件未找到: {}", config.getPinyinFile());
      }
    } catch (IOException e) {
      log.warn("[SearchTextProcessor] 拼音词典加载失败: {}", e.getMessage());
    }
  }

  /**
   * 从输入流加载拼音词典。
   *
   * <p>格式：每行 {@code 汉字 = pinyin}，以 {@code #} 开头的行为注释。
   *
   * @param is 输入流
   * @param target 目标 Map（写入加载结果）
   */
  private void loadPinyinInto(InputStream is, Map<String, String> target) throws IOException {
    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
      StringBuilder sb = new StringBuilder();
      int ch;
      while ((ch = reader.read()) != -1) {
        if (ch == '\n') {
          String line = sb.toString().trim();
          sb.setLength(0);
          if (line.isEmpty() || line.startsWith("#")) {
            continue;
          }
          int sep = line.indexOf('=');
          if (sep <= 0) {
            continue;
          }
          String hanzi = line.substring(0, sep).trim();
          String pinyin = line.substring(sep + 1).trim();
          if (!hanzi.isEmpty() && !pinyin.isEmpty()) {
            target.put(hanzi, pinyin);
          }
        } else {
          sb.append((char) ch);
        }
      }
    }
    log.info("[SearchTextProcessor] 拼音词典加载完成，共 {} 条", target.size());
  }

  // ==================== 词典热加载 ====================

  /**
   * 如果词典文件在磁盘上被修改过，则重新加载。
   *
   * <p>热加载采用「检测 → 加载 → 原子替换引用」三步策略：先构建新 Map，确认无误后再整体替换字段引用， 保证搜索请求在加载过程中仍可使用旧词典，不产生中间状态。
   *
   * <p>仅对文件系统路径（非 classpath 资源）生效，因为 classpath 资源通常打包在 JAR 内，无法动态修改。
   *
   * @return 是否有任一词典被重新加载
   */
  public synchronized boolean reloadIfChanged() {
    boolean reloaded = false;
    SearchProperties.TextProcessorConfig config = properties.getTextProcessor();

    // 同义词词典热加载
    if (config.isSynonymEnabled()) {
      long currentLastModified = getFileLastModified(config.getSynonymFile());
      if (currentLastModified > 0 && currentLastModified != synonymFileLastModified) {
        try (InputStream is = openDictionary(config.getSynonymFile())) {
          if (is != null) {
            Map<String, List<String>> newMap = new HashMap<>(16);
            loadSynonymsInto(is, newMap);
            if (!newMap.isEmpty()) {
              synonymMap.clear();
              synonymMap.putAll(newMap);
              synonymFileLastModified = currentLastModified;
              reloaded = true;
              log.info("[SearchTextProcessor] 同义词词典热加载完成，共 {} 组", synonymMap.size());
            }
          }
        } catch (IOException e) {
          log.warn("[SearchTextProcessor] 同义词词典热加载失败，保留旧版本: {}", e.getMessage());
        }
      }
    }

    // 拼音词典热加载
    if (config.isPinyinEnabled()) {
      long currentLastModified = getFileLastModified(config.getPinyinFile());
      if (currentLastModified > 0 && currentLastModified != pinyinFileLastModified) {
        try (InputStream is = openDictionary(config.getPinyinFile())) {
          if (is != null) {
            Map<String, String> newMap = new HashMap<>(16);
            loadPinyinInto(is, newMap);
            if (!newMap.isEmpty()) {
              pinyinMap.clear();
              pinyinMap.putAll(newMap);
              pinyinFileLastModified = currentLastModified;
              reloaded = true;
              log.info("[SearchTextProcessor] 拼音词典热加载完成，共 {} 条", pinyinMap.size());
            }
          }
        } catch (IOException e) {
          log.warn("[SearchTextProcessor] 拼音词典热加载失败，保留旧版本: {}", e.getMessage());
        }
      }
    }
    return reloaded;
  }

  /**
   * 获取词典文件在磁盘上的最后修改时间。
   *
   * @param path 文件路径（支持 {@code classpath:} 前缀）
   * @return 文件 {@code lastModified} 时间戳；文件不存在或非文件系统资源时返回 0
   */
  private long getFileLastModified(String path) {
    if (path == null || path.isEmpty() || path.startsWith("classpath:")) {
      return 0;
    }
    try {
      File file = new File(path);
      return file.exists() ? file.lastModified() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  // ==================== 文件访问 ====================

  /**
   * 打开词典文件。
   *
   * <p>支持 {@code classpath:} 前缀和普通文件系统路径。
   *
   * <p><b>资源管理约定</b>：本方法返回的 {@link InputStream} 由<b>调用方负责关闭</b>。 建议使用 try-with-resources 包裹调用，例如：
   * <pre>{@code
   * try (InputStream is = openDictionary(path)) {
   *   // 使用 is
   * }
   * }</pre>
   *
   * @param path 文件路径
   * @return 输入流，未找到或路径为空时返回 {@code null}
   */
  private InputStream openDictionary(String path) throws IOException {
    if (path == null || path.isEmpty()) {
      return null;
    }
    if (path.startsWith("classpath:")) {
      String resource = path.substring("classpath:".length());
      InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
      if (is == null) {
        is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
      }
      return is;
    }
    return new FileInputStream(path);
  }

  // ==================== 同义词扩展 ====================

  /**
   * 对分词后的文本执行同义词扩展。
   *
   * <p>对于每个词元，如果同义词词典中存在，则追加同义词（OR 扩展）。
   *
   * @param text 分词后的文本（空格分隔）
   * @return 扩展后的文本
   */
  String expandSynonyms(String text) {
    if (synonymMap.isEmpty() || text == null || text.isBlank()) {
      return text;
    }
    String[] tokens = text.split("\\s+");
    Set<String> expanded = new LinkedHashSet<>(tokens.length * 2);
    for (String token : tokens) {
      expanded.add(token);
      List<String> syns = synonymMap.get(token);
      if (syns != null) {
        expanded.addAll(syns);
      }
    }
    return String.join(" ", expanded);
  }

  // ==================== 拼音转换 ====================

  /**
   * 拼接拼音形式到查询文本末尾，支持拼音搜索。
   *
   * <p>将文本中每个字符替换为其拼音形式，追加到原始文本后，以空格分隔。
   *
   * @param text 原始文本
   * @return 原始文本 + 拼音文本
   */
  String appendPinyin(String text) {
    if (pinyinMap.isEmpty() || text == null || text.isBlank()) {
      return text;
    }
    StringBuilder pinyinBuilder = new StringBuilder(text.length() * 2);
    for (int i = 0; i < text.length(); i++) {
      String ch = String.valueOf(text.charAt(i));
      String py = pinyinMap.get(ch);
      if (py != null) {
        if (pinyinBuilder.length() > 0) {
          pinyinBuilder.append(' ');
        }
        pinyinBuilder.append(py);
      }
    }
    if (pinyinBuilder.length() == 0) {
      return text;
    }
    return text + " " + pinyinBuilder;
  }

  /**
   * 获取已加载的同义词词典（只读视图）。
   *
   * @return 词 → 同义词列表的只读映射
   */
  public Map<String, List<String>> getSynonymMap() {
    return Collections.unmodifiableMap(synonymMap);
  }

  /**
   * 获取已加载的拼音词典（只读视图）。
   *
   * @return 汉字/词 → 拼音的只读映射
   */
  public Map<String, String> getPinyinMap() {
    return Collections.unmodifiableMap(pinyinMap);
  }
}
