package com.njydsz.pmis.project.server.assembler;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 名称装配器
 *
 * <p>集中处理跨服务用户/客户名称拉取，对失败进行降级。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NameAssembler {

    /** 用户服务 Feign 客户端 */
    private final UserServiceClient userServiceClient;

    /**
     * 拉取员工姓名，失败返回 null。
     *
     * @param id 员工 ID
     * @return 员工姓名；失败或不存在返回 null
     */
    public String resolveEmployee(String id) {
        if (id == null) return null;
        try {
            Result<Map<String, Object>> r = userServiceClient.getEmployee(id);
            if (r != null && r.getData() != null) {
                Object name = r.getData().get("name");
                if (name == null) name = r.getData().get("realName");
                return name == null ? null : name.toString();
            }
        } catch (Exception e) {
            log.warn("[NameAssembler] 拉取员工 {} 名称失败: {}", id, e.getMessage());
        }
        return null;
    }

    /**
     * 拉取客户名称，失败返回 null。
     *
     * @param id 客户 ID
     * @return 客户名称；失败或不存在返回 null
     */
    public String resolveCustomer(String id) {
        if (id == null) return null;
        try {
            Result<String> r = userServiceClient.getCustomerName(id);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("[NameAssembler] 拉取客户 {} 名称失败: {}", id, e.getMessage());
        }
        return null;
    }

    /**
     * 批量拉取员工姓名。
     *
     * @param ids 员工 ID 列表
     * @return 员工 ID 到姓名的映射；失败返回空 Map
     */
    public Map<String, String> batchEmployeeName(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            Result<Map<String, String>> r = userServiceClient.batchEmployeeName(ids);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("[NameAssembler] 批量拉取员工名称失败: {}", e.getMessage());
        }
        return Map.of();
    }

    /**
     * 批量拉取客户名称。
     *
     * @param ids 客户 ID 列表
     * @return 客户 ID 到名称的映射；失败返回空 Map
     */
    public Map<String, String> batchCustomerName(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            Result<Map<String, String>> r = userServiceClient.batchCustomerName(ids);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("[NameAssembler] 批量拉取客户名称失败: {}", e.getMessage());
        }
        return Map.of();
    }
}
