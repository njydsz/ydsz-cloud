package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.message.dto.template.TemplateAuditDTO;
import com.njydsz.pmis.message.dto.template.TemplateCreateDTO;
import com.njydsz.pmis.message.dto.template.TemplateQueryDTO;
import com.njydsz.pmis.message.entity.template.MsgTemplateDO;

/**
 * 消息模板服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TemplateService {

    /**
     * 创建模板
     *
     * @param dto 模板创建参数
     * @return 已创建的模板
     */
    MsgTemplateDO create(TemplateCreateDTO dto);

    /**
     * 更新模板
     *
     * @param id  模板 ID
     * @param dto 模板更新参数
     * @return 更新后的模板
     */
    MsgTemplateDO update(String id, TemplateCreateDTO dto);

    /**
     * 删除模板(逻辑删除)
     *
     * @param id 模板 ID
     */
    void delete(String id);

    /**
     * 根据 ID 查询模板
     *
     * @param id 模板 ID
     * @return 模板实体
     */
    MsgTemplateDO getById(String id);

    /**
     * 分页查询模板
     *
     * @param query 查询参数
     * @return 分页结果
     */
    Page<MsgTemplateDO> page(TemplateQueryDTO query);

    /**
     * 按编码 + 通道 + 语言加载模板(locale 为空时回退默认 zh-CN)
     *
     * @param templateCode 模板编码
     * @param channel      通道
     * @param locale       语言区域(可为空)
     * @param tenantId     租户 ID
     * @return 模板实体
     */
    MsgTemplateDO loadByCodeAndChannel(String templateCode, String channel, String locale, String tenantId);

    /**
     * 审核模板
     *
     * @param id  模板 ID
     * @param dto 审核参数
     */
    void audit(String id, TemplateAuditDTO dto);
}
