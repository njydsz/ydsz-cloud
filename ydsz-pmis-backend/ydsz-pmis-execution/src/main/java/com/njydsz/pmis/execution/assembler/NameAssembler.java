package com.njydsz.pmis.execution.assembler;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 名称装配器
 *
 * <p>集中处理跨服务用户/客户/项目名称拉取，对失败进行降级。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NameAssembler {

    private final UserServiceClient userServiceClient;

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
            log.warn("[ExecNameAssembler] 拉取员工 {} 名称失败: {}", id, e.getMessage());
        }
        return null;
    }
}
