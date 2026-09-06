package com.njydsz.system.server.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.ApiPermissionDTO;
import com.njydsz.system.domain.enums.ApiPermissionStatus;
import com.njydsz.system.domain.query.ApiPermissionQuery;
import com.njydsz.system.domain.repository.ApiPermissionRepository;
import com.njydsz.system.domain.vo.ApiPermissionVO;
import com.njydsz.system.server.service.ApiPermissionService;



/**
 * 接口权限 Service 实现
 *
 * <p>对 {@link ApiPermissionService} 接口的完整实现，是「接口权限注册中心」的核心业务逻辑层。
 * 启动时通过 {@code AppPermissionScanRunner} 扫描 {@code @AuthApiPermission} 注解注册接口元数据到 DB。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>启动扫描注册</b>：{@link #scanAndRegister()} — 扫描注解注册新发现的 apiCode（不覆盖已有描述）
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #enable} / {@link #disable} / {@link
 *       #removeById}
 *   <li><b>批量注册</b>：{@link #batchRegister(List)} — 批量插入跳过已有权限码
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}；
 * 读方法不开启事务，依赖 MyBatis 自动提交。
 *
 * <p><b>多租户：</b>扫描注册使用平台租户（{@code 0}）作为默认租户 ID。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ApiPermissionService 接口权限 Service 接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPermissionServiceImpl implements ApiPermissionService {

  /** 平台级租户 ID（扫描注册时使用） */
  private static final String PLATFORM_TENANT_ID = "0";

  /** 默认状态：启用 */
  private static final String DEFAULT_STATUS = ApiPermissionStatus.ENABLED.getCode();

  private final ApiPermissionRepository apiPermissionRepository;
  private final SystemConverter converter;
  private final List<RequestMappingHandlerMapping> handlerMappings;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int scanAndRegister() {
    List<ApiPermissionDTO> permissions = extractApiPermissions();
    if (permissions.isEmpty()) {
      log.info("[ApiPermissionScan] 未扫描到 @AuthApiPermission 注解接口");
      return 0;
    }
    log.info("[ApiPermissionScan] 扫描到 {} 个唯一权限码，准备注册", permissions.size());
    int inserted = batchRegister(permissions);
    log.info("[ApiPermissionScan] 注册完成：新注册 {} 个接口权限", inserted);
    return inserted;
  }

  @Override
  public PageResponse<List<ApiPermissionVO>> page(ApiPermissionQuery query) {
    return apiPermissionRepository.findByPage(query);
  }

  @Override
  public ApiPermissionVO getById(String id) {
    return apiPermissionRepository.findById(id).orElse(null);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean enable(String id) {
    ApiPermissionVO existing = apiPermissionRepository.findById(id).orElse(null);
    if (existing == null) {
      log.warn("[ApiPermissionService] 启用失败：接口权限不存在, id={}", id);
      return false;
    }
    ApiPermissionDTO dto = converter.voToApiPermissionDto(existing);
    dto.setStatus(ApiPermissionStatus.ENABLED.getCode());
    return apiPermissionRepository.updateById(dto);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean disable(String id) {
    ApiPermissionVO existing = apiPermissionRepository.findById(id).orElse(null);
    if (existing == null) {
      log.warn("[ApiPermissionService] 禁用失败：接口权限不存在, id={}", id);
      return false;
    }
    ApiPermissionDTO dto = converter.voToApiPermissionDto(existing);
    dto.setStatus(ApiPermissionStatus.DISABLED.getCode());
    return apiPermissionRepository.updateById(dto);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    return apiPermissionRepository.removeById(id);
  }

  @Override
  public List<ApiPermissionVO> listAll() {
    return apiPermissionRepository.listAllByTenant(PLATFORM_TENANT_ID);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int batchRegister(List<ApiPermissionDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }
    return apiPermissionRepository.insertBatchSkipExisting(dtos);
  }

  /**
   * 从所有 RequestMappingHandlerMapping 中提取 API 权限元数据。
   *
   * <p>遍历所有注册的 handler methods，提取类级和方法级的 {@code @AuthApiPermission} 注解，
   * 组合 RequestMapping 的 path + method 信息，生成 ApiPermissionDTO 列表。
   *
   * @return 提取到的接口权限 DTO 列表（已去重，优先级：方法级注解 > 类级注解）
   */
  private List<ApiPermissionDTO> extractApiPermissions() {
    Map<String, ApiPermissionDTO> uniqueMap = new LinkedHashMap<>();
    for (RequestMappingHandlerMapping handlerMapping : handlerMappings) {
      Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
      for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
        RequestMappingInfo mappingInfo = entry.getKey();
        HandlerMethod handlerMethod = entry.getValue();

        // 1. 提取类级和方法级注解
        AuthApiPermission classAnno =
            handlerMethod.getBeanType().getAnnotation(AuthApiPermission.class);
        AuthApiPermission methodAnno = handlerMethod.getMethodAnnotation(AuthApiPermission.class);

        // 2. 合并 apiCodes（优先方法级）
        String[] apiCodes = mergeApiCodes(classAnno, methodAnno);
        if (apiCodes.length == 0) {
          continue;
        }

        // 3. 提取 URL 模式
        List<String> urlPatterns = extractUrlPatterns(mappingInfo);
        // 4. 提取 HTTP 方法
        List<String> httpMethods = extractHttpMethods(mappingInfo);

        String controllerClass = handlerMethod.getBeanType().getName();
        String methodName = handlerMethod.getMethod().getName();
        String operationSummary = extractOperationSummary(handlerMethod);

        // 5. 为每个 apiCode 生成 ApiPermissionDTO
        ApiPermissionContext ctx = new ApiPermissionContext(operationSummary, httpMethods,
            urlPatterns, controllerClass, methodName);
        registerApiPermissions(uniqueMap, apiCodes, ctx);
      }
    }
    return new ArrayList<>(uniqueMap.values());
  }

  /**
   * API 权限注册上下文（封装 HandlerMethod 提取的元数据）。
   */
  private static class ApiPermissionContext {
    final String operationSummary;
    final String httpMethodStr;
    final String urlPatternStr;
    final String controllerClass;
    final String methodName;

    ApiPermissionContext(String operationSummary, List<String> httpMethods, List<String> urlPatterns,
        String controllerClass, String methodName) {
      this.operationSummary = operationSummary;
      this.httpMethodStr = httpMethods.isEmpty() ? null : String.join(",", httpMethods);
      this.urlPatternStr = urlPatterns.isEmpty() ? null : String.join(",", urlPatterns);
      this.controllerClass = controllerClass;
      this.methodName = methodName;
    }
  }

  /**
   * 将 apiCodes 注册到唯一映射表中。
   *
   * @param uniqueMap 去重映射表
   * @param apiCodes  待注册的权限码数组
   * @param ctx       API 权限注册上下文
   */
  private void registerApiPermissions(Map<String, ApiPermissionDTO> uniqueMap, String[] apiCodes,
      ApiPermissionContext ctx) {
    for (String apiCode : apiCodes) {
      if (apiCode == null || apiCode.isBlank() || uniqueMap.containsKey(apiCode)) {
        continue;
      }
      ApiPermissionDTO dto = ApiPermissionDTO.builder()
          .apiCode(apiCode)
          .apiName(ctx.operationSummary != null ? ctx.operationSummary : apiCode)
          .httpMethod(ctx.httpMethodStr)
          .urlPattern(ctx.urlPatternStr)
          .controllerClass(ctx.controllerClass)
          .methodName(ctx.methodName)
          .description(ctx.operationSummary)
          .status(DEFAULT_STATUS)
          .build();
      uniqueMap.put(apiCode, dto);
    }
  }

  /**
   * 合并类级和方法级的 apiCodes。
   *
   * <p>优先级：方法级注解优先。两者均不存在时返回空数组。
   *
   * @param classAnno  类级注解（可为 null）
   * @param methodAnno 方法级注解（可为 null）
   * @return 合并后的 apiCodes 数组
   */
  private String[] mergeApiCodes(
      AuthApiPermission classAnno, AuthApiPermission methodAnno) {
    if (methodAnno != null && methodAnno.apiCodes().length > 0) {
      return methodAnno.apiCodes();
    }
    if (classAnno != null && classAnno.apiCodes().length > 0) {
      return classAnno.apiCodes();
    }
    return new String[0];
  }

  /**
   * 从 RequestMappingInfo 中提取 URL 模式列表。
   *
   * @param mappingInfo 请求映射信息
   * @return URL 模式字符串列表
   */
  private List<String> extractUrlPatterns(RequestMappingInfo mappingInfo) {
    PathPatternsRequestCondition pathPatternsCondition = mappingInfo.getPathPatternsCondition();
    if (pathPatternsCondition != null) {
      Collection<PathPattern> rawPatterns = pathPatternsCondition.getPatterns();
      List<String> patterns = new ArrayList<>(rawPatterns.size());
      rawPatterns.forEach(p -> patterns.add(p.getPatternString()));
      return patterns;
    }
    List<String> patterns = new ArrayList<>(8);
    PatternsRequestCondition patternsCondition = mappingInfo.getPatternsCondition();
    if (patternsCondition != null) {
      patterns.addAll(patternsCondition.getPatterns());
    }
    return patterns;
  }

  /**
   * 从 RequestMappingInfo 中提取 HTTP 方法列表。
   *
   * @param mappingInfo 请求映射信息
   * @return HTTP 方法字符串列表
   */
  private List<String> extractHttpMethods(RequestMappingInfo mappingInfo) {
    return mappingInfo.getMethodsCondition().getMethods().stream()
        .map(en -> en.name())
        .toList();
  }

  /**
   * 从 HandlerMethod 方法上提取 @Operation 的 summary 作为接口描述。
   *
   * @param handlerMethod 处理方法
   * @return 接口描述；无注解时返回 null
   */
  private String extractOperationSummary(HandlerMethod handlerMethod) {
    Operation operationAnno = handlerMethod.getMethodAnnotation(Operation.class);
    if (operationAnno != null && !operationAnno.summary().isBlank()) {
      return operationAnno.summary();
    }
    return null;
  }
}
