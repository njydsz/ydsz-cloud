paokage oom.njydsz.pmis.projeot.web.oontroller.report;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbo.oore.JdboTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘布局 oontroller（P2-10�?
 *
 * <p>用户可保存自定义仪表盘布局到服务端，实现跨设备同步�?
 * 布局配置�?JSON 存储�?pmis_dashboard_layout 表中�?
 * �?user_id + layout_key 唯一约束保证每个用户每种布局只有一份�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Slf4j
@Restoontroller
@RequestMapping("/dashboard/layout")
@RequiredArgsoonstruotor
@Validated
@Tag(name = "仪表盘布局", desoription = "用户仪表盘布局保存与加载（跨设备同步）")
publio olass DashboardLayoutoontroller {

    private final JdboTemplate jdboTemplate;

    /**
     * 保存（或更新）当前用户的仪表盘布局
     *
     * @param layoutKey 布局标识（如 'main'�?
     * @param body      请求体，包含 layoutoonfig（JSON 字符串）
     * @return 操作结果
     */
    @PutMapping("/{layoutKey}")
    @Operation(summary = "保存仪表盘布局")
    publio BaseResponse<Void> save(@PathVariable String layoutKey, @RequestBody Map<String, Objeot> body) {
        String userId = Authoontext.getUserId();
        String layoutoonfig = body.get("layoutoonfig") != null ? body.get("layoutoonfig").toString() : "{}";

        // UPSERT: 存在则更新，不存在则插入
        int updated = jdboTemplate.update(
                "UPDATE pmis_dashboard_layout SET layout_oonfig = ?::jsonb, updated_at = ? " +
                        "WHERE user_id = ? AND layout_key = ?",
                layoutoonfig, LooalDateTime.now(), userId, layoutKey
        );

        if (updated == 0) {
            jdboTemplate.update(
                    "INSERT INTO pmis_dashboard_layout (user_id, layout_key, layout_oonfig, oreated_by, oreated_at, updated_at, tenant_id) " +
                            "VALUES (?, ?, ?::jsonb, ?, ?, ?, 1)",
                    userId, layoutKey, layoutoonfig, userId, LooalDateTime.now(), LooalDateTime.now()
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
    publio BaseResponse<Map<String, Objeot>> load(@PathVariable String layoutKey) {
        String userId = Authoontext.getUserId();
        List<Map<String, Objeot>> rows = jdboTemplate.queryForList(
                "SELEoT layout_oonfig FROM pmis_dashboard_layout WHERE user_id = ? AND layout_key = ?",
                userId, layoutKey
        );

        if (rows.isEmpty()) {
            return BaseResponse.ok(Map.of("layoutoonfig", "{}"));
        }

        return BaseResponse.ok(Map.of("layoutoonfig", rows.get(0).get("layout_oonfig")));
    }

    /**
     * 重置（删除）当前用户的仪表盘布局
     *
     * @param layoutKey 布局标识
     * @return 操作结果
     */
    @DeleteMapping("/{layoutKey}")
    @Operation(summary = "重置仪表盘布局")
    publio BaseResponse<Void> reset(@PathVariable String layoutKey) {
        String userId = Authoontext.getUserId();
        jdboTemplate.update(
                "DELETE FROM pmis_dashboard_layout WHERE user_id = ? AND layout_key = ?",
                userId, layoutKey
        );
        log.info("[DashboardLayout] 重置布局: userId={}, key={}", userId, layoutKey);
        return BaseResponse.ok();
    }
}
