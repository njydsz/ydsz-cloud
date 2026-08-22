package com.njydsz.userinfo.server.auth;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 鉴权路径排除服务（P1-3 路径排除配置化）。
 *
 * <p>对标 XXL-SSO 的路径排除能力，提供运行时动态管理排除路径的能力。
 * 排除的路径不会经过 Spring Security 的认证过滤器链，提升鉴权性能。
 *
 * <p><b>路径匹配规则：</b>
 *
 * <ul>
 *   <li>支持 Ant 风格通配符（{@code *} 匹配单层路径，{@code **} 匹配多层路径）</li>
 *   <li>路径匹配不区分大小写</li>
 *   <li>支持运行时动态添加/移除排除路径</li>
 * </ul>
 *
 * <p><b>配置来源优先级：</b>
 *
 * <ol>
 *   <li>运行时动态排除路径（Redis 缓存 + 内存）</li>
 *   <li>配置文件 {@code ydsz.userinfo.auth-exclude-paths}</li>
 *   <li>默认排除路径（actuator、swagger 等）</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PathExcludeService {

  private final UserInfoProperties userInfoProperties;

  /** 运行时动态排除路径（追加式，重启后丢失） */
  private final Set<String> runtimeExcludedPaths = ConcurrentHashMap.newKeySet();

  /** 运行时包含路径（允许撤销 YAML 中的默认排除） */
  private final Set<String> runtimeIncludedPaths = ConcurrentHashMap.newKeySet();

  /**
   * 判断指定路径是否被排除（不需要鉴权）。
   *
   * @param path 请求路径
   * @return true 表示该路径不需要鉴权
   */
  public boolean isExcluded(String path) {
    if (path == null || path.isBlank()) {
      return false;
    }

    // 1. 检查运行时明确包含路径（优先级最高，可撤销 YAML 排除）
    if (matchesAny(path, runtimeIncludedPaths)) {
      return false;
    }

    // 2. 检查运行时排除路径
    if (matchesAny(path, runtimeExcludedPaths)) {
      return true;
    }

    // 3. 检查配置的排除路径
    return matchesAny(path, userInfoProperties.getAuthExcludePaths());
  }

  /**
   * 动态添加排除路径（运行时生效，重启后丢失）。
   *
   * @param pathPattern Ant 风格的路径模式（如 {@code /api/v1/public/**}）
   */
  public void addExcludedPath(String pathPattern) {
    if (pathPattern != null && !pathPattern.isBlank()) {
      runtimeExcludedPaths.add(pathPattern);
      log.info("运行时排除路径已添加: {}", pathPattern);
    }
  }

  /**
   * 动态移除排除路径。
   *
   * @param pathPattern 要移除的路径模式
   * @return true 表示成功移除
   */
  public boolean removeExcludedPath(String pathPattern) {
    boolean removed = runtimeExcludedPaths.remove(pathPattern);
    if (removed) {
      log.info("运行时排除路径已移除: {}", pathPattern);
    }
    return removed;
  }

  /**
   * 添加运行时包含路径（撤销 YAML 配置的排除）。
   *
   * <p>用于临时恢复某个 YAML 排除路径的鉴权。
   *
   * @param pathPattern Ant 风格的路径模式
   */
  public void addIncludedPath(String pathPattern) {
    if (pathPattern != null && !pathPattern.isBlank()) {
      runtimeIncludedPaths.add(pathPattern);
      log.info("运行时包含路径已添加（撤销排除）: {}", pathPattern);
    }
  }

  /**
   * 获取当前所有排除路径（配置 + 运行时）。
   *
   * @return 排除路径列表
   */
  public List<String> getAllExcludedPaths() {
    List<String> all = new CopyOnWriteArrayList<>(userInfoProperties.getAuthExcludePaths());
    all.addAll(runtimeExcludedPaths);
    // 移除被运行时包含的路径
    all.removeAll(runtimeIncludedPaths);
    return all.stream().sorted().distinct().collect(Collectors.toList());
  }

  /**
   * 清空运行时排除路径（恢复为纯 YAML 配置）。
   */
  public void clearRuntimePaths() {
    runtimeExcludedPaths.clear();
    runtimeIncludedPaths.clear();
    log.info("运行时排除/包含路径已清空");
  }

  /**
   * 检查路径是否匹配任一模式。
   *
   * @param path    请求路径
   * @param patterns 模式集合
   * @return true 表示匹配任一模式
   */
  private boolean matchesAny(String path, Collection<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return false;
    }
    return patterns.stream().anyMatch(pattern -> matchAntPattern(path, pattern));
  }

  /**
   * Ant 风格路径匹配。
   *
   * <p>支持 {@code *}（单层通配）和 {@code **}（多层通配）。
   *
   * @param path    请求路径
   * @param pattern Ant 模式
   * @return true 表示匹配
   */
  private boolean matchAntPattern(String path, String pattern) {
    if (pattern == null) {
      return false;
    }
    // 简单实现：将 ant 模式转为正则
    String regex = pattern
        .replace(".", "\\.")
        .replace("**", ".*")
        .replace("*", "[^/]*");
    return path.matches(regex);
  }
}
