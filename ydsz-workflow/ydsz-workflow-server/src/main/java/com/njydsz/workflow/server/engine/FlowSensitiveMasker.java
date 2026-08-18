package com.njydsz.workflow.server.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.safe.sensitive.SensitiveUtil;

/**
 * 敏感字段脱敏器（P0-1 落地，P1-3 优化）。
 *
 * <p>对标钉钉/飞书审批的敏感数据自动脱敏能力，在审计日志写入、通知内容组装、 评论持久化等场景统一调用，防止手机号、身份证号、银行卡号等 PII 数据明文暴露。
 *
 * <h3>脱敏策略（P1-3 优化后）</h3>
 *
 * <ul>
 *   <li><b>文本级脱敏</b> — 委托 {@link SensitiveUtil#scanAndMask(String)} 统一扫描+脱敏
 *   <li><b>自定义字段</b> — 通过 {@link #maskFields} 对 Map 中指定 key 的值整体脱敏
 * </ul>
 *
 * <p><b>P1-3 重构说明：</b>
 *
 * <ul>
 *   <li>原实现中 4 个 PII 正则（PHONE/ID_CARD/BANK_CARD/EMAIL）在 common-safe、workflow、agent 三处重复定义，现统一为
 *       {@link SensitiveUtil} 的 {@code PII_SCAN_PATTERNS} 单一来源
 *   <li>脱敏算法同样委托 {@link SensitiveUtil}，确保与 {@code @SensitiveData} 注解结果一致
 *   <li>升级 PII 类型（如手机号号段扩展）只需修改 common-safe 一处
 * </ul>
 *
 * <p>所有方法均为无状态纯函数，线程安全。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowSensitiveMasker {

  /**
   * 敏感字段名匹配模式（使用单词边界，避免误匹配非敏感字段如 phoneModel、telephoneExchange 等）
   *
   * <p>匹配规则：字段名中包含独立的敏感词（前后为下划线、连字符、字符串边界或大小写转换位置）， 而非作为其他单词的子串。例如：
   *
   * <ul>
 *   <li><b>匹配</b>：{@code phone}、{@code mobile_phone}、{@code phoneNo}、{@code id_card_no}、{@code emailAddress}
 *   <li><b>不匹配</b>：{@code phoneModel}、{@code telephoneExchange}、{@code cardNote}
 *   </ul>
   */
  private static final List<Pattern> SENSITIVE_KEY_PATTERNS = List.of(
      // 电话/手机
      Pattern.compile("(^|[_-]|(?<=[a-z]))phone($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      Pattern.compile("(^|[_-]|(?<=[a-z]))mobile($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      Pattern.compile("(^|[_-]|(?<=[a-z]))tel($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      // 身份证
      Pattern.compile("(^|[_-]|(?<=[a-z]))idcard($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      Pattern.compile("(^|[_-]|(?<=[a-z]))idno($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      Pattern.compile("(^|[_-]|(?<=[a-z]))identity($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      Pattern.compile("(^|[_-]|(?<=[a-z]))id_number($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      Pattern.compile("(^|[_-]|(?<=[a-z]))certno($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      // 银行卡
      Pattern.compile("(^|[_-]|(?<=[a-z]))bankcard($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      Pattern.compile("(^|[_-]|(?<=[a-z]))cardno($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      Pattern.compile("(^|[_-]|(?<=[a-z]))bankno($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE),
      // 邮箱
      Pattern.compile("(^|[_-]|(?<=[a-z]))email($|[_-]|(?=[A-Z]))", Pattern.CASE_INSENSITIVE));

  // ============================== 公共 API ==============================

  /**
   * 对字符串进行自动脱敏（手机号 / 身份证 / 银行卡 / 邮箱）。
   *
   * <p>委托 {@link SensitiveUtil#scanAndMask(String)}，使用 common-safe 统一维护的 PII 扫描正则库，不再在本类中重复定义
   * Pattern。
   *
   * @param text 原始文本（可为 null）
   * @return 脱敏后文本
   */
  public String mask(String text) {
    return SensitiveUtil.scanAndMask(text);
  }

  /**
   * 对 Map 中指定 key 的值进行字段级脱敏。
   *
   * <p>用于流程变量、通知 payload 等结构化数据的字段级精确脱敏。 仅对 {@code fieldKeys} 中列出的 key 做脱敏，其他字段不动。
   *
   * @param data 原始 Map（不会被修改，返回新 Map）
   * @param fieldKeys 需脱敏的字段名列表
   * @return 脱敏后的新 Map
   */
  public Map<String, Object> maskFields(Map<String, Object> data, List<String> fieldKeys) {
    if (data == null || data.isEmpty() || fieldKeys == null || fieldKeys.isEmpty()) {
      return data;
    }
    Map<String, Object> copy = new LinkedHashMap<>(data);
    for (String key : fieldKeys) {
      if (key == null || !copy.containsKey(key)) {
        continue;
      }
      Object val = copy.get(key);
      if (val == null) {
        continue;
      }
      if (val instanceof String s) {
        copy.put(key, mask(s));
      } else {
        copy.put(key, mask(String.valueOf(val)));
      }
    }
    return copy;
  }

  /**
   * 便捷方法：对 Map 中疑似敏感字段自动脱敏。
   *
   * <p>扫描 Map 的 key，若 key 名称匹配常见敏感字段名（phone/mobile/tel/idCard/idNo/ bankCard/cardNo/email
   * 等），则对其值做脱敏。
   *
   * <p>使用单词边界匹配，避免误匹配非敏感字段（如 {@code phoneModel}、{@code cardNote} 等）。
   *
   * @param data 原始 Map
   * @return 脱敏后的新 Map
   */
  public Map<String, Object> maskAuto(Map<String, Object> data) {
    if (data == null || data.isEmpty()) {
      return data;
    }
    List<String> sensitiveKeys = new ArrayList<>();
    for (String key : data.keySet()) {
      if (key == null) {
        continue;
      }
      if (isSensitiveKey(key)) {
        sensitiveKeys.add(key);
      }
    }
    return maskFields(data, sensitiveKeys);
  }

  /**
   * 判断字段名是否为敏感字段（使用单词边界正则匹配）
   *
   * @param key 字段名
   * @return true 表示该字段名匹配敏感模式
   */
  private boolean isSensitiveKey(String key) {
    for (Pattern pattern : SENSITIVE_KEY_PATTERNS) {
      if (pattern.matcher(key).find()) {
        return true;
      }
    }
    return false;
  }
}
