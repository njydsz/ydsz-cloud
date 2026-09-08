package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.system.domain.enums.ApiPermissionStatus;
import com.njydsz.system.domain.enums.SystemExceptionCode;


/**
 * 接口权限注册实体
 *
 * <p>对应数据库表 {@code ydsz_sys_api_permission}，存储启动时扫描 {@code @AuthApiPermission} 注解自动注册的 API 元数据。
 * 权限校验仍走 Redis（{@code ydsz-auth:role-api:{roleCode}}），DB 仅作为「接口注册中心」展示哪些接口存在、属于哪个 Controller。
 *
 * <p><b>充血模型能力：</b>
 *
 * <ul>
 *   <li>{@link #enable()} — 启用接口权限
 *   <li>{@link #disable()} — 禁用接口权限
 *   <li>{@link #markDeleted()} — 标记逻辑删除
 *   <li>{@link #validate()} — 写入前自校验
 * </ul>
 *
 * <p><b>唯一约束：</b>{@code (tenant_id, api_code)} — 同一租户下权限码唯一。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_sys_api_permission")
public class ApiPermission extends MpBaseEntity<String> {

  /** 权限码（如 sys:config:list），同租户内唯一 */
  private String apiCode;

  /** 接口名称/描述（可人工补充） */
  private String apiName;

  /** HTTP 方法（GET/POST/PUT/DELETE 等） */
  private String httpMethod;

  /** URL 模式（Ant 风格，如 /api/config/page） */
  private String urlPattern;

  /** Controller 完全限定名 */
  private String controllerClass;

  /** Controller 方法名 */
  private String methodName;

  /** 接口描述 */
  private String description;

  // ==================== 充血领域方法 ====================

  /**
   * 启用接口权限。
   */
  public void enable() {
    setStatus(ApiPermissionStatus.ENABLED.getCode());
  }

  /**
   * 禁用接口权限。
   */
  public void disable() {
    setStatus(ApiPermissionStatus.DISABLED.getCode());
  }

  /**
   * 标记逻辑删除（将 deleted 字段置为 1）。
   */
  public void markDeleted() {
    setDeleted(1);
  }

  /**
   * 接口权限写入前自校验（领域完整性校验）。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>权限码不能为空
   *   <li>权限码格式校验（不应包含空白字符）
   * </ul>
   *
   * @throws BusinessException 校验失败时抛出
   */
  public void validate() {
    if (apiCode == null || apiCode.isBlank()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "权限码不能为空")
          .data("controllerClass", controllerClass)
          .data("methodName", methodName);
    }
    if (apiCode.contains(" ")) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "权限码不能包含空格")
          .data("apiCode", apiCode);
    }
  }
}
