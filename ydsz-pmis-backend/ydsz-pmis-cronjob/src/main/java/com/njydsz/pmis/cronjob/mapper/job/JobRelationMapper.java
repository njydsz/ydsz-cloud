package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.job.JobRelationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务依赖关系 Mapper（P4 DAG 工作流）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobRelationMapper extends BaseMapper<JobRelationDO> {

    /**
     * 查询指定任务的所有后继依赖边（作为前置任务的子任务列表）。
     *
     * @param parentJobId 前置任务 ID
     * @return 依赖边列表
     */
    @Select("SELECT id, parent_job_id, child_job_id, fail_strategy, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_relation "
            + "WHERE parent_job_id = #{parentJobId} AND deleted = 0")
    List<JobRelationDO> selectByParentJobId(@Param("parentJobId") String parentJobId);

    /**
     * 查询指定任务的所有前置依赖边（作为后继任务的父任务列表）。
     *
     * @param childJobId 后继任务 ID
     * @return 依赖边列表
     */
    @Select("SELECT id, parent_job_id, child_job_id, fail_strategy, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_relation "
            + "WHERE child_job_id = #{childJobId} AND deleted = 0")
    List<JobRelationDO> selectByChildJobId(@Param("childJobId") String childJobId);

    /**
     * 查询所有依赖边（DAG 解析时使用，加载全图做拓扑排序和环检测）。
     *
     * @return 所有未删除的依赖边
     */
    @Select("SELECT id, parent_job_id, child_job_id, fail_strategy, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_relation WHERE deleted = 0")
    List<JobRelationDO> selectAllRelations();
}
