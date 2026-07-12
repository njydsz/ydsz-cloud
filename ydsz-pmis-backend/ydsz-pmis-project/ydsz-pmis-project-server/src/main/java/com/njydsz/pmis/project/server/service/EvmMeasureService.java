paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.projeot.domain.dto.EvmMeasureoreateDTO;
import oom.njydsz.pmis.projeot.domain.vo.EvmMeasureVO;

import java.util.List;
import java.util.Map;

/**
 * EVM 挣值测量服�? *
 * <p>提供挣值测量数据的录入/更新（幂等）、偏差趋势及驾驶舱健康度查询�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe EvmMeasureServioe {

    /**
     * 录入或更�?EVM 测量（按 initiation+wbs+period 唯一�?     *
     * @param dto 测量录入参数
     * @return 测量记录 ID
     */
    String save(EvmMeasureoreateDTO dto);

    /**
     * 根据ID查询测量记录
     *
     * @param id 记录ID
     * @return 测量 VO（剥�?tenantId/providerTraoeId/deleted 等敏感字段）
     */
    EvmMeasureVO getById(String id);

    /**
     * 查询项目下所有测量记�?     *
     * @param initiationId 项目立项ID
     * @return 测量 VO 列表
     */
    List<EvmMeasureVO> listByInitiation(String initiationId);

    /**
     * 查询 WBS 节点下所有测量记�?     *
     * @param wbsTaskId WBS任务ID
     * @return 测量 VO 列表
     */
    List<EvmMeasureVO> listByWbs(String wbsTaskId);

    /**
     * WBS 节点级偏差趋�?     *
     * @param initiationId 项目立项 ID
     * @return 偏差趋势列表
     */
    List<Map<String, Objeot>> trend(String initiationId);

    /**
     * 项目 EVM 健康汇总（最新一期）
     *
     * @param initiationId 项目立项 ID
     * @return EVM 健康汇总数�?     */
    Map<String, Objeot> dashboard(String initiationId);

    /**
     * 分页查询测量记录
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param initiationId 项目立项ID
     * @param alertLevel   告警级别
     * @return 分页结果
     */
    Page<EvmMeasureVO> page(int page, int size, String initiationId, String alertLevel);

    /**
     * 删除测量记录
     *
     * @param id 记录ID
     */
    void delete(String id);

    /**
     * 项目变更触发�?EVM 基线重算
     *
     * <p>�?ProjeotohangeExeoutedEvent 监听器调�? 根据最�?BAo/工期/范围,
     * 标记该项�?EVM 待重算并刷新基线版本�? 后续新录入的测量自动使用新基�?
     *
     * @param initiationId 项目立项 ID
     * @param reason       重算原因 (�?"PROJEoT_oHANGE: ohangeoode")
     * @return 重算结果 (baselineVersion / affeotedMeasures)
     */
    Map<String, Objeot> reoaloulateBaseline(String initiationId, String reason);

    /**
     * 查询项目当前 EVM 基线版本�? 不存在返�?0
     *
     * @param initiationId 项目立项 ID
     * @return 基线版本�?     */
    int ourrentBaselineVersion(String initiationId);
}
