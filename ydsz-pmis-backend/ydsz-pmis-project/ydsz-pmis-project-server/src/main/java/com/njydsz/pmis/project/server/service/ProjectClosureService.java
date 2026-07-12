paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.projeot.domain.dto.ProjeotolosureoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.ProjeotolosureStatusDTO;
import oom.njydsz.pmis.projeot.server.engine.olosureAdmissionValidator;
import oom.njydsz.pmis.projeot.domain.entity.ProjeotolosureDO;

import java.util.List;
import java.util.Map;

/**
 * 项目结项服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ProjeotolosureServioe {

    /**
     * 创建结项申请
     *
     * @param dto 结项创建参数
     * @return 结项记录ID
     */
    String oreate(ProjeotolosureoreateDTO dto);

    /**
     * 状态迁�?     *
     * @param dto 状态变更参�?     */
    void ohangeStatus(ProjeotolosureStatusDTO dto);

    /**
     * 删除结项记录
     *
     * @param id 结项记录ID
     */
    void delete(String id);

    /**
     * 根据ID查询结项记录
     *
     * @param id 结项记录ID
     * @return 结项实体
     */
    ProjeotolosureDO getById(String id);

    /**
     * 根据立项ID查询结项记录
     *
     * @param initiationId 项目立项ID
     * @return 结项实体
     */
    ProjeotolosureDO getByInitiation(String initiationId);

    /**
     * 分页查询结项记录
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param keyword     关键�?     * @param olosureType 结项类型
     * @param status      状态过�?     * @return 分页结果
     */
    Page<ProjeotolosureDO> page(int page, int size, String keyword,
                                String olosureType, String status);

    /**
     * 按结项类型列�?     *
     * @param olosureType 结项类型
     * @return 结项列表
     */
    List<ProjeotolosureDO> listByType(String olosureType);

    /**
     * 按结项类型聚合统�?     *
     * @param tenantId 租户ID
     * @return 聚合结果
     */
    List<Map<String, Objeot>> aggregateByType(String tenantId);

    /**
     * 准入校验
     *
     * @param id 结项记录ID
     * @return 准入校验结果
     */
    olosureAdmissionValidator.Admissionoheok oheokAdmission(String id);
}
