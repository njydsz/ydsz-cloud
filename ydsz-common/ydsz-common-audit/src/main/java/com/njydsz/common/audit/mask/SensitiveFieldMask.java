package com.njydsz.common.audit.mask;

import java.util.Set;

import com.njydsz.common.util.mask.MaskUtils;

/**
 * 敏感字段脱敏工具类（审计模块入口）
 *
 * <p>对 JSON 字符串按字段名匹配脱敏。能力已沉淀至 {@link MaskUtils#maskJson(String, Set)}，
 * 本类作为审计模块的向后兼容入口保留，切面可直接调用 {@link MaskUtils} 获取同等功能。
 *
 * <p><b>脱敏规则：</b>
 *
 * <ul>
 *   <li>字符串：根据字段名类型（手机号/邮箱/身份证/银行卡）选择特定策略，否则保留前后 2 位
 *   <li>List/Map：递归处理每个元素
 * </ul>
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>类为 final，构造器私有，禁止实例化
 *   <li>解析失败时降级返回原 JSON，保证审计主流程不受影响
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 26.10.01 能力已沉淀至 {@link MaskUtils#maskJson(String, Set)}，推荐直接使用
 */
@Deprecated
public final class SensitiveFieldMask {

  private SensitiveFieldMask() {
    throw new UnsupportedOperationException("SensitiveFieldMask is a utility class");
  }

  /**
   * 对 JSON 字符串中的敏感字段进行脱敏处理
   *
   * <p>委托 {@link MaskUtils#maskJson(String, Set)} 执行实际脱敏逻辑。
   *
   * @param json JSON 字符串
   * @param patterns 额外敏感字段名称集合（与默认模式合并生效）
   * @return 脱敏后的 JSON 字符串；解析失败时返回原 JSON
   */
  public static String maskJson(String json, Set<String> patterns) {
    return MaskUtils.maskJson(json, patterns);
  }
}
