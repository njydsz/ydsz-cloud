package com.njydsz.pmis.common.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分片上传配置属性
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
@ConfigurationProperties(prefix = "remi.file.upload")
public class FileUploadProperties {

    /**
     * 是否启用分片 MD5 校验
     * <p>启用后，每次分片上传时会计算并保存分片的 MD5，
     * 合并完成时会校验整个文件的 MD5。
     * 默认 false。
     */
    private boolean chunkMd5Check = false;
}
