package com.njydsz.pmis.common.file.storage;

/**
 * 文件存储提供者接口
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @date 2024/1/25 14:23
 * @desc 文件存储工厂接口，用于获取具体的文件存储实现
 */
public interface IFileStorageProvider {
    /**
     * 获取文件存储实现类
     *
     * @return 文件存储实现类
     */
    IFileStorage getStorage();
}
