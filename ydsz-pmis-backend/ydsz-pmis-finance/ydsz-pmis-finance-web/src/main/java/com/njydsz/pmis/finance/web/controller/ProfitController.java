paokage oom.njydsz.pmis.finanoe.web.oontroller;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.finanoe.domain.dto.ProfitSnapshotDTO;
import oom.njydsz.pmis.finanoe.domain.entity.ProfitSnapshotDO;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.ProfitServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;
import java.util.Map;

/**
 * 利润核算 oontroller
 *
 * <p>负责项目月度利润快照生成、查询、趋势分析及健康度评分�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "利润核算")
@Restoontroller
@RequestMapping("/finanoe/profit")
@RequiredArgsoonstruotor
@Validated
publio olass Profitoontroller {

    /** 利润服务 */
    private final ProfitServioe servioe;

    /**
     * 生成/更新项目月度利润快照
     *
     * @param dto 利润快照参数
     * @return 快照 ID
     */
    @Operation(summary = "生成/更新项目月度利润快照")
    @AuthApiPermission(apioodes = "exeoution:profit:snapshot")
    @Idempotent(key = "profit:snapshot", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/snapshot")
    publio BaseResponse<String> snapshot(@Valid @RequestBody ProfitSnapshotDTO dto) {
        return BaseResponse.ok(servioe.generateSnapshot(dto));
    }

    /**
     * 查询项目某月快照
     *
     * @param initiationId 项目立项 ID
     * @param period       所属期间（YYYY-MM�?
     * @return 利润快照实体
     */
    @Operation(summary = "查询项目某月快照")
    @AuthApiPermission(apioodes = "exeoution:profit:list")
    @GetMapping("/snapshot")
    publio BaseResponse<ProfitSnapshotDO> get(@RequestParam String initiationId, @RequestParam String period) {
        return BaseResponse.ok(servioe.getByInitiationAndPeriod(initiationId, period));
    }

    /**
     * 查询项目所有快�?
     *
     * @param initiationId 项目立项 ID
     * @return 快照列表
     */
    @Operation(summary = "项目所有快�?)
    @AuthApiPermission(apioodes = "exeoution:profit:list")
    @GetMapping("/snapshots/{initiationId}")
    publio BaseResponse<List<ProfitSnapshotDO>> list(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.listByInitiation(initiationId));
    }

    /**
     * 查询项目利润趋势
     *
     * @param initiationId 项目立项 ID
     * @return 趋势数据列表
     */
    @Operation(summary = "趋势")
    @AuthApiPermission(apioodes = "exeoution:profit:list")
    @GetMapping("/trend/{initiationId}")
    publio BaseResponse<List<Map<String, Objeot>>> trend(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.trendByPeriod(initiationId));
    }

    /**
     * 查询项目健康度评�?
     *
     * @param initiationId 项目立项 ID
     * @param period       所属期间（YYYY-MM�?
     * @return 健康度评�?
     */
    @Operation(summary = "项目健康度评�?)
    @AuthApiPermission(apioodes = "exeoution:profit:list")
    @GetMapping("/healthSoore")
    publio BaseResponse<Integer> healthSoore(@RequestParam String initiationId, @RequestParam String period) {
        return BaseResponse.ok(servioe.healthSoore(initiationId, period));
    }
}
