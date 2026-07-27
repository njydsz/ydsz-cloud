package com.njydsz.cronjob.web.controller.connector;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.server.core.connector.ConnectorConfig;
import com.njydsz.cronjob.server.core.connector.ConnectorExportResult;
import com.njydsz.cronjob.server.core.connector.ConnectorManager;
import com.njydsz.cronjob.server.core.connector.ConnectorTaskInfo;
import com.njydsz.cronjob.server.core.connector.JobConnector;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.dto.post.ConnectorConfigPostDTO;
import com.njydsz.cronjob.domain.vo.ConnectorExportResultVO;
import com.njydsz.cronjob.domain.vo.ConnectorTaskInfoVO;
import com.njydsz.cronjob.domain.vo.StringVO;

/**
 * 生态连接器 Controller（P2-3）。
 *
 * <p>提供与外部调度系统的集成接口：测试连接、导入任务、导出任务、查询远程任务。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "生态连接器")
@RestController
@RequestMapping("/api/v1/cronjob/connector")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorManager connectorManager;

    /**
     * 获取所有已注册的连接器类型。
     */
    @Operation(summary = "查询已注册连接器类型")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/types")
    public BaseResponse<List<StringVO>> types() {
        return BaseResponse.success(CronjobConverter.INSTANT.stringListToVO(connectorManager.getRegisteredTypes()));
    }

    /**
     * 测试连接器连接。
     */
    @Operation(summary = "测试连接")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @RateLimit(resource = "cronjob.connector.testConnection", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:ConnectorController:testConnection:lock", ttlSeconds = 5)
    @PostMapping("/test")
    public BaseResponse<Boolean> testConnection(@RequestBody ConnectorConfigPostDTO dto,
                                           @RequestParam String type) {
        JobConnector connector = connectorManager.getConnector(type);
        if (connector == null) {
            return BaseResponse.error("不支持的连接器类型: " + type);
        }
        ConnectorConfig config = toConnectorConfig(dto);
        return BaseResponse.success(connector.testConnection(config));
    }

    /**
     * 查询外部系统中的任务列表（不导入）。
     */
    @Operation(summary = "查询远程任务列表")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @RateLimit(resource = "cronjob.connector.listRemoteTasks", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:ConnectorController:listRemoteTasks:lock", ttlSeconds = 5)
    @PostMapping("/remote-tasks")
    public BaseResponse<List<ConnectorTaskInfoVO>> listRemoteTasks(@RequestBody ConnectorConfigPostDTO dto,
                                                            @RequestParam String type) {
        JobConnector connector = connectorManager.getConnector(type);
        if (connector == null) {
            return BaseResponse.error("不支持的连接器类型: " + type);
        }
        ConnectorConfig config = toConnectorConfig(dto);
        return BaseResponse.success(connector.listRemoteTasks(config));
    }

    /**
     * 从外部系统导入任务。
     */
    @Operation(summary = "导入任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_CREATE)
    @RateLimit(resource = "cronjob.connector.importTasks", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:ConnectorController:importTasks:lock", ttlSeconds = 5)
    @PostMapping("/import")
    public BaseResponse<List<ConnectorTaskInfoVO>> importTasks(@RequestBody ConnectorConfigPostDTO dto,
                                                        @RequestParam String type) {
        JobConnector connector = connectorManager.getConnector(type);
        if (connector == null) {
            return BaseResponse.error("不支持的连接器类型: " + type);
        }
        ConnectorConfig config = toConnectorConfig(dto);
        return BaseResponse.success(connector.importTasks(config));
    }

    /**
     * 导出任务到外部系统。
     */
    @Operation(summary = "导出任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
    @RateLimit(resource = "cronjob.connector.exportTasks", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:ConnectorController:exportTasks:lock", ttlSeconds = 5)
    @PostMapping("/export")
    public BaseResponse<ConnectorExportResultVO> exportTasks(@RequestBody ExportRequest request) {
        JobConnector connector = connectorManager.getConnector(request.getType());
        if (connector == null) {
            return BaseResponse.error("不支持的连接器类型: " + request.getType());
        }
        ConnectorConfig config = toConnectorConfig(request.getConfig());
        return BaseResponse.success(connector.exportTasks(request.getTasks(), config));
    }

    /**
     * 导出请求体。
     */
    @lombok.Data
    public static class ExportRequest {
        /** 连接器类型 */
        private String type;
        /** 连接配置 */
        private ConnectorConfigPostDTO config;
        /** 要导出的任务列表 */
        private List<ConnectorTaskInfo> tasks;
    }

    /**
     * 将 DTO 转换为 ConnectorConfig（server 层对象）。
     */
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
