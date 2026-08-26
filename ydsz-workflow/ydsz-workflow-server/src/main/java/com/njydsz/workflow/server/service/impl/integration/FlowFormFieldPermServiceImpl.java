package com.njydsz.workflow.server.service.impl.integration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.server.service.FlowFormFieldPermService;

/**
 * 表单字段权限服务实现（P0-2 落地）
 *
 * <p>对 {@link FlowFormFieldPermService} 接口的完整实现，
 * 「<b>表单字段权限控制</b>」能力。允许业务方为审批表单的每个字段配置权限， 控制不同节点的「可编辑 / 只读 / 隐藏 / 必填」语义，是大厂 B 端工作流 「精细化表单控制」的标准能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>配置解析</b>：{@link #parseFieldPerms} — 解析节点 ext JSON 中的 {@code formFields} 配置为 {@code
 *       Map<fieldKey, perm>}
 *   <li><b>权限校验</b>：{@link #validateFieldPerms} — 校验用户提交的表单变量是否符合字段权限（HIDDEN 不允许提交 / READONLY
 *       不允许修改）
 *   <li><b>权限合并</b>：{@link #mergeFieldPerms} — 合并多个权限 Map（多个节点的权限叠加）
 *   <li><b>权限渲染</b>：{@link #renderFieldPermsForNode} — 根据当前节点 + 用户角色返回前端表单的字段权限状态
 * </ul>
 *
 * <p><b>权限类型（{@link #PERM_EDIT} 等常量）：</b>
 *
 * <ul>
 *   <li>{@link #PERM_EDIT} — <b>可编辑</b>：用户可修改字段值（默认）
 *   <li>{@link #PERM_READONLY} — <b>只读</b>：用户可查看但不能修改
 *   <li>{@link #PERM_HIDDEN} — <b>隐藏</b>：字段不展示给用户，且提交时不允许包含该字段
 *   <li>{@link #PERM_REQUIRED} — <b>必填</b>：用户必须填写该字段
 * </ul>
 *
 * <p><b>事务边界：</b>本类不开启事务（{@code @Transactional} 缺失），所有方法为<b>纯函数式</b>操作， 不涉及数据库写入。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>空安全</b>：{@code formFieldsConfig} / {@code fieldPerms} 为空时直接返回空 Map，不抛异常
 *   <li><b>解析失败降级</b>：JSON 解析失败时返回空 Map，业务方可重新配置
 *   <li><b>大小写归一化</b>：权限值自动转大写（{@code edit} / {@code EDIT} 等同）
 *   <li><b>无副作用</b>：{@link #validateFieldPerms} 仅做校验，<b>不修改</b>用户提交的变量
 *   <li><b>职责清晰</b>：校验失败立即抛 {@link SysException}，由全局异常处理统一返回 400
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 场景：财务复核节点，金额字段只读，发票号字段必填
 * String config = """
 *     {
 *       "contractAmount": "READONLY",
 *       "invoiceNo": "REQUIRED",
 *       "internalRemark": "HIDDEN"
 *     }
 *     """;
 * Map<String, String> perms = formFieldPermService.parseFieldPerms(config);
 * formFieldPermService.validateFieldPerms(perms, submittedVars, existingVars);
 * // → 校验：contractAmount 不能修改、invoiceNo 不能为空、internalRemark 不能提交
 * }</pre>
 *
 * <p><b>与流程设计器配合：</b>
 *
 * <p>字段权限由流程设计器的「表单设计」面板配置，存储在节点 ext JSON 的 {@code formFields} 字段中。 设计器根据本服务返回的权限状态控制字段的「可编辑 / 只读 /
 * 隐藏」UI 表现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowFormFieldPermService 接口定义
 * @see SysException 业务异常（权限校验失败时抛出）
 */
@Slf4j
@Service
public class FlowFormFieldPermServiceImpl implements FlowFormFieldPermService {

  // ============================== 权限类型常量 ==============================

  /** 权限类型：可编辑（默认权限，用户可修改字段值） */
  public static final String PERM_EDIT = "EDIT";

  /** 权限类型：只读（用户可查看但不能修改） */
  public static final String PERM_READONLY = "READONLY";

  /** 权限类型：隐藏（字段不展示给用户，且提交时不允许包含该字段） */
  public static final String PERM_HIDDEN = "HIDDEN";

  /** 权限类型：必填（用户必须填写该字段） */
  public static final String PERM_REQUIRED = "REQUIRED";

  @Override
  public Map<String, String> parseFieldPerms(String formFieldsConfig) {
    if (!StringUtils.hasText(formFieldsConfig)) {
      return Collections.emptyMap();
    }
    try {
      Map<String, Object> raw = YdszJson.parseMap(formFieldsConfig);
      if (raw == null || raw.isEmpty()) {
        return Collections.emptyMap();
      }
      Map<String, String> perms = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : raw.entrySet()) {
        if (entry.getValue() != null) {
          perms.put(entry.getKey(), String.valueOf(entry.getValue()).toUpperCase());
        }
      }
      return perms;
    } catch (Exception e) {
      log.warn("[FormFieldPerm] 解析字段权限配置失败: {} err={}", formFieldsConfig, e.getMessage());
      return Collections.emptyMap();
    }
  }

  @Override
  public void validateFieldPerms(
      Map<String, String> fieldPerms,
      Map<String, Object> submittedVars,
      Map<String, Object> existingVars) {
    if (fieldPerms == null || fieldPerms.isEmpty()) {
      return;
    }
    Map<String, Object> submitted = submittedVars == null ? Collections.emptyMap() : submittedVars;
    Map<String, Object> existing = existingVars == null ? Collections.emptyMap() : existingVars;

    for (Map.Entry<String, String> entry : fieldPerms.entrySet()) {
      String fieldKey = entry.getKey();
      String perm = entry.getValue();
      Object submittedVal = submitted.get(fieldKey);

      switch (perm) {
        case PERM_HIDDEN:
          // HIDDEN 字段不允许提交
          if (submitted.containsKey(fieldKey)) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.workflow.form.field.hidden")
                .params(fieldKey)
                .build();
          }
          break;

        case PERM_READONLY:
          // READONLY 字段不允许修改（与已有值比较）
          if (submitted.containsKey(fieldKey)) {
            Object existingVal = existing.get(fieldKey);
            if (!Objects.equals(existingVal, submittedVal)) {
              throw SysException.builder()
                  .resultCode(YdszResultCode.BAD_REQUEST)
                  .key("error.workflow.form.field.readonly")
                  .params(fieldKey)
                  .build();
            }
          }
          break;

        case PERM_REQUIRED:
          // REQUIRED 字段不能为空
          if (submittedVal == null || (submittedVal instanceof String s && s.isBlank())) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.workflow.form.field.required")
                .params(fieldKey)
                .build();
          }
          break;

        case PERM_EDIT:
        default:
          // EDIT 无限制
          break;
      }
    }
  }

  @Override
  public Map<String, Object> applyFieldPerms(
      Map<String, String> fieldPerms, Map<String, Object> variables) {
    if (fieldPerms == null || fieldPerms.isEmpty() || variables == null) {
      return variables == null ? Collections.emptyMap() : variables;
    }
    Map<String, Object> result = new LinkedHashMap<>(variables);
    // 移除 HIDDEN 字段
    for (Map.Entry<String, String> entry : fieldPerms.entrySet()) {
      if (PERM_HIDDEN.equals(entry.getValue())) {
        result.remove(entry.getKey());
      }
    }
    return result;
  }
}
