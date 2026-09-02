package com.njydsz.literule.server.expression;

import java.util.List;

/**
 * 空变量注册表
 *
 * <p>默认实现，不注册任何变量定义。当应用未配置 {@link VariableRegistry} Bean 时使用， 确保向后兼容：{@link
 * ExpressionValidationService} 仍可工作，但不会触发 UNDEFINED_VARIABLE 校验。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class EmptyVariableRegistry implements VariableRegistry {

  @Override
  public VariableDefinition lookup(String name) {
    return null;
  }

  @Override
  public List<VariableDefinition> listAll() {
    return List.of();
  }

  @Override
  public boolean isEmpty() {
    return true;
  }
}
