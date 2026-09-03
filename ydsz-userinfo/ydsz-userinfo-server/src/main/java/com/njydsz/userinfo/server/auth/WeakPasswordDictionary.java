package com.njydsz.userinfo.server.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 弱密码字典加载器。
 *
 * <p>从 classpath 加载弱密码字典文件（{@code weak-passwords.txt}），提供 O(1) 时间复杂度的弱密码校验。
 *
 * <p>字典文件格式：
 *
 * <ul>
 *   <li>每行一个密码
 *   <li>以 {@code #} 开头的行为注释
 *   <li>空行自动忽略
 *   <li>校验时忽略大小写
 * </ul>
 *
 * <p>对标互联网大厂安全标准（美团/阿里/字节均要求弱密码字典校验）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class WeakPasswordDictionary {

  /** 集合初始容量 */
  private static final int CAPACITY = 16;


  /** 默认字典文件路径 */
  private static final String DEFAULT_DICTIONARY_PATH = "weak-passwords.txt";

  /** 弱密码集合（小写存储，用于忽略大小写校验） */
  private Set<String> weakPasswords = Collections.emptySet();

  /** 字典大小（用于日志） */
  private int dictionarySize = 0;

  /**
   * 初始化字典（从 classpath 加载）。
   *
   * <p>使用 {@link PostConstruct} 确保在 Spring 容器启动时加载字典，加载失败时记录警告但不阻塞启动。
   */
  @PostConstruct
  public void init() {
    loadDictionary(DEFAULT_DICTIONARY_PATH);
  }

  /**
   * 加载字典文件。
   *
   * @param path classpath 路径
   */
  private void loadDictionary(String path) {
    Set<String> loaded = new HashSet<>(CAPACITY);
    try {
      ClassPathResource resource = new ClassPathResource(path);
      if (!resource.exists()) {
        log.warn("弱密码字典文件不存在: {}, 弱密码校验将跳过", path);
        return;
      }
      try (InputStream is = resource.getInputStream();
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
            loaded.add(trimmed.toLowerCase());
          }
        }
      }
      weakPasswords = loaded;
      dictionarySize = loaded.size();
      log.info("弱密码字典加载完成: {} 条记录", dictionarySize);
    } catch (IOException e) {
      log.warn("加载弱密码字典失败: {}, 弱密码校验将跳过", e.getMessage());
    }
  }

  /**
   * 检查密码是否在弱密码字典中。
   *
   * @param password 待检查密码
   * @return true 如果密码在字典中
   */
  public boolean isWeakPassword(String password) {
    if (password == null || password.isBlank()) {
      return false;
    }
    return weakPasswords.contains(password.toLowerCase());
  }

  /**
   * 获取字典大小。
   *
   * @return 字典中弱密码数量
   */
  public int size() {
    return dictionarySize;
  }
}
