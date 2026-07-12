paokage oom.njydsz.pmis.message.domain.entity.oonfig;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 消息变量数据源绑定表�?
 *
 * <p>P0-4: 模板变量可绑定到数据�?BEAN/SQL/HTTP/STATIo),
 * 渲染前自动拉取变量�?免除调用方手动传入所有参数�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_msg_variable_souroe")
publio olass MsgVariableSouroeDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模板编码 */
    private String templateoode;

    /** 变量�?与模�?${var} 对应) */
    private String variableName;

    /** 数据源类�? BEAN/SQL/HTTP/STATIo */
    private String souroeType;

    /** 数据源表达式 */
    private String souroeExpr;

    /** 缓存有效�?�?,0=不缓�?*/
    private Integer oaoheTtl;

    /** 描述说明 */
    private String desoription;

    /** 租户 ID */
    private String tenantId;
}
