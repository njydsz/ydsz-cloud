package com.njydsz.pmis.cronjob.web.controller.connector;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.server.core.connector.ConnectorConfig;
import com.njydsz.pmis.cronjob.server.core.connector.ConnectorExportResult;
import com.njydsz.pmis.cronjob.server.core.connector.ConnectorManager;
import com.njydsz.pmis.cronjob.server.core.connector.ConnectorTaskInfo;
import com.njydsz.pmis.cronjob.server.core.connector.JobConnector;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 生态连接器 Controller（P2-3）。
 *
 * <p>提供与外部调度系统的集成接口：测试连接、导入任务、导出任务、查询远程任务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "生态连接器")
@RestController
@RequestMapping("/cronjob/connector")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorManager connectorManager;

    /**
     * 获取所有已注册的连接器类型。
     */
    @Operation(summary = "查询已注册连接器类型")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @GetMapping("/types")
    public BaseResponse<List<String>> types() {
        return BaseResponse.ok(connectorManager.getRegisteredTypes());
    }

    /**
     * 测试连接器连接。
     */
    @Operation(summary = "测试连接")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @PostMapping("/test")
    public BaseResponse<Boolean> testConnection(@RequestBody ConnectorConfig config,
                                           @RequestParam String type) {
        JobConnector connector = connectorManager.getConnector(type);
        if (connector == null) {
            return BaseResponse.fail("不支持的连接器类型: " + type);
        }
        return BaseResponse.ok(connector.testConnection(config));
    }

    /**
     * 查询外部系统中的任务列表（不导入）。
     */
    @Operation(summary = "查询远程任务列表")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
    @PostMapping("/remote-tasks")
    public BaseResponse<List<ConnectorTaskInfo>> listRemoteTasks(@RequestBody ConnectorConfig config,
                                                            @RequestParam String type) {
        JobConnector connector = connectorManager.getConnector(type);
        if (connector == null) {
            return BaseResponse.fail("不支持的连接器类型: " + type);
        }
        return BaseResponse.ok(connector.listRemoteTasks(config));
    }

    /**
     * 从外部系统导入任务。
     */
    @Operation(summary = "导入任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_CREATE)
    @PostMapping("/import")
    public BaseResponse<List<ConnectorTaskInfo>> importTasks(@RequestBody ConnectorConfig config,
                                                        @RequestParam String type) {
        JobConnector connector = connectorManager.getConnector(type);
        if (connector == null) {
            return BaseResponse.fail("不支持的连接器类型: " + type);
        }
        return BaseResponse.ok(connector.importTasks(config));
    }

    /**
     * 导出任务到外部系统。
     */
    @Operation(summary = "导出任务")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
    @PostMapping("/export")
    public BaseResponse<ConnectorExportResult> exportTasks(@RequestBody ExportRequest request) {
        JobConnector connector = connectorManager.getConnector(request.getType());
        if (connector == null) {
            return BaseResponse.fail("不支持的连接器类型: " + request.getType());
        }
        return BaseResponse.ok(connector.exportTasks(request.getTasks(), request.getConfig()));
    }

    /**
     * 导出请求体。
     */
    @lombok.Data
    public static class ExportRequest {
        /** 连接器类型 */
        private String type;
        /** 连接配置 */
        private ConnectorConfig config;
        /** 要导出的任务列表 */
        private List<ConnectorTaskInfo> tasks;
    }
}
