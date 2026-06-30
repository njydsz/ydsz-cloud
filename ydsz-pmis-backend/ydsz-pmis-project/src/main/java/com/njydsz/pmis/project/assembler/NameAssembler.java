package com.njydsz.pmis.project.assembler;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.project.feign.UserServiceClient;
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

    private final UserServiceClient userServiceClient;

    /**
     * 拉取员工姓名，失败返回 null
     */
    public String resolveEmployee(Long id) {
        if (id == null) return null;
        try {
            R<Map<String, Object>> r = userServiceClient.getEmployee(id);
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

    public String resolveCustomer(Long id) {
        if (id == null) return null;
        try {
            R<String> r = userServiceClient.getCustomerName(id);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("[NameAssembler] 拉取客户 {} 名称失败: {}", id, e.getMessage());
        }
        return null;
    }

    public Map<Long, String> batchEmployeeName(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        try {
            R<Map<Long, String>> r = userServiceClient.batchEmployeeName(ids);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("[NameAssembler] 批量拉取员工名称失败: {}", e.getMessage());
        }
        return Map.of();
    }
}
