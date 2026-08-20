package com.njydsz.userinfo.server.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.config.SocialAuthProperties;
import com.njydsz.userinfo.domain.dto.SocialClientCreateDTO;
import com.njydsz.userinfo.domain.dto.SocialClientUpdateDTO;
import com.njydsz.userinfo.domain.query.SocialClientPageQuery;
import com.njydsz.userinfo.domain.repository.SocialClientRepository;
import com.njydsz.userinfo.domain.vo.SocialClientVO;

/**
 * 社交平台客户端配置服务（P1-1 DB+YAML 混合配置）。
 *
 * <p>实现社交平台 OAuth2 客户端配置的运行时热更新：
 *
 * <ul>
 *   <li>配置优先级：数据库 ＞ application.yml</li>
 *   <li>数据库有配置时覆盖 YAML 同名平台</li>
 *   <li>数据库无配置时回落 YAML</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 热更新场景：管理员新增 GitHub 平台配置
 * SocialClientCreateDTO dto = new SocialClientCreateDTO();
 * dto.setPlatform("GITHUB");
 * dto.setClientId("gh_xxx");
 * dto.setClientSecret("xxx");
 * service.create(dto);  // 立即生效，无需重启
 *
 * // 获取配置时自动合并
 * SocialAuthProperties.ProviderConfig config = service.getProviderConfig("GITHUB");
 * }</pre>
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialClientConfigService {

  private final SocialClientRepository socialClientRepository;
  private final SocialAuthProperties socialAuthProperties;

  /**
   * 分页查询社交平台客户端配置列表。
   *
   * @param query 分页查询参数
   * @return 配置 VO 列表
   */
  public List<SocialClientVO> findByPage(SocialClientPageQuery query) {
    return socialClientRepository.findByPage(query);
  }

  /**
   * 查询所有已启用的平台配置（DB + YAML 合并）。
   *
   * @return 已启用的配置列表
   */
  public List<SocialClientVO> findEnabledWithFallback() {
    // 1. 优先从数据库加载已启用配置
    List<SocialClientVO> dbClients = socialClientRepository.findEnabled();

    // 2. 合并 YAML 中配置但 DB 中不存在的平台
    List<SocialClientVO> merged = new java.util.ArrayList<>(dbClients);
    Map<String, SocialAuthProperties.ProviderConfig> yamlProviders = socialAuthProperties.getProviders();
    if (yamlProviders != null) {
      for (Map.Entry<String, SocialAuthProperties.ProviderConfig> entry : yamlProviders.entrySet()) {
        String platformKey = entry.getKey().toUpperCase();
        boolean existsInDb = dbClients.stream()
            .anyMatch(vo -> platformKey.equals(vo.getPlatform()));
        if (!existsInDb) {
          // YAML 中存在但 DB 不存在 → 构建 VO 返回
          SocialClientVO vo = new SocialClientVO();
          vo.setPlatform(platformKey);
          vo.setPlatformName(capitalize(entry.getKey()));
          vo.setAppId(entry.getValue().getAppId());
          vo.setScope(entry.getValue().getScope());
          vo.setRedirectUri(entry.getValue().getRedirectUri());
          vo.setStatus("ENABLED");
          vo.setSortOrder(999); // YAML 配置排在 DB 配置后面
          vo.setRemark("YAML 静态配置");
          merged.add(vo);
        }
      }
    }

    return merged;
  }

  /**
   * 根据平台标识获取合并后的 Provider 配置（DB 优先）。
   *
   * <p>运行时热更新：DB 配置变更后立即生效，无需重启。
   *
   * @param platform 平台标识
   * @return 合并后的配置
   */
  public Optional<SocialAuthProperties.ProviderConfig> getProviderConfig(String platform) {
    if (platform == null || platform.isBlank()) {
      return Optional.empty();
    }

    // 1. 优先从数据库获取
    Optional<SocialClientVO> dbClient = socialClientRepository.findByPlatform(platform.toUpperCase());
    if (dbClient.isPresent() && "ENABLED".equals(dbClient.get().getStatus())) {
      SocialClientVO vo = dbClient.get();
      SocialAuthProperties.ProviderConfig config = new SocialAuthProperties.ProviderConfig();
      config.setAppId(vo.getAppId());
      config.setAppSecret(null); // DB 存储的是 BCrypt 哈希，不返回明文
      config.setScope(vo.getScope());
      config.setRedirectUri(vo.getRedirectUri());
      return Optional.of(config);
    }

    // 2. 回落到 YAML 配置
    SocialAuthProperties.ProviderConfig yamlConfig = socialAuthProperties.getProvider(platform);
    return Optional.ofNullable(yamlConfig);
  }

  /**
   * 创建社交平台客户端配置（立即生效，无需重启）。
   *
   * @param dto 创建 DTO
   */
  public void create(SocialClientCreateDTO dto) {
    socialClientRepository.save(dto);
    log.info("社交平台客户端配置已创建并生效: platform={}", dto.getPlatform());
  }

  /**
   * 更新社交平台客户端配置（立即生效）。
   *
   * @param platform 平台标识
   * @param dto 更新 DTO
   */
  public void update(String platform, SocialClientUpdateDTO dto) {
    socialClientRepository.update(platform, dto);
    log.info("社交平台客户端配置已更新: platform={}", platform);
  }

  /**
   * 删除社交平台客户端配置。
   *
   * @param platform 平台标识
   */
  public void delete(String platform) {
    socialClientRepository.deleteByPlatform(platform);
    log.info("社交平台客户端配置已删除: platform={}", platform);
  }

  /**
   * 获取合并后的配置 Map（供 SocialAuthProviderRegistry 使用）。
   *
   * @return 平台标识 → 配置映射
   */
  public Map<String, SocialAuthProperties.ProviderConfig> getMergedProviderConfigs() {
    Map<String, SocialAuthProperties.ProviderConfig> merged = new HashMap<>();

    // 1. 先加载 YAML 配置
    if (socialAuthProperties.getProviders() != null) {
      socialAuthProperties.getProviders().forEach((key, value) ->
          merged.put(key.toUpperCase(), value));
    }

    // 2. DB 配置覆盖 YAML（DB 优先）
    List<SocialClientVO> dbClients = socialClientRepository.findEnabled();
    for (SocialClientVO vo : dbClients) {
      SocialAuthProperties.ProviderConfig config = new SocialAuthProperties.ProviderConfig();
      config.setAppId(vo.getAppId());
      config.setScope(vo.getScope());
      config.setRedirectUri(vo.getRedirectUri());
      merged.put(vo.getPlatform(), config);
    }

    return merged;
  }

  /**
   * 首字母大写。
   */
  private String capitalize(String str) {
    if (str == null || str.isBlank()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }
}
