package com.njydsz.system.server.service.impl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.vo.FrontendInitVO;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.server.service.FrontendInitService;




/**
 * 前端初始化服务实现
 *
 * <p>聚合前端初始化所需数据，减少前端启动时的请求次数。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrontendInitServiceImpl implements FrontendInitService {

  /** 系统配置服务 */
  private final ConfigService configService;

  /** 字典项服务 */
  private final DictItemService dictItemService;

  /** 系统配置属性 */
  private final SystemProperties properties;

  /** 默认初始化的字典类型 */
  private static final List<String> DEFAULT_DICT_TYPES = List.of(
      "user_status", "gender", "priority", "task_status");

  @Override
  public FrontendInitVO getInitData() {
    return getInitDataWithDicts(DEFAULT_DICT_TYPES);
  }

  @Override
  public FrontendInitVO getInitDataWithDicts(List<String> typeCodes) {
    // 1. 获取公开配置
    Map<String, String> publicConfigs = getPublicConfigMap();

    // 2. 获取指定字典数据
    Map<String, List<DictItemVO>> dictMap = getDictMap(typeCodes);

    // 3. 构建响应
    return FrontendInitVO.builder()
        .publicConfigs(publicConfigs)
        .dictMap(dictMap)
        .systemVersion(properties.getVersion())
        .tenantId(TenantContextHolder.getTenantId())
        .userId(getCurrentUserId())
        .build();
  }

  /**
   * 获取公开配置 Map
   *
   * @return 配置键 -> 配置值
   */
  private Map<String, String> getPublicConfigMap() {
    try {
      List<ConfigVO> publicConfigs = configService.listPublicConfigs();
      return publicConfigs.stream()
          .filter(vo -> vo.getConfigKey() != null && vo.getConfigValue() != null)
          .collect(Collectors.toMap(
              ConfigVO::getConfigKey,
              ConfigVO::getConfigValue,
              (v1, v2) -> v1 // 重复键取第一个
          ));
    } catch (Exception e) {
      log.warn("[FrontendInitService] 获取公开配置失败: {}", e.getMessage(), e);
      return new HashMap<>(0);
    }
  }

  /**
   * 获取字典数据 Map
   *
   * @param typeCodes 字典类型编码列表
   * @return typeCode -> 字典项列表
   */
  private Map<String, List<DictItemVO>> getDictMap(List<String> typeCodes) {
    Map<String, List<DictItemVO>> dictMap = new HashMap<>(typeCodes.size());
    if (typeCodes == null || typeCodes.isEmpty()) {
      return dictMap;
    }
    for (String typeCode : typeCodes) {
      try {
        List<DictItemVO> items = dictItemService.listEnabledByTypeCode(typeCode);
        if (items != null && !items.isEmpty()) {
          dictMap.put(typeCode, items);
        }
      } catch (Exception e) {
        log.warn("[FrontendInitService] 获取字典失败: typeCode={}, error={}", typeCode, e.getMessage(), e);
      }
    }
    return dictMap;
  }

  /**
   * 获取当前用户 ID
   *
   * @return 当前用户 ID，未登录时返回 null
   */
  private String getCurrentUserId() {
    try {
      return RequestContext.getUserId();
    } catch (Exception e) {
      log.warn("[FrontendInit] 获取当前用户ID失败，将以匿名用户身份初始化", e);
      return null;
    }
  }
}
