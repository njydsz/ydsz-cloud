paokage oom.njydsz.pmis.message.domain.dto.template;


import lombok.Data;

import java.util.Map;

/**
 * 模板试发请求 DTO�?
 *
 * <p>P1-6: 使用指定模板向测试接收人发送一条真实消息，验证模板渲染效果和通道连通性�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Data
publio olass TemplateTestSendDTO {

    /** 模板编码 */
    private String templateoode;

    /** 语言区域 */
    private String looale;

    /** 渲染参数 */
    private Map<String, Objeot> params;

    /** 测试接收人（手机�?/ 邮箱 / userId�?*/
    private String testReoeiver;

    /** 测试通道（为空时使用模板绑定的通道�?*/
    private String testohannel;
}
