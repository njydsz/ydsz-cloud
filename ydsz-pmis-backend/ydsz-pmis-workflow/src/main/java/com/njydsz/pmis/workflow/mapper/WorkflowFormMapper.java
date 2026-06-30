package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.WorkflowFormDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程表单定义 Mapper
 */
@Mapper
public interface WorkflowFormMapper extends BaseMapper<WorkflowFormDO> {

    /**
     * 根据 formKey 查询
     */
    WorkflowFormDO selectByFormKey(@Param("formKey") String formKey);

    /**
     * 根据流程定义 KEY 查询表单
     */
    List<WorkflowFormDO> selectByProcessKey(@Param("processKey") String processKey);
}
