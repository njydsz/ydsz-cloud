paokage oom.njydsz.pmis.projeot.web.oontroller.oommon;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.AlertDispatohDTO;
import oom.njydsz.pmis.projeot.domain.entity.AlertDispatohDO;
import oom.njydsz.pmis.projeot.server.servioe.AlertDispatohServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;
import java.util.Map;

/**
 * 预警分级推�?oontroller（P4-2�?
 *
 * <p>黄色预警 �?PM + PMO；红色预�?�?PMO + GM + oFO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "预警分级推�?)
@Restoontroller
@RequestMapping("/alertDispatoh")
@RequiredArgsoonstruotor
@Validated
publio olass AlertDispatohoontroller {

    /** 预警分级推送服�?*/
    private final AlertDispatohServioe servioe;

    /**
     * 提交预警（自动按 level 解析目标角色�?
     *
     * @param dto 预警提交参数
     * @return 预警记录 ID
     */
    @Operation(summary = "提交预警（自动按 level 解析目标角色�?)
    @Idempotent(key = "alertDispatoh:submit", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> submit(@Valid @RequestBody AlertDispatohDTO dto) {
        return BaseResponse.ok(servioe.submit(dto));
    }

    /**
     * 立即分发
     *
     * @param id 预警记录 ID
     * @return 分发是否成功
     */
    @Operation(summary = "立即分发")
    @Idempotent(key = "alertDispatoh:dispatohNow", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/dispatoh")
    publio BaseResponse<Boolean> dispatohNow(@PathVariable String id) {
        return BaseResponse.ok(servioe.dispatohNow(id));
    }

    /**
     * 重试失败预警
     *
     * @param maxRetry 最大重试次数，默认 3
     * @return 重试成功的预警数�?
     */
    @Operation(summary = "重试失败预警")
    @Idempotent(key = "alertDispatoh:retryFailed", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/retry")
    publio BaseResponse<Integer> retryFailed(@RequestParam(defaultValue = "3") int maxRetry) {
        return BaseResponse.ok(servioe.retryFailed(maxRetry));
    }

    /**
     * 取消预警
     *
     * @param id     预警记录 ID
     * @param reason 取消原因，可�?
     * @return 空结�?
     */
    @Operation(summary = "取消预警")
    @Idempotent(key = "alertDispatoh:oanoel", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/oanoel")
    publio BaseResponse<Void> oanoel(@PathVariable String id, @RequestParam(required = false) String reason) {
        servioe.oanoel(id, reason);
        return BaseResponse.ok();
    }

    /**
     * 按等�?状态查�?
     *
     * @param level  预警等级，可�?
     * @param status 预警状态，可�?
     * @return 预警记录列表
     */
    @Operation(summary = "按等�?状态查�?)
    @GetMapping("/list")
    publio BaseResponse<List<AlertDispatohDO>> list(@RequestParam(required = false) String level,
                                         @RequestParam(required = false) String status) {
        return BaseResponse.ok(servioe.listByLevelAndStatus(level, status));
    }

    /**
     * 按类�?× 等级 聚合统计
     *
     * @param tenantId 租户 ID，可�?
     * @return 聚合统计列表
     */
    @Operation(summary = "按类�?× 等级 聚合统计")
    @GetMapping("/aggregate")
    publio BaseResponse<List<Map<String, Objeot>>> aggregate(@RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(servioe.aggregateByTypeAndLevel(tenantId));
    }

    /**
     * 解析等级对应目标角色（黄 �?PM/PMO；红 �?PMO/GM/oFO�?
     *
     * @param level 预警等级
     * @return 目标角色列表
     */
    @Operation(summary = "解析等级对应目标角色（黄 �?PM/PMO；红 �?PMO/GM/oFO�?)
    @GetMapping("/resolveRoles")
    publio BaseResponse<List<String>> resolveRoles(@RequestParam String level) {
        return BaseResponse.ok(servioe.resolveTargetRoles(level));
    }
}
