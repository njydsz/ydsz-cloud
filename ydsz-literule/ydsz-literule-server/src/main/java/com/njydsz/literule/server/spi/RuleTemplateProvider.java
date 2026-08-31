package com.njydsz.literule.server.spi;

import java.util.List;

import com.njydsz.literule.domain.api.RuleDefinition;
import com.njydsz.literule.domain.vo.RuleTemplateVO;

/**
 * 规则模板提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，从规则模板市场（{@code ydsz_rule_template} 表） 加载预置模板，将原有 {@code
 * RuleTemplateService} 的能力抽象为 SPI，避免 literule 模块 直接依赖持久层实现。
 *
 * <p>literule 模块的 {@code RuleAdminController} 通过此接口反转依赖调用模板市场能力。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface RuleTemplateProvider {

  /**
   * 列出全部模板
   *
   * @return 模板列表（按优先级升序）
   */
  List<RuleTemplateVO> listAll();

  /**
   * 按类别查询模板
   *
   * @param category 模板类别（如 FINANCE / EVM / BENCH）
   * @return 模板列表
   */
  List<RuleTemplateVO> listByCategory(String category);

  /**
   * 按行业查询模板
   *
   * @param industry 行业编码
   * @return 模板列表
   */
  List<RuleTemplateVO> listByIndustry(String industry);

  /**
   * 导入模板为规则定义
   *
   * <p>根据模板编码查找模板，将其转换为 {@link RuleDefinition} 后保存为正式规则。 规则编码使用模板编码（{@code
   * templateCode}），若已存在同名规则则执行更新。
   *
   * @param templateCode 模板编码
   * @param operator 操作人
   * @return 保存后的规则定义（含版本号）
   */
  RuleDefinition importTemplate(String templateCode, String operator);
}
