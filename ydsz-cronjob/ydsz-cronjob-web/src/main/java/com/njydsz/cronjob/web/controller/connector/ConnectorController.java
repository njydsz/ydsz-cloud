package com.njydsz.cronjob.web.controller.connector;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.domain.dto.post.ConnectorConfigPostDTO;
import com.njydsz.cronjob.domain.enums.CronjobExceptionCode;
import com.njydsz.cronjob.server.core.connector.ConnectorConfig;
import com.njydsz.cronjob.server.core.connector.ConnectorExportResult;
import com.njydsz.cronjob.server.core.connector.ConnectorManager;
import com.njydsz.cronjob.server.core.connector.ConnectorTaskInfo;
import com.njydsz.cronjob.server.core.connector.JobConnector;

/**
 * 生态连接器 Controller（P2-3）。
 *
 * <p>提供与外部调度系统的集成接口：测试连接、导入任务、导出任务、查询远程任务。 通过 {@link JobConnector} SPI
 * 接入不同的外部调度系统。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #types} - 查询已注册的连接器类型（用于下拉选择器）
 *   <li>{@link #testConnection} - 测试与外部系统的连通性
 *   <li>{@link #listRemoteTasks} - 查询外部系统中的任务列表（不导入）
 *   <li>{@link #importTasks} - 从外部系统导入任务到本系统
 *   <li>{@link #exportTasks} - 将本系统任务导出到外部系统
 * </ul>
 *
 * <p>所有连接器由 {@link ConnectorManager} 统一管理，通过类型字符串路由。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "生态连接器", description = "外部调度系统集成：测试连接、导入/导出、查询远程任务")
@RestController
@RequestMapping("/api/v1/cronjob/connector")
@RequiredArgsConstructor
public class ConnectorController {

  private final ConnectorManager connectorManager;

  /**
   * 获取所有已注册的连接器类型。
   *
   * <p>前端连接器下拉选择器使用，返回所有可用的连接器类型字符串（XXL_JOB / POWER_JOB 等）。
   *
   * @return 已注册连接器类型列表
   */
  @Operation(summary = "查询已注册连接器类型")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_CONNECTOR_VIEW)
  @GetMapping("/types")
  public YdszResponse<List<String>> types() {
    return YdszResponse.success(connectorManager.getRegisteredTypes());
  }

  /**
   * 测试连接器连接。
   *
   * <p>使用给定的连接配置（endpoint/认证信息等）尝试连接外部系统，验证配置正确性。 不会修改任何数据，用于导入/导出前的连通性验证。
   *
   * @param dto 连接配置（endpoint/authType/username/password/accessKey/secretKey 等）
   * @param type 连接器类型（XXL_JOB / POWER_JOB / ELASTIC_JOB 等）
   * @return true=连接成功，false=连接失败
   */
  @Operation(summary = "测试连接")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_CONNECTOR_TEST)
  @RateLimit(resource = "cronjob.connector.testConnection", threshold = 50)
  @Idempotent(key = "ydsz:cronjob:ConnectorController:testConnection:lock", ttlSeconds = 5)
  @PostMapping("/test")
  @Audit(
      module = "连接器管理",
      type = AuditType.OPERATION,
      action = AuditAction.OTHER,
      content = "'testConnection'")
  public YdszResponse<Boolean> testConnection(
      @RequestBody ConnectorConfigPostDTO dto, @RequestParam String type) {
    JobConnector connector = connectorManager.getConnector(type);
    if (connector == null) {
      return YdszResponse.error(CronjobExceptionCode.CONNECTOR_NOT_FOUND, "不支持的连接器类型: " + type);
    }
    ConnectorConfig config = toConnectorConfig(dto);
    return YdszResponse.success(connector.testConnection(config));
  }

  /**
   * 查询外部系统中的任务列表（不导入）。
   *
   * <p>仅查询远端任务列表并返回，供前端预览；用户可勾选后调用 {@link #importTasks} 真正导入。
   *
   * @param dto 连接配置
   * @param type 连接器类型
   * @return 远程任务列表（含名称/调度规则/Handler 等）
   */
  @Operation(summary = "查询远程任务列表")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_CONNECTOR_VIEW)
  @RateLimit(resource = "cronjob.connector.listRemoteTasks", threshold = 50)
  @Idempotent(key = "ydsz:cronjob:ConnectorController:listRemoteTasks:lock", ttlSeconds = 5)
  @PostMapping("/remote-tasks")
  public YdszResponse<List<ConnectorTaskInfo>> listRemoteTasks(
      @RequestBody ConnectorConfigPostDTO dto, @RequestParam String type) {
    JobConnector connector = connectorManager.getConnector(type);
    if (connector == null) {
      return YdszResponse.error(CronjobExceptionCode.CONNECTOR_NOT_FOUND, "不支持的连接器类型: " + type);
    }
    ConnectorConfig config = toConnectorConfig(dto);
    return YdszResponse.success(connector.listRemoteTasks(config));
  }

  /**
   * 从外部系统导入任务。
   *
   * <p>调用对应连接器的 importTasks 方法，将外部系统的任务定义转换为本系统任务并持久化。 已存在（jobKey 相同）的任务会被覆盖更新（upsert 语义）。
   *
   * @param dto 连接配置
   * @param type 连接器类型
   * @return 导入成功的任务列表
   */
  @Operation(summary = "导入任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_CREATE)
  @RateLimit(resource = "cronjob.connector.importTasks", threshold = 50)
  @Idempotent(key = "ydsz:cronjob:ConnectorController:importTasks:lock", ttlSeconds = 5)
  @PostMapping("/import")
  public YdszResponse<List<ConnectorTaskInfo>> importTasks(
      @RequestBody ConnectorConfigPostDTO dto, @RequestParam String type) {
    JobConnector connector = connectorManager.getConnector(type);
    if (connector == null) {
      return YdszResponse.error(CronjobExceptionCode.CONNECTOR_NOT_FOUND, "不支持的连接器类型: " + type);
    }
    ConnectorConfig config = toConnectorConfig(dto);
    return YdszResponse.success(connector.importTasks(config));
  }

  /**
   * 导出任务到外部系统。
   *
   * <p>将本系统的任务列表（{@link ConnectorTaskInfo} 格式）通过连接器推送到外部调度系统。 返回导出结果（含成功数、失败明细等）。
   *
   * @param request 导出请求（含 type/config/tasks）
   * @return 导出结果
   */
  @Operation(summary = "导出任务")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @RateLimit(resource = "cronjob.connector.exportTasks", threshold = 50)
  @Idempotent(key = "ydsz:cronjob:ConnectorController:exportTasks:lock", ttlSeconds = 5)
  @PostMapping("/export")
  @Audit(
      module = "连接器管理",
      type = AuditType.OPERATION,
      action = AuditAction.OTHER,
      content = "'exportTasks'")
  public YdszResponse<ConnectorExportResult> exportTasks(@RequestBody ExportRequest request) {
    JobConnector connector = connectorManager.getConnector(request.getType());
    if (connector == null) {
      return YdszResponse.error(
          CronjobExceptionCode.CONNECTOR_NOT_FOUND, "不支持的连接器类型: " + request.getType());
    }
    ConnectorConfig config = toConnectorConfig(request.getConfig());
    return YdszResponse.success(connector.exportTasks(request.getTasks(), config));
  }

  /** 导出请求体。 */
  @lombok.Data
  public static class ExportRequest {
    /** 连接器类型 */
    private String type;

    /** 连接配置 */
    private ConnectorConfigPostDTO config;

    /** 要导出的任务列表 */
    private List<ConnectorTaskInfo> tasks;
  }

  /** 将 DTO 转换为 ConnectorConfig（server 层对象）。 */
  private ConnectorConfig toConnectorConfig(ConnectorConfigPostDTO dto) {
    ConnectorConfig config = new ConnectorConfig();
    config.setEndpoint(dto.getEndpoint());
    config.setAuthType(dto.getAuthType());
    config.setUsername(dto.getUsername());
    config.setPassword(dto.getPassword());
    config.setAccessKey(dto.getAccessKey());
    config.setSecretKey(dto.getSecretKey());
    config.setExtraProps(dto.getExtraProps());
    config.setConnectTimeoutSeconds(dto.getConnectTimeoutSeconds());
    config.setReadTimeoutSeconds(dto.getReadTimeoutSeconds());
    return config;
  }
}
