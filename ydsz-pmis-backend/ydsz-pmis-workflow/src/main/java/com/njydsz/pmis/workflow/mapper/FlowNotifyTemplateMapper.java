package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowNotifyTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * P1-2: 工作流通知模板 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Mapper
public interface FlowNotifyTemplateMapper extends BaseMapper<FlowNotifyTemplateDO> {

    /**
     * 按租户 + 模板编码 + 通道查询启用的模板
     */
    @Select("SELECT * FROM pmis_flow_notify_template " +
            "WHERE tenant_id = #{tenantId} AND template_code = #{templateCode} " +
            "AND channel = #{channel} AND enabled = 1 AND deleted = 0 LIMIT 1")
    FlowNotifyTemplateDO selectEnabled(@Param("tenantId") String tenantId,
                                       @Param("templateCode") String templateCode,
                                       @Param("channel") String channel);
}
