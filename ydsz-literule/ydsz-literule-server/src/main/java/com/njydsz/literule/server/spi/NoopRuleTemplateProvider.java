package com.njydsz.literule.server.spi;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;

/**
 * RuleTemplateProvider 的默认 NOOP 实现。
 *
 * <p>当消费方未提供自定义实现时，使用此空实现避免启动时 Bean 缺失。
 * 所有方法返回空列表或不执行任何操作。
 *
 * @since 26.10.01
 */
@Component
public class NoopRuleTemplateProvider implements RuleTemplateProvider {

  @Override
  public List<RuleTemplateVO> listAll() {
    return Collections.emptyList();
  }

  @Override
  public List<RuleTemplateVO> listByCategory(String category) {
    return Collections.emptyList();
  }

  @Override
  public List<RuleTemplateVO> listByIndustry(String industry) {
    return Collections.emptyList();
  }

  @Override
  public RuleDefinitionDTO importTemplate(String templateCode, String operator) {
    return null;
  }
}
