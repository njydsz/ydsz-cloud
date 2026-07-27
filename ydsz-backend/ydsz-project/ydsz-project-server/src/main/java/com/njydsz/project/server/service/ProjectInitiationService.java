package com.njydsz.project.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.dto.ProjectInitiationDTO;
import com.njydsz.project.domain.dto.ProjectInitiationPageQuery;
import com.njydsz.project.domain.vo.ProjectInitiationVO;

/**
 * 项目立项 Application Service。
 *
 * <p>提供项目立项全生命周期管理能力，包括 CRUD、阶段推进、门审等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ProjectInitiationService {

    /**
     * 按 ID 查询项目立项。
     *
     * @param id 主键 ID
     * @return 项目立项 VO
     */
    ProjectInitiationVO getById(String id);

    /**
     * 按项目编号查询。
     *
     * @param projectCode 项目编号
     * @return 项目立项 VO
     */
    ProjectInitiationVO getByCode(String projectCode);

    /**
     * 分页查询项目立项。
     *
     * @param query 分页查询条件
     * @return 分页结果
     */
    IPage<ProjectInitiationVO> page(ProjectInitiationPageQuery query);

    /**
     * 创建项目立项。
     *
     * @param dto 项目立项 DTO
     * @return 主键 ID
     */
    String save(ProjectInitiationDTO dto);

    /**
     * 更新项目立项。
     *
     * @param dto 项目立项 DTO
     * @return 是否成功
     */
    boolean updateById(ProjectInitiationDTO dto);

    /**
     * 删除项目立项（逻辑删除）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);

    /**
     * 推进项目阶段。
     *
     * @param id      项目 ID
     * @param stage   目标阶段
     * @param gate    门审阶段（可选）
     * @return 是否成功
     */
    boolean advanceStage(String id, String stage, String gate);

    /**
     * 查询项目经理负责的项目列表。
     *
     * @param pmId 项目经理 ID
     * @return 项目立项 VO 列表
     */
    List<ProjectInitiationVO> listByPmId(String pmId);
}
