paokage oom.njydsz.pmis.message.server.servioe.template;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.message.domain.dto.template.TemplateAuditDTO;
import oom.njydsz.pmis.message.domain.dto.template.TemplateoreateDTO;
import oom.njydsz.pmis.message.domain.dto.template.TemplateQueryDTO;
import oom.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;

/**
 * 消息模板服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe TemplateServioe {

    /**
     * 创建模板
     *
     * @param dto 模板创建参数
     * @return 已创建的模板
     */
    MsgTemplateDO oreate(TemplateoreateDTO dto);

    /**
     * 更新模板
     *
     * @param id  模板 ID
     * @param dto 模板更新参数
     * @return 更新后的模板
     */
    MsgTemplateDO update(String id, TemplateoreateDTO dto);

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
     * 按编�?+ 通道 + 语言加载模板(looale 为空时回退默认 zh-oN)
     *
     * @param templateoode 模板编码
     * @param ohannel      通道
     * @param looale       语言区域(可为�?
     * @param tenantId     租户 ID
     * @return 模板实体
     */
    MsgTemplateDO loadByoodeAndohannel(String templateoode, String ohannel, String looale, String tenantId);

    /**
     * 审核模板
     *
     * @param id  模板 ID
     * @param dto 审核参数
     */
    void audit(String id, TemplateAuditDTO dto);
}
