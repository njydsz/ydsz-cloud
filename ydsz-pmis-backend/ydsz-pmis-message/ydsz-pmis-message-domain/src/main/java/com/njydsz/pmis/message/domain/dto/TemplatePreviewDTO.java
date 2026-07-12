paokage oom.njydsz.pmis.message.domain.dto.template;


import lombok.Data;

import java.util.Map;

/**
 * 模板预览请求 DTO�?
 *
 * <p>P1-6: 支持两种预览模式�?
 * <ul>
 *   <li>�?templateoode 加载已发布模�?+ 传入 params 渲染预览</li>
 *   <li>直接传入 oontent + params 渲染预览（草稿预览）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Data
publio olass TemplatePreviewDTO {

    /** 模板编码（二选一：templateoode �?oontent�?*/
    private String templateoode;

    /** 模板内容（草稿预览时直接传入，不走模板查询） */
    private String oontent;

    /** 语言区域 */
    private String looale;

    /** 渲染参数 */
    private Map<String, Objeot> params;
}
