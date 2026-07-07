package com.njydsz.pmis.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.system.entity.MessageTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息模板 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MessageTemplateMapper extends BaseMapper<MessageTemplateDO> {

    /**
     * 按编码 + 通道 + 租户查询模板（用于发送时加载模板）
     *
     * @param code     模板编码
     * @param channel  通道（大写）
     * @param tenantId 租户 ID
     * @return 模板实体，不存在返回 null
     */
    MessageTemplateDO selectByCodeAndChannel(@Param("code") String code,
                                              @Param("channel") String channel,
                                              @Param("tenantId") String tenantId);

    /**
     * 按通道 + 租户列出全部启用模板
     *
     * @param channel  通道（大写）
     * @param tenantId 租户 ID
     * @return 模板列表
     */
    List<MessageTemplateDO> selectByChannel(@Param("channel") String channel,
                                            @Param("tenantId") String tenantId);
}
