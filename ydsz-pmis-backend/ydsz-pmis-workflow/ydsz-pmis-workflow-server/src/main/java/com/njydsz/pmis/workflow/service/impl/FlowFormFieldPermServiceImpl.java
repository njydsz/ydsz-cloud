package com.njydsz.pmis.workflow.server.service.impl.integration;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.workflow.server.service.integration.FlowFormFieldPermService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 表单字段权限服务实现（P0-2 落地）。
 *
 * <p>对标钉钉/飞书审批的表单字段权限控制。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@Service
public class FlowFormFieldPermServiceImpl implements FlowFormFieldPermService {

    /** 权限类型常量 */
    public static final String PERM_EDIT = "EDIT";
    public static final String PERM_READONLY = "READONLY";
    public static final String PERM_HIDDEN = "HIDDEN";
    public static final String PERM_REQUIRED = "REQUIRED";

    @Override
    public Map<String, String> parseFieldPerms(String formFieldsConfig) {
        if (!StringUtils.hasText(formFieldsConfig)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = JsonUtils.parseMap(formFieldsConfig);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, String> perms = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() != null) {
                    perms.put(entry.getKey(), String.valueOf(entry.getValue()).toUpperCase());
                }
            }
            return perms;
        } catch (Exception e) {
            log.warn("[FormFieldPerm] 解析字段权限配置失败: {} err={}", formFieldsConfig, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public void validateFieldPerms(Map<String, String> fieldPerms,
                                   Map<String, Object> submittedVars,
                                   Map<String, Object> existingVars) {
        if (fieldPerms == null || fieldPerms.isEmpty()) {
            return;
        }
        Map<String, Object> submitted = submittedVars == null ? Collections.emptyMap() : submittedVars;
        Map<String, Object> existing = existingVars == null ? Collections.emptyMap() : existingVars;

        for (Map.Entry<String, String> entry : fieldPerms.entrySet()) {
            String fieldKey = entry.getKey();
            String perm = entry.getValue();
            Object submittedVal = submitted.get(fieldKey);

            switch (perm) {
                case PERM_HIDDEN:
                    // HIDDEN 字段不允许提交
                    if (submitted.containsKey(fieldKey)) {
                        throw new BizException(BizErrorCode.BAD_REQUEST,
                                "error.workflow.msg_form_field_hidden", fieldKey);
                    }
                    break;

                case PERM_READONLY:
                    // READONLY 字段不允许修改（与已有值比较）
                    if (submitted.containsKey(fieldKey)) {
                        Object existingVal = existing.get(fieldKey);
                        if (!Objects.equals(existingVal, submittedVal)) {
                            throw new BizException(BizErrorCode.BAD_REQUEST,
                                    "error.workflow.msg_form_field_readonly", fieldKey);
                        }
                    }
                    break;

                case PERM_REQUIRED:
                    // REQUIRED 字段不能为空
                    if (submittedVal == null || (submittedVal instanceof String s && s.isBlank())) {
                        throw new BizException(BizErrorCode.BAD_REQUEST,
                                "error.workflow.msg_form_field_required", fieldKey);
                    }
                    break;

                case PERM_EDIT:
                default:
                    // EDIT 无限制
                    break;
            }
        }
    }

    @Override
    public Map<String, Object> applyFieldPerms(Map<String, String> fieldPerms,
                                                Map<String, Object> variables) {
        if (fieldPerms == null || fieldPerms.isEmpty() || variables == null) {
            return variables == null ? Collections.emptyMap() : variables;
        }
        Map<String, Object> result = new LinkedHashMap<>(variables);
        // 移除 HIDDEN 字段
        for (Map.Entry<String, String> entry : fieldPerms.entrySet()) {
            if (PERM_HIDDEN.equals(entry.getValue())) {
                result.remove(entry.getKey());
            }
        }
        return result;
    }
}
