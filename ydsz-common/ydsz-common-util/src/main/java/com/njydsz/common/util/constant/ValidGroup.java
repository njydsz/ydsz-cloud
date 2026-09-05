package com.njydsz.common.util.constant;

/**
 * Bean Validation 校验分组（JSR-303 groups 标记接口集合）。
 *
 * <p>统一管理系统中所有校验分组，避免各模块分散定义同名分组接口导致校验语义割裂。
 *
 * <p>典型用法：
 *
 * <pre>{@code
 * @NotBlank(groups = ValidGroup.Create.class, message = "创建时不能为空")
 * @NotBlank(groups = ValidGroup.Update.class, message = "更新时不能为空")
 * private String name;
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public final class ValidGroup {

  /** 私有构造器，禁止实例化。 */
  private ValidGroup() {
    throw new UnsupportedOperationException(
        "ValidGroup is a marker interface container and cannot be instantiated");
  }

  /**
   * 新增分组（create 场景触发）。
   *
   * <p>用于 INSERT 时必须填写字段的校验。
   */
  public interface Create {
    // 标记接口
  }

  /**
   * 更新分组（update 场景触发）。
   *
   * <p>用于 UPDATE 时必须填写字段的校验（如主键必填）。
   */
  public interface Update {
    // 标记接口
  }

  /**
   * 默认分组（与 {@code javax.validation.groups.Default} 语义等价）。
   *
   * <p>用于无分组标注时的兜底校验。
   */
  public interface Default {
    // 标记接口
  }
}
