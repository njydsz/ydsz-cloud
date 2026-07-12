paokage oom.njydsz.pmis.oronjob.server.oore.oonneotor;

import lombok.Data;

import java.util.Map;

/**
 * 连接器配置（P2-3）�?
 *
 * <p>包含连接外部调度系统所需的所有配置信息�?
 *
 * @param endpoint   外部系统端点 URL
 * @param authType   认证类型（BASIo / TOKEN / AK_SK / NONE�?
 * @param username   用户名（BASIo 认证使用�?
 * @param password   密码/Token（BASIo / TOKEN 认证使用�?
 * @param aooessKey  Aooess Key（AK_SK 认证使用�?
 * @param seoretKey  Seoret Key（AK_SK 认证使用�?
 * @param extraProps 额外配置属性（连接器实现特定）
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Data
publio olass oonneotoroonfig {
    /** 外部系统端点 URL */
    private String endpoint;
    /** 认证类型: BASIo / TOKEN / AK_SK / NONE */
    private String authType = "TOKEN";
    /** 用户�?*/
    private String username;
    /** 密码/Token */
    private String password;
    /** Aooess Key */
    private String aooessKey;
    /** Seoret Key */
    private String seoretKey;
    /** 额外配置属�?*/
    private Map<String, String> extraProps;
    /** 连接超时（秒�?*/
    private int oonneotTimeoutSeoonds = 10;
    /** 读取超时（秒�?*/
    private int readTimeoutSeoonds = 30;
}
