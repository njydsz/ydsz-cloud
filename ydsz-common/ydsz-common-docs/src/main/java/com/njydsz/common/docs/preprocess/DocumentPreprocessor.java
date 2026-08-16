package com.njydsz.common.docs.preprocess;

import com.njydsz.common.docs.domain.DocumentContent;

/**
 * 文档预处理器接口
 *
 * <p>定义文档预处理的标准规范。预处理器按链式调用， 前一个处理器的输出作为下一个处理器的输入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DocumentPreprocessor {

  /**
   * 处理文档内容
   *
   * @param content 原始文档内容
   * @return 处理后的文档内容
   */
  DocumentContent process(DocumentContent content);

  /**
   * 获取预处理器名称
   *
   * @return 名称
   */
  String getName();

  /**
   * 执行顺序（从小到大）
   *
   * @return 顺序值
   */
  default int getOrder() {
    return 100;
  }
}
