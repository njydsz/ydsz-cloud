package com.njydsz.literule.server.spi;

import java.util.List;

import com.njydsz.literule.domain.dto.DecisionTableDefinitionDTO;

/**
 * 决策表配置提供者接口（SPI）
 *
 * <p>由消费方（如 execution 模块）提供实现，从数据库加载决策表定义。 literule 模块本身不依赖持久层。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface DecisionTableConfigProvider {

  /**
   * 加载全部启用的决策表
   *
   * @return 启用的决策表列表
   */
  List<DecisionTableDefinitionDTO> loadEnabledTables();

  /**
   * 加载全部决策表（含禁用）
   *
   * @return 全部决策表列表
   */
  List<DecisionTableDefinitionDTO> loadAllTables();

  /**
   * 保存决策表
   *
   * @param definition 决策表定义
   * @param operator 操作人
   * @return 保存后的定义（含版本号）
   */
  DecisionTableDefinitionDTO save(DecisionTableDefinitionDTO definition, String operator);

  /**
   * 切换启停
   *
   * @param tableCode 表编码
   * @param enabled 是否启用
   * @param operator 操作人
   */
  void toggleEnabled(String tableCode, boolean enabled, String operator);

  /**
   * 根据编码查询
   *
   * @param tableCode 表编码
   * @return 决策表定义；不存在返回 null
   */
  DecisionTableDefinitionDTO findByCode(String tableCode);

  /**
   * 删除决策表
   *
   * @param tableCode 表编码
   * @param operator 操作人
   */
  void delete(String tableCode, String operator);
}
