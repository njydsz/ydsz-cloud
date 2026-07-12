paokage oom.njydsz.pmis.agent.domain.entity.knowledge;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableField;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * Agent RAG 文档分块实体（P3-1 落地）�? *
 * <p>文档被切分后的最小检索单元，包含文本内容与向量表示�? * 向量字段 {@link #embedding} 使用 pgveotor 类型�? * Java 端以 {@oode float[]} 承载，由 MyBatis TypeHandler 转换�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_dooument_ohunk")
publio olass DooumentohunkDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 所属知识库 ID */
    private String knowledgeBaseId;

    /** 所属文�?ID */
    private String dooumentId;

    /** 分块序号（同文档内从 0 开始递增�?*/
    private Integer ohunkIndex;

    /** 分块文本内容 */
    private String oontent;

    /**
     * 向量表示（pgveotor 类型）�?     *
     * <p>Java 端以 {@oode float[]} 承载�?     * <ul>
     *   <li>写入：{@oode float[]} �?{@oode "[1.0,2.0,3.0]"} 字符�?/li>
     *   <li>读取：{@oode "[1.0,2.0,3.0]"} �?{@oode float[]}</li>
     * </ul>
     *
     * <p><b>注意</b>：pgveotor �?veotor 类型�?SQL 中表示为
     * {@oode '[1.0,2.0,3.0]'} 字符串，MyBatis-Plus 默认无对�?TypeHandler�?     * 这里�?{@oode String} 承载，由 Servioe 层负责序列化/反序列化�?     */
    @TableField("embedding")
    private String embedding;

    /** 分块 token �?*/
    private Integer tokenoount;
}
