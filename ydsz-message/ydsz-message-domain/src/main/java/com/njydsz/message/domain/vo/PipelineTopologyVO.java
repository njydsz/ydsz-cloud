package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 发送管线拓扑视图对象（VO）。
 *
 * <p>描述 {@link com.njydsz.message.server.service.chain.SendPipeline} 在特定模板下的 Handler 链结构与执行顺序，供运维
 * 拓扑看板渲染。
 *
 * <p>每个 {@link HandlerNode} 对应一个 {@link com.njydsz.message.server.service.chain.SendHandler}， {@code order}
 * 字段决定执行顺序（升序）。
 *
 * @author ydsz-team
 * @since 26.09.10
 * @see com.njydsz.message.server.service.chain.SendPipelineFacade
 */
@Data
@Builder
public class PipelineTopologyVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 管线模板标识（full / template / simple / batch / callback） */
  private String template;

  /** 该模板包含的 Handler 节点列表（已按 order 升序排列） */
  private List<HandlerNode> handlers;

  /** 全局 Handler 总数量（不区分模板） */
  private int totalHandlerCount;

  /** Handler 节点描述。 */
  @Data
  @Builder
  public static class HandlerNode implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** Handler 名称（类.getSimpleName） */
    private String name;

    /** Handler 类全限定名 */
    private String className;

    /** 执行顺序（升序，值越小越先执行） */
    private int order;

    /** Handler 中文描述 */
    private String description;
  }
}
