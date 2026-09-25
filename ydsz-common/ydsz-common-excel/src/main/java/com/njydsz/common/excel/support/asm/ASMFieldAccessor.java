package com.njydsz.common.excel.support.asm;

import java.lang.reflect.Field;

import com.njydsz.common.excel.support.mh.MHFieldAccessor;

/**
 * 字段访问器 - 兼容性保留类。
 *
 * <p>本类已迁移至 {@link com.njydsz.common.excel.support.mh.MHFieldAccessor}（MH = MethodHandle），
 * 仅保留作为向后兼容的代理层，不要直接使用本类。
 *
 * <p><b>命名澄清</b>：原 {@code ASM} 前缀为历史遗留——本类从不生成或加载字节码，
 * 实际基于 {@code java.lang.invoke.MethodHandle} 实现。
 *
 * <h3>迁移指南</h3>
 * <pre>{@code
 * // 旧写法（已废弃）
 * import com.njydsz.common.excel.support.mh.MHFieldAccessor;
 * MHFieldAccessor.FieldGetter g = MHFieldAccessor.getGetter(clazz, field);
 *
 * // 新写法
 * import com.njydsz.common.excel.support.mh.MHFieldAccessor;
 * MHFieldAccessor.FieldGetter g = MHFieldAccessor.getGetter(clazz, field);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 请使用 {@link com.njydsz.common.excel.support.mh.MHFieldAccessor} 替代
 * @see com.njydsz.common.excel.support.mh.MHFieldAccessor
 */
@Deprecated
public class MHFieldAccessor {

  private MHFieldAccessor() {}

  /**
   * @deprecated 请使用 {@link MHFieldAccessor.FieldGetter} 替代
   */
  @Deprecated
  public interface FieldGetter extends MHFieldAccessor.FieldGetter {}

  /**
   * @deprecated 请使用 {@link MHFieldAccessor.FieldSetter} 替代
   */
  @Deprecated
  public interface FieldSetter extends MHFieldAccessor.FieldSetter {}

  /**
   * @deprecated 请使用 {@link MHFieldAccessor.ObjectInstantiator} 替代
   */
  @Deprecated
  public interface ObjectInstantiator extends MHFieldAccessor.ObjectInstantiator {}

  /**
   * @deprecated 请使用 {@link MHFieldAccessor#getGetter(Class, Field)} 替代
   */
  @Deprecated
  public static MHFieldAccessor.FieldGetter getGetter(Class<?> clazz, Field field) {
    return MHFieldAccessor.getGetter(clazz, field);
  }

  /**
   * @deprecated 请使用 {@link MHFieldAccessor#getSetter(Class, Field)} 替代
   */
  @Deprecated
  public static MHFieldAccessor.FieldSetter getSetter(Class<?> clazz, Field field) {
    return MHFieldAccessor.getSetter(clazz, field);
  }

  /**
   * @deprecated 请使用 {@link MHFieldAccessor#getInstantiator(Class)} 替代
   */
  @Deprecated
  public static MHFieldAccessor.ObjectInstantiator getInstantiator(Class<?> clazz) {
    return MHFieldAccessor.getInstantiator(clazz);
  }

  /**
   * @deprecated 请使用 {@link MHFieldAccessor#clearCache()} 替代
   */
  @Deprecated
  public static void clearCache() {
    MHFieldAccessor.clearCache();
  }

  /**
   * @deprecated 请使用 {@link MHFieldAccessor#getActiveAccessorCount()} 替代
   */
  @Deprecated
  public static int getActiveAccessorCount() {
    return MHFieldAccessor.getActiveAccessorCount();
  }

  /**
   * @deprecated 请使用 {@link MHFieldAccessor#getActiveSetterCount()} 替代
   */
  @Deprecated
  public static int getActiveSetterCount() {
    return MHFieldAccessor.getActiveSetterCount();
  }

  /**
   * @deprecated 请使用 {@link MHFieldAccessor#getActiveInstantiatorCount()} 替代
   */
  @Deprecated
  public static int getActiveInstantiatorCount() {
    return MHFieldAccessor.getActiveInstantiatorCount();
  }
}
