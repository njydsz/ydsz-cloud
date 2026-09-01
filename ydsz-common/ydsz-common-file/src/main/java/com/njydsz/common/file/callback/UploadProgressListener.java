package com.njydsz.common.file.callback;

/**
 * 文件上传进度回调接口
 *
 * <p>监听上传过程中的关键节点，使业务层能够获取实时进度（用于进度条、弱网重试等场景）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface UploadProgressListener {

  /**
   * 上传开始前回调
   *
   * @param totalBytes 文件总字节数
   */
  void onStart(long totalBytes);

  /**
   * 上传进行中回调（每读取一个缓冲区触发一次）
   *
   * @param uploadedBytes 已写入字节数
   * @param totalBytes 文件总字节数
   */
  void onProgress(long uploadedBytes, long totalBytes);

  /**
   * 上传成功回调
   *
   * @param objectName 存储键
   */
  void onSuccess(String objectName);

  /**
   * 上传失败回调
   *
   * @param objectName 存储键
   * @param cause 异常原因
   */
  void onFailure(String objectName, Throwable cause);
}
