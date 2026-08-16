package com.njydsz.literule.server.spi;

import com.njydsz.literule.api.ScorecardDefinition;
import java.util.List;

/**
 * 评分卡配置提供者接口（SPI）
 *
 * <p>由消费方提供实现，从数据库加载评分卡定义。 literule 模块本身不依赖持久层。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface ScorecardConfigProvider {

  /**
   * 加载全部启用的评分卡
   *
   * @return 启用的评分卡列表
   */
  List<ScorecardDefinition> loadEnabledScorecards();

  /**
   * 加载全部评分卡（含禁用）
   *
   * @return 全部评分卡列表
   */
  List<ScorecardDefinition> loadAllScorecards();

  /**
   * 保存评分卡
   *
   * @param definition 评分卡定义
   * @param operator 操作人
   * @return 保存后的定义（含版本号）
   */
  ScorecardDefinition save(ScorecardDefinition definition, String operator);

  /**
   * 切换启停
   *
   * @param ruleCode 规则编码
   * @param enabled 是否启用
   * @param operator 操作人
   */
  void toggleEnabled(String ruleCode, boolean enabled, String operator);

  /**
   * 根据编码查询
   *
   * @param ruleCode 规则编码
   * @return 评分卡定义；不存在返回 null
   */
  ScorecardDefinition findByCode(String ruleCode);

  /**
   * 删除评分卡
   *
   * @param ruleCode 规则编码
   * @param operator 操作人
   */
  void delete(String ruleCode, String operator);
}
