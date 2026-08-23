package com.njydsz.userinfo.server.auth;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.UserAccountDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.scim.ScimConverter;
import com.njydsz.userinfo.domain.scim.ScimPatchOp;
import com.njydsz.userinfo.domain.scim.ScimUser;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.config.ScimProperties;
import com.njydsz.userinfo.server.service.UserAccountService;

/**
 * SCIM 2.0 PATCH 操作处理器。
 *
 * <p>解析并执行 RFC 7644 Section 3.5.2 定义的 PATCH 操作，将增量修改转换为
 * ydsz {@link UserAccountDTO} 并委托 {@link UserAccountService} 执行更新。
 *
 * <p><b>支持的属性路径：</b>
 *
 * <ul>
 *   <li>{@code userName} — 登录用户名</li>
 *   <li>{@code displayName} — 显示名称</li>
 *   <li>{@code name.formatted} — 真实姓名</li>
 *   <li>{@code emails} / {@code emails.value} — 电子邮箱</li>
 *   <li>{@code phoneNumbers} / {@code phoneNumbers.value} — 电话号码</li>
 *   <li>{@code active} — 账号启用状态</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScimPatchHandler {
  /** SCIM 路径正则捕获组：子属性 */
  private static final int GROUP_SUB_ATTRIBUTE = 4;


  /** 带过滤器的属性路径正则（如 emails[value eq "xxx"].value）。 */
  private static final Pattern FILTERED_PATH_PATTERN =
      Pattern.compile("(\\w+)\\[(.+?)\\](\\.(\\w+))?");

  private final UserAccountService userAccountService;
  private final ScimProperties scimProperties;

  /**
   * 执行 PATCH 操作。
   *
   * <p>解析操作列表，将每个操作转换为 {@link UserAccountDTO} 的对应字段变更，
   * 最终委托 {@link UserAccountService#update} 执行。
   *
   * @param userId 目标用户 ID
   * @param patchOp PATCH 操作请求体
   * @return 更新后的 SCIM User 资源
   * @throws BusinessException 操作无效或解析失败时抛出
   */
  public ScimUser applyPatch(String userId, ScimPatchOp patchOp) {
    if (!scimProperties.isAllowPatch()) {
      throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
    }

    if (patchOp == null || patchOp.getOperations() == null || patchOp.getOperations().isEmpty()) {
      throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
    }

    // 获取当前用户信息
    UserAccountVO currentUser = userAccountService.getById(userId);
    if (currentUser == null) {
      throw new BusinessException(UserInfoExceptionCode.SCIM_USER_NOT_FOUND);
    }

    UserAccountDTO updateDTO = new UserAccountDTO();
    updateDTO.setId(userId);
    boolean hasChanges = false;

    // 按顺序执行每个操作
    for (ScimPatchOp.ScimPatchOperation operation : patchOp.getOperations()) {
      boolean applied = applyOperation(updateDTO, operation, currentUser);
      if (applied) {
        hasChanges = true;
      }
    }

    // 执行更新
    if (hasChanges) {
      userAccountService.save(updateDTO);
      log.info("SCIM PATCH applied: userId={}, operations={}", userId, patchOp.getOperations().size());
    }

    // 返回更新后的用户
    UserAccountVO updatedUser = userAccountService.getById(userId);
    return ScimConverter.toScimUser(updatedUser);
  }

  /**
   * 应用单个 PATCH 操作。
   *
   * @param updateDTO 待更新的 DTO（累积变更）
   * @param operation 单个 PATCH 操作
   * @param currentUser 当前用户信息（用于条件判断）
   * @return true 表示有实际变更
   * @throws BusinessException 操作类型不支持或路径无法解析时抛出
   */
  private boolean applyOperation(
      UserAccountDTO updateDTO,
      ScimPatchOp.ScimPatchOperation operation,
      UserAccountVO currentUser) {
    String op = operation.getOp() != null ? operation.getOp().toLowerCase() : null;
    String path = operation.getPath();
    Object value = operation.getValue();

    if (op == null || op.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
    }

    // 无 path 路径：操作整个资源（replace 语义）
    if (path == null || path.isBlank()) {
      return applyWholeResource(updateDTO, op, value);
    }

    // 解析路径并应用操作
    return applyAttributeUpdate(updateDTO, op, path, value, currentUser);
  }

  /**
   * 对整个资源应用操作（无 path 时）。
   *
   * @param updateDTO 待更新的 DTO
   * @param op 操作类型
   * @param value 操作值（应为 Map 结构）
   * @return true 表示有实际变更
   */
  private boolean applyWholeResource(UserAccountDTO updateDTO, String op, Object value) {
    if ("replace".equals(op) && value instanceof Map<?, ?> mapValue) {
      // 全量替换语义：遍历 Map 中的每个字段
      boolean changed = false;
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        String key = entry.getKey().toString();
        Object val = entry.getValue();
        changed |= setAttribute(updateDTO, key, val, true);
      }
      return changed;
    }
    throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
  }

  /**
   * 对指定属性路径应用操作。
   *
   * @param updateDTO 待更新的 DTO
   * @param op 操作类型
   * @param path 属性路径
   * @param value 操作值
   * @param currentUser 当前用户信息
   * @return true 表示有实际变更
   */
  private boolean applyAttributeUpdate(
      UserAccountDTO updateDTO,
      String op,
      String path,
      Object value,
      UserAccountVO currentUser) {
    // 检查是否为带过滤器的路径
    Matcher matcher = FILTERED_PATH_PATTERN.matcher(path);
    if (matcher.matches()) {
      return applyFilteredUpdate(updateDTO, op, matcher, value, currentUser);
    }

    // 简单属性路径
    return switch (path) {
      case "userName" -> applySimpleUpdate(updateDTO, op, "username", value);
      case "displayName" -> applySimpleUpdate(updateDTO, op, "displayName", value);
      case "name.formatted", "name" -> applySimpleUpdate(updateDTO, op, "realName", value);
      case "emails", "emails.value" -> applyEmailUpdate(updateDTO, op, value, currentUser);
      case "phoneNumbers", "phoneNumbers.value" -> applyPhoneUpdate(updateDTO, op, value, currentUser);
      case "active" -> applyActiveUpdate(updateDTO, op, value);
      default -> {
        log.warn("SCIM PATCH: unsupported path={}", path);
        throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
      }
    };
  }

  /**
   * 应用带过滤器的属性更新。
   *
   * <p>处理形如 {@code emails[value eq "xxx"].value} 的路径。
   *
   * @param updateDTO 待更新的 DTO
   * @param op 操作类型
   * @param matcher 路径正则匹配结果
   * @param value 操作值
   * @param currentUser 当前用户信息
   * @return true 表示有实际变更
   */
  private boolean applyFilteredUpdate(
      UserAccountDTO updateDTO,
      String op,
      Matcher matcher,
      Object value,
      UserAccountVO currentUser) {
    String attribute = matcher.group(1);
    String filter = matcher.group(2);
    String subAttribute = matcher.group(GROUP_SUB_ATTRIBUTE);

    // 仅支持 emails 和 phoneNumbers 的过滤器路径
    if ("emails".equals(attribute)) {
      return applyEmailUpdate(updateDTO, op, value, currentUser);
    }
    if ("phoneNumbers".equals(attribute)) {
      return applyPhoneUpdate(updateDTO, op, value, currentUser);
    }

    log.warn("SCIM PATCH: unsupported filtered path attribute={}", attribute);
    throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
  }

  /**
   * 应用简单属性更新。
   *
   * @param updateDTO 待更新的 DTO
   * @param op 操作类型
   * @param fieldName 字段名
   * @param value 操作值
   * @return true 表示有实际变更
   */
  private boolean applySimpleUpdate(
      UserAccountDTO updateDTO, String op, String fieldName, Object value) {
    return switch (op) {
      case "add", "replace" -> setAttribute(updateDTO, fieldName, value, true);
      case "remove" -> setAttribute(updateDTO, fieldName, null, false);
      default -> throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
    };
  }

  /**
   * 应用邮箱属性更新。
   *
   * @param updateDTO 待更新的 DTO
   * @param op 操作类型
   * @param value 操作值
   * @param currentUser 当前用户信息
   * @return true 表示有实际变更
   */
  private boolean applyEmailUpdate(
      UserAccountDTO updateDTO, String op, Object value, UserAccountVO currentUser) {
    return switch (op) {
      case "add", "replace" -> {
        if (value instanceof String email) {
          updateDTO.setEmail(email);
          yield true;
        } else if (value instanceof Map<?, ?> mapValue) {
          Object emailVal = mapValue.get("value");
          if (emailVal instanceof String email) {
            updateDTO.setEmail(email);
            yield true;
          }
        }
        throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
      }
      case "remove" -> {
        updateDTO.setEmail(null);
        yield true;
      }
      default -> throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
    };
  }

  /**
   * 应用手机号属性更新。
   *
   * @param updateDTO 待更新的 DTO
   * @param op 操作类型
   * @param value 操作值
   * @param currentUser 当前用户信息
   * @return true 表示有实际变更
   */
  private boolean applyPhoneUpdate(
      UserAccountDTO updateDTO, String op, Object value, UserAccountVO currentUser) {
    return switch (op) {
      case "add", "replace" -> {
        if (value instanceof String phone) {
          updateDTO.setPhone(phone);
          yield true;
        } else if (value instanceof Map<?, ?> mapValue) {
          Object phoneVal = mapValue.get("value");
          if (phoneVal instanceof String phone) {
            updateDTO.setPhone(phone);
            yield true;
          }
        }
        throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
      }
      case "remove" -> {
        updateDTO.setPhone(null);
        yield true;
      }
      default -> throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
    };
  }

  /**
   * 应用账号启用状态更新。
   *
   * @param updateDTO 待更新的 DTO
   * @param op 操作类型
   * @param value 操作值
   * @return true 表示有实际变更
   */
  private boolean applyActiveUpdate(UserAccountDTO updateDTO, String op, Object value) {
    if ("add".equals(op) || "replace".equals(op)) {
      if (value instanceof Boolean active) {
        updateDTO.setStatus(
            active
                ? com.njydsz.userinfo.domain.enums.EnableStatusEnum.ENABLED
                : com.njydsz.userinfo.domain.enums.EnableStatusEnum.DISABLED);
        return true;
      }
      throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
    }
    if ("remove".equals(op)) {
      // active 属性不可移除，设为启用
      updateDTO.setStatus(com.njydsz.userinfo.domain.enums.EnableStatusEnum.ENABLED);
      return true;
    }
    throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
  }

  /**
   * 设置 DTO 属性值。
   *
   * @param updateDTO 待更新的 DTO
   * @param fieldName 字段名
   * @param value 属性值
   * @param isReplace 是否为 replace 操作（false 表示 remove）
   * @return true 表示有实际变更
   */
  private boolean setAttribute(
      UserAccountDTO updateDTO, String fieldName, Object value, boolean isReplace) {
    String strValue = value != null ? value.toString() : null;
    return switch (fieldName) {
      // username 创建后不可修改（返回 false 表示忽略）
      case "displayName", "realName" -> {
        updateDTO.setRealName(strValue);
        yield true;
      }
      default -> {
        log.warn("SCIM PATCH: unsupported attribute={}", fieldName);
        throw new BusinessException(UserInfoExceptionCode.SCIM_PATCH_INVALID);
      }
    };
  }
}
