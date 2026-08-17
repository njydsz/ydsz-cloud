package com.njydsz.system.domain.enums;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 系统管理模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}， 支持
 * i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 *
 * <ul>
 *   <li>B90001-B90099 系统配置
 *   <li>B91001-B91099 字典类型/字典项
 *   <li>B92001-B92099 系统变量
 *   <li>B93001-B93099 应用信息
 *   <li>B94001-B94099 租户管理
 *   <li>B95001-B95099 租户套餐
 *   <li>B96001-B96099 实体版本（通用）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "system", description = "系统管理")
public enum SystemExceptionCode implements ExceptionCode {

  // ==================== B90001-B90099 系统配置 ====================
  CONFIG_NOT_FOUND("B90001", "system.config.not.found", 404), // 系统配置不存在（资源未找到，HTTP 404）
  CONFIG_KEY_DUPLICATE("B90002", "system.config.key.duplicate"), // 配置键在分组内重复，违反唯一约束
  CONFIG_KEY_FORMAT_INVALID("B90003", "system.config.key.format.invalid"), // 配置键格式非法（含非法字符、长度超限）
  CONFIG_VALUE_TOO_LONG("B90005", "system.config.value.too.long"), // 配置值长度超过限制
  CONFIG_VALUE_FORMAT_INVALID("B90006", "system.config.value.format.invalid"), // 配置值格式非法（值类型与内容不匹配）
  CONFIG_BATCH_LIMIT_EXCEEDED("B90007", "system.config.batch.limit.exceeded"), // 批量操作超过条数限制
  PARAM_ERROR("B90004", "system.param.error"), // 参数错误（通用）
  VALUE_TYPE_INVALID("B90008", "system.value.type.invalid"), // 值类型非法（配置/变量通用）
  CONFIG_EXPORT_FAILED("B90009", "system.config.export.failed"), // 配置导出失败

  // ==================== B91001-B91099 字典 ====================
  DICT_TYPE_NOT_FOUND("B91001", "system.dict.type.not.found", 404), // 字典类型不存在（资源未找到，HTTP 404）
  DICT_TYPE_CODE_DUPLICATE("B91002", "system.dict.type.code.duplicate"), // 字典类型编码在租户内重复，违反唯一约束
  DICT_TYPE_HAS_ITEMS("B91007", "system.dict.type.has.items"), // 字典类型下存在子项，禁止删除
  DICT_ITEM_NOT_FOUND("B91003", "system.dict.item.not.found", 404), // 字典项不存在（资源未找到，HTTP 404）
  DICT_ITEM_CODE_DUPLICATE("B91004", "system.dict.item.code.duplicate"), // 字典项编码在同类型内重复，违反唯一约束
  SNAPSHOT_PARSE_ERROR("B91006", "system.dict.snapshot.parse.error", 500), // 字典版本快照解析失败

  // ==================== B92001-B92099 系统变量 ====================
  VARIABLE_NOT_FOUND("B92001", "system.variable.not.found", 404), // 系统变量不存在（资源未找到，HTTP 404）
  VARIABLE_KEY_DUPLICATE("B92002", "system.variable.key.duplicate"), // 系统变量键在租户内重复，违反唯一约束

  // ==================== B93001-B93099 应用信息 ====================
  APP_INFO_NOT_FOUND("B93001", "system.app.info.not.found", 404), // 应用信息不存在（资源未找到，HTTP 404）
  APP_KEY_DUPLICATE("B93002", "system.app.key.duplicate"), // 应用 Key（client_id）在租户内重复，违反唯一约束

  // ==================== B94001-B94099 租户管理 ====================
  TENANT_NOT_FOUND("B94001", "system.tenant.not.found", 404), // 租户不存在
  TENANT_CODE_DUPLICATE("B94002", "system.tenant.code.duplicate"), // 租户编码全局重复
  TENANT_PLAN_LINKED("B94003", "system.tenant.plan.linked"), // 套餐下存在关联租户，禁止删除
  TENANT_LINKED("B94004", "system.tenant.linked"), // 租户下存在业务数据，禁止删除
  TENANT_BUILTIN("B94005", "system.tenant.builtin"), // 内置平台租户禁止删除

  // ==================== B95001-B95099 租户套餐 ====================
  TENANT_PLAN_NOT_FOUND("B95001", "system.tenant.plan.not.found", 404), // 套餐不存在
  TENANT_PLAN_CODE_DUPLICATE("B95002", "system.tenant.plan.code.duplicate"), // 套餐编码全局重复

  // ==================== B96001-B96099 实体版本（通用） ====================
  ENTITY_VERSION_NOT_FOUND("B96001", "system.entity.version.not.found", 404); // 实体版本不存在

  /** HTTP 状态码：客户端参数错误 */
  private static final int HTTP_BAD_REQUEST = 400;

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  SystemExceptionCode(String code, String key) {
    this(code, key, HTTP_BAD_REQUEST);
  }

  SystemExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }
}
