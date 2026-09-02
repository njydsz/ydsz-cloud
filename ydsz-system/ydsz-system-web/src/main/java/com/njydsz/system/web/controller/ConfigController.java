package com.njydsz.system.web.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.excel.spring.ExcelWebSupport;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.dto.ConfigBatchDTO;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.ImportResultVO;
import com.njydsz.system.server.service.ConfigBatchService;
import com.njydsz.system.server.service.ConfigService;

/**
 * 系统配置 Controller
 *
 * <p>提供系统参数的完整 CRUD 接口以及多种业务查询能力（按 ID、按 key、按 group 批量、公开配置）。
 * 系统配置用于集中管理运行时可调参数（如功能开关、限流阈值、第三方服务地址、密钥等）， 配合 Nacos 实现动态配置下发，业务模块通过 {@code @NacosValue} 或 {@code
 * ConfigClient} 监听变更。
 *
 * <p><b>接口路径：</b>{@code /api/v1/config}
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（save/update/remove）启用 {@link Idempotent} 防重复提交（Redis SET NX EX）
 *   <li>写接口启用 {@link RateLimit} 接口级限流（50 QPS）
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>公开配置查询（{@code /public}）无需鉴权，用于前端获取客户端可读参数
 * </ul>
 *
 * <p><b>配置分组：</b>通过 {@code group} 字段对配置进行逻辑分组（如 {@code rate-limit}、{@code third-party}、{@code
 * feature-flag}），便于批量查询与管理。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.system.server.service.ConfigService 配置业务逻辑
 */
@Tag(name = "系统配置", description = "系统参数配置 CRUD + 按键查询 + 分组批量查询 + 批量操作 + 导入导出")
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:config:list")
public class ConfigController {

  private final ConfigService configService;
  private final ConfigBatchService configBatchService;
  private final ExcelWebSupport excelWebSupport;

  // ============================== CRUD 端点 ==============================

  /**
   * 分页查询系统配置
   *
   * <p>支持按 configKey / configName 模糊匹配、configGroup / status 精确匹配过滤， 默认按 {@code updated_at} 降序排列。
   *
   * @param query 分页查询条件（pageNum / pageSize / configKey / configGroup / status）
   * @return 分页结果（总记录数、当前页、每页大小、数据列表）
   */
  @Operation(summary = "分页查询")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<ConfigVO>>> page(ConfigPageQuery query) {
    return YdszResponse.success(configService.page(query));
  }

  /**
   * 游标分页查询系统配置
   *
   * <p>基于 ID 的 seek method 分页，避免深度分页 offset 扫描开销。 适合大数据量连续翻页场景。
   *
   * @param configGroup 配置分组（可选）
   * @param configKey 配置键模糊匹配（可选）
   * @param pageSize 每页条数（默认 20，最大 500）
   * @param cursor 游标（上一页最后一条记录 ID，首次查询不传）
   * @return 游标分页响应
   */
  @Operation(summary = "游标分页查询")
  @GetMapping("/cursor")
  public YdszResponse<PageResponse<ConfigVO>> pageByCursor(
      @RequestParam(required = false) String configGroup,
      @RequestParam(required = false) String configKey,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String cursor) {
    return YdszResponse.success(configService.pageByCursor(configGroup, configKey, pageSize, cursor));
  }

  /**
   * 按 ID 查询系统配置
   *
   * @param id 配置 ID（雪花算法字符串）
   * @return 配置详情；不存在时返回 null
   */
  @Operation(summary = "按 ID 查询")
  @GetMapping("/{id}")
  public YdszResponse<ConfigVO> getById(@PathVariable String id) {
    return YdszResponse.success(configService.getById(id));
  }

  /**
   * 创建系统配置
   *
   * <p>幂等保护：5 秒内同一请求只能成功一次（Redis SET NX EX）；限流 50 QPS；写审计日志。
   *
   * <p>创建后会自动失效 Redis 缓存（{@code ydsz:system:ConfigController:save:lock}）， 并通过 {@code
   * ConfigChangeEvent} 广播变更。
   *
   * @param dto 配置 DTO（命令入参，含 configKey / configValue / configGroup / valueType / isPublic）
   * @return 新创建的配置 ID
   */
  @Audit(
      module = "系统配置",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建配置: ' + #dto.configKey")
  @Operation(summary = "创建配置")
  @RateLimit(resource = "system.config.save", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:config:save:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:config:add")
  @PostMapping
  public YdszResponse<String> save(@Valid @RequestBody ConfigDTO dto) {
    return YdszResponse.success(configService.save(dto));
  }

  /**
   * 更新系统配置
   *
   * <p>幂等保护：5 秒内同一请求只能成功一次；限流 50 QPS；写审计日志。
   *
   * <p>更新后会自动失效 Redis 缓存，并通过 {@code ConfigChangeEvent} 广播变更， 业务方可通过订阅事件感知配置变更。
   *
   * @param dto 配置 DTO（命令入参，必须包含 ID）
   * @return 是否成功
   */
  @Audit(
      module = "系统配置",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新配置: ' + #dto.configKey")
  @Operation(summary = "更新配置")
  @RateLimit(resource = "system.config.update", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:config:update:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:config:edit")
  @PutMapping
  public YdszResponse<Boolean> update(@Valid @RequestBody ConfigDTO dto) {
    return YdszResponse.success(configService.updateById(dto));
  }

  /**
   * 按 ID 删除系统配置
   *
   * <p>幂等保护：5 秒内同一请求只能成功一次；限流 50 QPS；写审计日志。
   *
   * <p>删除后会精准失效单 key / 分组 / 公开配置缓存，并发布 {@code CONFIG_CHANGED} 变更事件。
   * 业务方如依赖某配置项，删除前应由调用方自行确认无引用（本服务不维护跨模块引用关系）。
   *
   * @param id 配置 ID
   * @return 是否成功
   */
  @Audit(
      module = "系统配置",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除配置: ' + #id")
  @Operation(summary = "删除配置")
  @RateLimit(resource = "system.config.remove", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:config:remove:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId() + ':' + #id",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:config:delete")
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(configService.removeById(id));
  }

  // ============================== 批量操作端点 ==============================

  /**
   * 批量创建配置项
   *
   * <p>用于运营初始化场景，单次最多 500 条。
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>批量唯一性校验（不重复校验）
   *   <li>逐条格式校验
   *   <li>逐条 DB 唯一性校验
   *   <li>批量插入
   *   <li>精准缓存失效
   * </ol>
   *
   * <p><b>幂等保护：</b>基于 items 哈希值，30 秒内相同请求不重复处理。
   *
   * @param batchDTO 批量创建请求（含配置列表）
   * @return 批量操作结果（成功数、总数、消息）
   */
  @Audit(
      module = "系统配置",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'批量创建配置: ' + #batchDTO.items.size() + ' 条'")
  @Operation(summary = "批量创建配置", description = "运营初始化场景，单次最多 500 条")
  @RateLimit(resource = "system.config.batch", threshold = 10)
  @Idempotent(
      key = "'ydsz:system:config:batch:' + #batchDTO.items.hashCode() + ':' + T(com.njydsz.common.auth.context."
          + "AuthContextUtils).getUserId()",
      ttlSeconds = 30)
  @AuthApiPermission(apiCodes = "sys:config:add")
  @PostMapping("/batch")
  public YdszResponse<Map<String, Object>> batchSave(
      @Valid @RequestBody ConfigBatchDTO batchDTO) {
    return YdszResponse.success(configBatchService.batchSave(batchDTO.getItems()));
  }

  // ============================== 业务扩展端点 ==============================

  /**
   * 按配置键查询配置值
   *
   * <p>走 Redis 二级缓存（{@code ydsz:config:value:{configKey}}），未命中时回源 DB 并回写缓存。
   *
   * <p>常用于业务方高频读取同一配置（如 {@code ydsz.workflow.sla-default-hours}）， 比 {@link #getById} 更高效（无反序列化 VO
   * 开销）。
   *
   * @param configKey 配置键（如 {@code ydsz.workflow.sla-default-hours}）
   * @return 配置值字符串；不存在时返回 null
   */
  @Operation(summary = "按配置键查询配置值")
  @GetMapping("/key/{configKey}")
  public YdszResponse<String> getByKey(@PathVariable String configKey) {
    return YdszResponse.success(configService.getConfigValue(configKey));
  }

  /**
   * 按配置分组批量查询
   *
   * <p>走 Redis 缓存（{@code ydsz:config:group:{configGroup}}，TTL 5min）。
   *
   * <p>常用于业务方按功能模块批量加载配置（如 {@code rate-limit} 分组包含所有限流参数）。
   *
   * @param configGroup 配置分组（如 {@code rate-limit} / {@code third-party}）
   * @return 该分组下全部配置项列表
   */
  @Operation(summary = "按配置分组批量查询")
  @GetMapping("/group/{configGroup}")
  public YdszResponse<List<ConfigVO>> getConfigsByGroup(@PathVariable String configGroup) {
    return YdszResponse.success(configService.getConfigsByGroup(configGroup));
  }

  /**
   * 查询所有公开配置
   *
   * <p>返回 {@code isPublic=true} 的全部配置项，<b>无需鉴权</b>，用于前端「公开配置」接口。
   *
   * <p>公开配置仅包含前端可读、客户端可见的运行参数（如功能开关、UI 主题、登录页配置等）， 严禁将密钥、连接地址等敏感配置标记为公开。
   *
   * @return 全部公开配置列表
   */
  @Operation(summary = "查询所有公开配置")
  @GetMapping("/public")
  public YdszResponse<List<ConfigVO>> listPublicConfigs() {
    return YdszResponse.success(configService.listPublicConfigs());
  }

  // ============================== 导入导出端点 ==============================

  /**
   * 导出配置为 Excel
   *
   * <p>按配置分组导出（可选，不传则导出全部配置），使用 ydsz-common-excel 生成 Excel 文件。
   *
   * @param configGroup 配置分组（可选，为空则导出全部）
   * @param response HTTP 响应
   * @throws IOException 写入失败时抛出
   */
  @Audit(
      module = "系统配置",
      type = AuditType.OPERATION,
      action = AuditAction.EXPORT,
      content = "'导出配置: ' + #configGroup")
  @Operation(summary = "导出配置", description = "按分组导出配置为 Excel 文件")
  @GetMapping("/export")
  public void exportConfigs(
      @RequestParam(required = false) String configGroup,
      HttpServletResponse response)
      throws IOException {
    byte[] bytes = configService.exportConfigs(configGroup);
    excelWebSupport.writeBytes(response, bytes, buildExportFilename(configGroup));
  }

  /**
   * 构造导出文件名（{@code config_{group}_{timestamp}.xlsx}，group 为空时使用 {@code all}）。
   *
   * @param configGroup 配置分组（可为空）
   * @return 导出文件名
   */
  private String buildExportFilename(String configGroup) {
    String group = configGroup != null ? configGroup : "all";
    return "config_" + group + "_" + System.currentTimeMillis() + ".xlsx";
  }

  /**
   * 从 Excel 导入配置
   *
   * <p>使用 ydsz-common-excel 读取 Excel 文件，逐条校验后批量插入。 导入前校验 (configGroup, configKey) 唯一性，重复时跳过。
   *
   * @param file Excel 文件（.xlsx）
   * @return 导入结果（成功数、失败数、跳过数）
   */
  @Audit(
      module = "系统配置",
      type = AuditType.OPERATION,
      action = AuditAction.IMPORT,
      content = "'导入配置: ' + #file.originalFilename")
  @Operation(summary = "导入配置", description = "从 Excel 文件导入配置")
  @RateLimit(resource = "system.config.import", threshold = 5)
  @AuthApiPermission(apiCodes = "sys:config:add")
  @PostMapping("/import")
  public YdszResponse<ImportResultVO> importConfigs(@RequestParam("file") MultipartFile file)
      throws IOException {
    if (file == null || file.isEmpty()) {
      return YdszResponse.success(
          ImportResultVO.builder()
              .totalCount(0)
              .successCount(0)
              .failCount(0)
              .skipCount(0)
              .message("文件不能为空")
              .build());
    }
    // Service 层返回部分成功明细（成功/跳过/失败条数 + 逐条错误）；
    // Excel 解析或数据库异常直接抛出，由 common-exception 全局处理器返回错误响应（《云顶编码规范》18.4），
    // 不在 Controller 内吞异常包装为 success。
    ImportResultVO result = configService.importConfigs(file.getInputStream());
    return YdszResponse.success(result);
  }
}
