package com.njydsz.common.excel.core.reader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

import com.njydsz.common.excel.core.metadata.ReadMetadata;

/**
 * 输入源检测器 - 负责检测输入源类型和格式
 *
 * <p>从ExcelReader中提取的职责：
 * <ul>
 *   <li>根据配置获取输入流</li>
 *   <li>检测输入流是否为XLSX格式(通过魔数判断)</li>
 * </ul>
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 * @see ExcelReader
 * @since 1.0.0
 */
public class InputSourceDetector {

    /** 读取配置元数据 */
    private final ReadMetadata metadata;

    /**
     * 构造输入源检测器
     *
     * @param metadata 读取配置元数据
     */
    public InputSourceDetector(ReadMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * 获取输入流
     *
     * <p>输入流优先级: InputStream > FilePath > File。
     * 根据配置的来源创建合适的输入流。</p>
     *
     * @return 输入流对象
     * @throws IOException 文件未找到时抛出
     */
    public InputStream getInputStream() throws IOException {
        InputStream is = metadata.getInputStream();
        if (is != null) {
            return is;
        }

        String filePath = metadata.getFilePath();
        if (filePath != null && !filePath.isEmpty()) {
            return new FileInputStream(filePath);
        }

        if (metadata.getFile() != null) {
            return new FileInputStream(metadata.getFile());
        }

        return null;
    }

    /**
     * 检测输入流是否为XLSX格式
     *
     * <p>通过读取文件头魔数(PK\x03\x04)判断是否为ZIP格式(XLSX本质是ZIP)。
     * 如果输入流不支持mark/reset，会自动包装为SequenceInputStream。</p>
     *
     * @param is 输入流
     * @return true表示XLSX格式，false表示XLS格式
     */
    public boolean detectXlsxFormat(InputStream is) {
        try {
            boolean canMark = is.markSupported();
            if (canMark) {
                is.mark(8);
            }
            byte[] header = new byte[4];
            int read = is.read(header);
            if (canMark) {
                is.reset();
            } else {
                is = new SequenceInputStream(new ByteArrayInputStream(header), is);
                metadata.setInputStream(is);
            }
            if (read < 4) {
                return false;
            }
            return (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04);
        } catch (IOException e) {
            return false;
        }
    }
}
