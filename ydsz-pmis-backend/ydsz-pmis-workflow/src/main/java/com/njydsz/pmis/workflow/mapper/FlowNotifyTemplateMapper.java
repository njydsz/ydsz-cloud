package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowNotifyTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * P1-2: 工作流通知模板 Mapper
 *
 * <p>P1-5: 新增按 locale 查询方法，支持多语言模板匹配。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Mapper
public interface FlowNotifyTemplateMapper extends BaseMapper<FlowNotifyTemplateDO> {

    /**
     * 按租户 + 模板编码 + 通道查询启用的模板（向后兼容：不指定 locale）
     */
    @Select("SELECT * FROM pmis_flow_notify_template " +
            "WHERE tenant_id = #{tenantId} AND template_code = #{templateCode} " +
            "AND channel = #{channel} AND enabled = 1 AND deleted = 0 LIMIT 1")
    FlowNotifyTemplateDO selectEnabled(@Param("tenantId") String tenantId,
                                       @Param("templateCode") String templateCode,
                                       @Param("channel") String channel);

    /**
     * P1-5: 按租户 + 模板编码 + 通道 + locale 精确查询启用的模板。
     *
     * @param tenantId     租户 ID
     * @param templateCode 模板编码
     * @param channel      通道
     * @param locale       语言区域（如 zh_CN / en_US）
     * @return 模板 DO，不存在返回 null
     */
    @Select("SELECT * FROM pmis_flow_notify_template " +
            "WHERE tenant_id = #{tenantId} AND template_code = #{templateCode} " +
            "AND channel = #{channel} AND locale = #{locale} " +
            "AND enabled = 1 AND deleted = 0 LIMIT 1")
    FlowNotifyTemplateDO selectEnabledByLocale(@Param("tenantId") String tenantId,
                                                @Param("templateCode") String templateCode,
                                                @Param("channel") String channel,
                                                @Param("locale") String locale);
}
