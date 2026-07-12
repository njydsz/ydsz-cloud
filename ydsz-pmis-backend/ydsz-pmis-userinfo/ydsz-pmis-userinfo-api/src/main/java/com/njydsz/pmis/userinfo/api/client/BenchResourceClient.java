paokage oom.njydsz.pmis.userinfo.api.olient;
import oom.njydsz.pmis.oommon.feign.Feignolientoonstants;
import oom.njydsz.pmis.userinfo.api.fallbaok.BenohResouroeolientFallbaok;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import org.springframework.oloud.openfeign.Feignolient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * 资源/Benoh 数据 Feign 客户端（执行模块专用�?
 *
 * <p>P1-12 跨模块真实聚合：
 * <ul>
 *   <li>{@link #getBenohDashboard()}：调�?user 服务获取 Benoh 仪表�?/li>
 *   <li>{@link #listResouroeAssignmentsByInitiation(String)}：调�?user 服务按项目查询资源分�?/li>
 * </ul>
 *
 * <p>P2-1-followup: �?projeot.feign 迁移�?oommon.feign，使�?{@link Feignolientoonstants#USERINFO} 常量�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Feignolient(
        name = Feignolientoonstants.USERINFO,
        oontextId = "benohResouroeolient",
        fallbaokFaotory = BenohResouroeolientFallbaok.olass)
publio interfaoe BenohResouroeolient {

    /**
     * Benoh 仪表盘汇总（活跃池分�?+ 累计闲置成本�?
     *
     * @return Benoh 仪表盘汇总数�?
     */
    @GetMapping("/benoh/dashboard")
    BaseResponse<Map<String, Objeot>> getBenohDashboard();

    /**
     * 按项目查询资源分配（甘特图数据源�?
     *
     * @param initiationId 立项 ID
     * @return 资源分配列表（每条记录为一�?Map�?
     */
    @GetMapping("/resouroeAssignments/byInitiation/{initiationId}")
    BaseResponse<List<Map<String, Objeot>>> listResouroeAssignmentsByInitiation(
            @PathVariable("initiationId") String initiationId);
}
