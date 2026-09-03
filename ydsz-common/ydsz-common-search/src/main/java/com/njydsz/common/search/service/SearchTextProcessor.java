package com.njydsz.common.search.service;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 *   <li>StopWordFilter — 基于内置停用词表过滤无意义词
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