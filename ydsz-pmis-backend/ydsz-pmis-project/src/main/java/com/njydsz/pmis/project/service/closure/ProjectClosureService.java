package com.njydsz.pmis.project.service.closure;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.closure.ProjectClosureCreateDTO;
import com.njydsz.pmis.project.dto.closure.ProjectClosureStatusDTO;
import com.njydsz.pmis.project.engine.ClosureAdmissionValidator;
import com.njydsz.pmis.project.entity.closure.ProjectClosureDO;

import java.util.List;
import java.util.Map;

/**
 * 项目结项服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ProjectClosureService {

    /**
     * 创建结项申请
     *
     * @param dto 结项创建参数
     * @return 结项记录ID
     */
    String create(ProjectClosureCreateDTO dto);

    /**
     * 状态迁移
     *
     * @param dto 状态变更参数
     */
    void changeStatus(ProjectClosureStatusDTO dto);

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
    ProjectClosureDO getById(String id);

    /**
     * 根据立项ID查询结项记录
     *
     * @param initiationId 项目立项ID
     * @return 结项实体
     */
    ProjectClosureDO getByInitiation(String initiationId);

    /**
     * 分页查询结项记录
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param keyword     关键词
     * @param closureType 结项类型
     * @param status      状态过滤
     * @return 分页结果
     */
    Page<ProjectClosureDO> page(int page, int size, String keyword,
                                String closureType, String status);

    /**
     * 按结项类型列出
     *
     * @param closureType 结项类型
     * @return 结项列表
     */
    List<ProjectClosureDO> listByType(String closureType);

    /**
     * 按结项类型聚合统计
     *
     * @param tenantId 租户ID
     * @return 聚合结果
     */
    List<Map<String, Object>> aggregateByType(String tenantId);

    /**
     * 准入校验
     *
     * @param id 结项记录ID
     * @return 准入校验结果
     */
    ClosureAdmissionValidator.AdmissionCheck checkAdmission(String id);
}
