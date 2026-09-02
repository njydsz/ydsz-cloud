package com.njydsz.workflow.server.service;

import java.util.Map;

import com.njydsz.common.exception.custom.SysException;

/**
 * 流程表单字段权限服务。
 *
 * <p>按节点/角色控制字段可见/可编辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowFormFieldPermService {

  /**
   * 解析节点的表单字段权限配置。
   *
   * @param formFieldsConfig 节点的 formFieldsConfig JSON
   * @return 字段权限映射（fieldKey → 权限类型），空配置返回空 Map
   */
  Map<String, String> parseFieldPerms(String formFieldsConfig);

  /**
   * 校验提交的表单变量是否符合字段权限规则。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>READONLY 字段：不允许提交新值（值变化时拒绝）
   *   <li>HIDDEN 字段：不允许提交（拒绝）
   *   <li>REQUIRED 字段：值不能为空（拒绝）
   * </ul>
   *
   * @param fieldPerms 字段权限映射
   * @param submittedVars 提交的表单变量
   * @param existingVars 已有变量（用于判断 READONLY 字段是否变化，可空）
   * @throws SysException 校验失败时抛出
   */
  void validateFieldPerms(
      Map<String, String> fieldPerms,
      Map<String, Object> submittedVars,
      Map<String, Object> existingVars);

  /**
   * 对表单变量应用字段权限过滤。
   *
   * <p>用于返回给前端时，将 HIDDEN 字段移除、READONLY 字段保留原值。
   *
   * @param fieldPerms 字段权限映射
   * @param variables 原始变量
   * @return 过滤后的变量 Map
   */
  Map<String, Object> applyFieldPerms(
      Map<String, String> fieldPerms, Map<String, Object> variables);
}
