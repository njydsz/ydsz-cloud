package com.njydsz.pmis.project.web.controller.report;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.core.response.BaseResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 仪表盘布局 Controller（P2-10）
 *
 * <p>用户可保存自定义仪表盘布局到服务端，实现跨设备同步。
 * 布局配置以 JSON 存储在 pmis_dashboard_layout 表中，
 * 按 user_id + layout_key 唯一约束保证每个用户每种布局只有一份。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/api/project/dashboard/layout")
@RequiredArgsConstructor
@Validated
@Tag(name = "仪表盘布局", description = "用户仪表盘布局保存与加载（跨设备同步）")
public class DashboardLayoutController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 保存（或更新）当前用户的仪表盘布局
     *
     * @param layoutKey 布局标识（如 'main'）
     * @param body      请求体，包含 layoutConfig（JSON 字符串）
     * @return 操作结果
     */
    @PutMapping("/{layoutKey}")
    @Operation(summary = "保存仪表盘布局")
    public BaseResponse<Void> save(@PathVariable String layoutKey, @RequestBody Map<String, Object> body) {
        String userId = AuthContext.getUserId();
        String layoutConfig = body.get("layoutConfig") != null ? body.get("layoutConfig").toString() : "{}";

        // UPSERT: 存在则更新，不存在则插入
        int updated = jdbcTemplate.update(
                "UPDATE pmis_dashboard_layout SET layout_config = ?::jsonb, updated_at = ? " +
                        "WHERE user_id = ? AND layout_key = ?",
                layoutConfig, LocalDateTime.now(), userId, layoutKey
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO pmis_dashboard_layout (user_id, layout_key, layout_config, created_by, created_at, updated_at, tenant_id) " +
                            "VALUES (?, ?, ?::jsonb, ?, ?, ?, 1)",
                    userId, layoutKey, layoutConfig, userId, LocalDateTime.now(), LocalDateTime.now()
            );
        }

        log.info("[DashboardLayout] 保存布局: userId={}, key={}", userId, layoutKey);
        return BaseResponse.ok();
    }

    /**
     * 加载当前用户的仪表盘布局
     *
     * @param layoutKey 布局标识
     * @return 布局配置 JSON，不存在时返回空对象
     */
    @GetMapping("/{layoutKey}")
    @Operation(summary = "加载仪表盘布局")
    public BaseResponse<Map<String, Object>> load(@PathVariable String layoutKey) {
        String userId = AuthContext.getUserId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT layout_config FROM pmis_dashboard_layout WHERE user_id = ? AND layout_key = ?",
                userId, layoutKey
        );

        if (rows.isEmpty()) {
            return BaseResponse.ok(Map.of("layoutConfig", "{}"));
        }

        return BaseResponse.ok(Map.of("layoutConfig", rows.get(0).get("layout_config")));
    }

    /**
     * 重置（删除）当前用户的仪表盘布局
     *
     * @param layoutKey 布局标识
     * @return 操作结果
     */
    @DeleteMapping("/{layoutKey}")
    @Operation(summary = "重置仪表盘布局")
    public BaseResponse<Void> reset(@PathVariable String layoutKey) {
        String userId = AuthContext.getUserId();
        jdbcTemplate.update(
                "DELETE FROM pmis_dashboard_layout WHERE user_id = ? AND layout_key = ?",
                userId, layoutKey
        );
        log.info("[DashboardLayout] 重置布局: userId={}, key={}", userId, layoutKey);
        return BaseResponse.ok();
    }
}
