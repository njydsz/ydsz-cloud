package com.njydsz.common.excel.core.reader.hssf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OLE2 复合文档解析器 — 读取 Microsoft Compound Document Format (CDF) 容器。
 *
 * <p>XLS（BIFF8）文件是 OLE2 复合文档，内部包含多个"流"（Stream），
 * 关键流为 {@code Workbook}（或 {@code BOOK}）。本解析器挑出所有流名 + 内容字节，
 * 供 {@link HssfCompatibleReader} 进一步解析 BIFF 记录。
 *
 * <h3>OLE2 结构简述</h3>
 *
 * <ul>
 *   <li>512 字节头：包含扇区大小（通常 512）、迷你扇区大小（64）、FAT 扇区数等</li>
 *   <li>FAT（File Allocation Table）：扇区号链，定位流的存储位置</li>
 *   <li>Mini FAT：迷你流（&lt; 4096 字节）的分配表</li>
 *   <li>Directory Entry：目录条目，包含流名、大小、起始扇区</li>
 * </ul>
 *
 * <p>参考：[MS-CFB] Microsoft Compound File Binary Format Specification
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class Ole2CompoundDocument implements AutoCloseable {

  /** OLE2 文件魔数 */
  private static final long MAGIC = 0xE11AB1A1E011E000L;

  /** 扇区大小（标准 = 512 字节；大容量文件 = 4096） */
  private final int sectorSize;

  /** 迷你扇区大小（标准 = 64 字节） */
  private final int miniSectorSize = 64;

  /**  FAT 扇区号数组 */
  private final int[] fat;

  /** 迷你 FAT 扇区号数组 */
  private final int[] miniFat;

  /** 目录条目列表 */
  private final List<DirectoryEntry> entries;

  /** 所有原始字节 */
  private final byte[] data;

  /** 流对象 — 名 → 字节内容 */
  public static final class StreamEntry {
    public final String name;
    public final byte[] data;

    StreamEntry(String name, byte[] data) {
      this.name = name;
      this.data = data;
    }
  }

  private Ole2CompoundDocument(byte[] data, int sectorSize, int[] fat, int[] miniFat,
      List<DirectoryEntry> entries) {
    this.data = data;
    this.sectorSize = sectorSize;
    this.fat = fat;
    this.miniFat = miniFat;
    this.entries = entries;
  }

  /**
   * 从输入流解析 OLE2 复合文档。
   *
   * @param is XLS 文件输入流
   * @return 解析后的 OLE2 文档实例
   * @throws IOException 读取或格式错误
   */
  public static Ole2CompoundDocument parse(InputStream is) throws IOException {
    ReadableByteChannel channel = Channels.newChannel(is);
    ByteBuffer buffer = ByteBuffer.allocate(4096);
    int totalRead = 0;
    byte[] tmp = new byte[8192];
    int n;
    // 读取全部字节（对于典型 XLS 文件 &lt; 10MB）
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(64 * 1024);
    while ((n = channel.read(buffer)) != -1) {
      buffer.flip();
      byte[] arr = new byte[n];
      buffer.get(arr);
      bos.write(arr);
      buffer.clear();
      totalRead += n;
    }
    byte[] data = bos.toByteArray();
    if (data.length < 512) {
      throw new IOException("Invalid OLE2 file: too short");
    }

    ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

    long magic = header.getLong(0);
    if (magic != MAGIC) {
      throw new IOException("Invalid OLE2 magic: 0x" + Long.toHexString(magic));
    }

    int sectorShift = header.getShort(30) & 0xFFFF;
    int sectorSize = 1 << sectorShift; // 通常 512（sectorShift=9）

    int miniSectorShift = header.getShort(32) & 0xFFFF; // 通常 6（64 字节）

    int csectFat = header.getInt(44); // FAT 扇区数

    int firstDirSectorSid = header.getInt(48); // 首个目录扇区

    int firstMiniFatSectorSid = header.getInt(60); // 首个迷你 FAT 扇区
    int cSectMiniFat = header.getInt(64); // 迷你 FAT 扇区数

    // 解析 FAT（位于头的 FAT 扇区列表中，前 109 个在 header 中）
    int[] fat = new int[data.length / sectorSize];
    int[] fatSectorList = new int[109];
    for (int i = 0; i < 109; i++) {
      fatSectorList[i] = header.getInt(76 + i * 4);
    }

    // 读取 FAT 扇区
    int fatIdx = 0;
    for (int i = 0; i < csectFat && i < 109; i++) {
      int secNum = fatSectorList[i];
      if (secNum < 0) {
        continue;
      }
      int offset = (secNum + 1) * sectorSize;
      if (offset + sectorSize > data.length) {
        continue;
      }
      ByteBuffer secBuf = ByteBuffer.wrap(data, offset, sectorSize).order(ByteOrder.LITTLE_ENDIAN);
      for (int j = 0; j < sectorSize / 4 && fatIdx < fat.length; j++) {
        fat[fatIdx++] = secBuf.getInt();
      }
    }

    // 解析目录条目（首个扇区链）
    List<DirectoryEntry> entries = new ArrayList<>(8);
    int dirSid = firstDirSectorSid;
    while (dirSid >= 0 && dirSid != 0xFFFFFFFE) {
      int dirOffset = (dirSid + 1) * sectorSize;
      if (dirOffset + sectorSize > data.length) {
        break;
      }
      try (DirectoryStreamReader dsr =
          new DirectoryStreamReader(data, dirOffset, sectorSize, 4)) {
        for (int e = 0; e < 4; e++) {
          DirectoryEntry entry = DirectoryEntry.read(dsr);
          if (entry == null || entry.name.isEmpty()) {
            break;
          }
          entries.add(entry);
        }
      }
      dirSid = fat[dirSid];
    }

    // 解析迷你 FAT
    int[] miniFat;
    if (cSectMiniFat > 0 && firstMiniFatSectorSid >= 0) {
      miniFat = readChain(fat, firstMiniFatSectorSid, data, sectorSize);
    } else {
      miniFat = new int[0];
    }

    return new Ole2CompoundDocument(data, sectorSize, fat, miniFat, entries);
  }

  /**
   * 获取名为 {@code name} 的流的完整字节内容。
   *
   * @param name 流名（如 "Workbook"）
   * @return 字节数组，未找到返回 null
   */
  public StreamEntry getStream(String name) {
    for (DirectoryEntry entry : entries) {
      if (entry.name.equals(name)) {
        byte[] content = readEntryContent(entry);
        if (content != null) {
          return new StreamEntry(entry.name, content);
        }
      }
    }
    return null;
  }

  /**
   * 获取所有流条目。
   *
   * @return 流名 → 内容的映射
   */
  public List<StreamEntry> getAllStreams() {
    List<StreamEntry> result = new ArrayList<>(entries.size());
    for (DirectoryEntry entry : entries) {
      byte[] content = readEntryContent(entry);
      if (content != null && entry.name.length() > 0) {
        result.add(new StreamEntry(entry.name, content));
      }
    }
    return result;
  }

  private byte[] readEntryContent(DirectoryEntry entry) {
    int size = entry.streamSize;
    if (size <= 0) {
      return new byte[0];
    }
    if (size < 4096) {
      // 迷你流：从迷你 FAT 分配迷你扇区，从迷你流容器读取
      return readMiniStream(entry.startSector, size);
    } else {
      // 常规流：从 FAT 分配扇区
      return readRegularStream(entry.startSector, size);
    }
  }

  private byte[] readRegularStream(int startSector, int size) {
    byte[] result = new byte[size];
    int offset = 0;
    int sid = startSector;
    while (sid >= 0 && sid != 0xFFFFFFFE && offset < size) {
      int secOffset = (sid + 1) * sectorSize;
      int len = Math.min(sectorSize, size - offset);
      if (secOffset + len > data.length) {
        len = data.length - secOffset;
        if (len <= 0) {
          break;
        }
      }
      System.arraycopy(data, secOffset, result, offset, len);
      offset += len;
      sid = fat[sid];
    }
    return result;
  }

  private byte[] readMiniStream(int startSector, int size) {
    // 读取迷你流链
    byte[] miniData = readMiniFatChain(startSector);
    byte[] result = new byte[size];
    System.arraycopy(miniData, 0, result, 0, Math.min(size, miniData.length));
    return result;
  }

  private byte[] readMiniFatChain(int startSector) {
    // 查找目录条目 "Root Entry" 作为迷你流容器
    byte[] miniStreamContainer = null;
    int miniStreamSize = 0;
    for (DirectoryEntry entry : entries) {
      if ("Root Entry".equals(entry.name)) {
        miniStreamContainer = readRegularStream(entry.startSector, entry.streamSize);
        miniStreamSize = entry.streamSize;
        break;
      }
    }
    if (miniStreamContainer == null) {
      return new byte[0];
    }

    // 遍历迷你 FAT 链
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(4096);
    int sid = startSector;
    int remaining = miniStreamSize;
    while (sid >= 0 && sid != 0xFFFFFFFE && remaining > 0) {
      int miniOffset = sid * miniSectorSize;
      int len = Math.min(miniSectorSize, remaining);
      if (miniOffset + len <= miniStreamContainer.length) {
        bos.write(miniStreamContainer, miniOffset, len);
      }
      remaining -= miniSectorSize;
      if (sid < miniFat.length) {
        sid = miniFat[sid];
      } else {
        break;
      }
    }
    return bos.toByteArray();
  }

  private static int[] readChain(int[] fat, int start, byte[] data, int sectorSize) {
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    int sid = start;
    while (sid >= 0 && sid != 0xFFFFFFFE) {
      int offset = (sid + 1) * sectorSize;
      if (offset + sectorSize <= data.length) {
        bos.write(data, offset, sectorSize);
      }
      sid = fat[sid];
    }
    byte[] allBytes = bos.toByteArray();
    int[] result = new int[allBytes.length / 4];
    ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(result);
    return result;
  }

  @Override
  public void close() {
    // 无释放资源
  }

  // ==================== 内部类 ====================

  /** 目录条目（96 字节） */
  static final class DirectoryEntry {
    String name;
    int nameLen;
    int type; // 0=未知, 1=存储, 2=流, 5=根存储
    int startSector;
    int streamSize;
    int sidLeftSibling;
    int sidRightSibling;
    int sidChild;

    static final int ENTRY_SIZE = 128;

    static DirectoryEntry read(DirectoryStreamReader reader) throws IOException {
      DirectoryEntry e = new DirectoryEntry();
      byte[] nameBytes = reader.readBytes(64);
      int nameLen = reader.readUShort();
      int type = reader.readByte();
      int color = reader.readByte(); // 忽略
      e.sidLeftSibling = reader.readInt();
      e.sidRightSibling = reader.readInt();
      e.sidChild = reader.readInt();
      reader.skip(36); // CLSID + state + time stamps
      e.startSector = reader.readInt();
      e.streamSize = reader.readInt();
      reader.skip(4); // 忽略

      if (type != 2 || nameLen <= 0 || nameLen > 64) {
        return e; // 非流条目
      }
      e.name = new String(nameBytes, 0, nameLen - 2, StandardCharsets.UTF_16LE); // 去掉结尾 NUL
      e.nameLen = nameLen;
      e.type = type;
      return e;
    }
  }

  /** 目录扇区读取器 */
  static final class DirectoryStreamReader implements AutoCloseable {
    private final byte[] data;
    private int offset;
    private final int sectorSize;
    private final int maxEntries;

    DirectoryStreamReader(byte[] data, int offset, int sectorSize, int maxEntries) {
      this.data = data;
      this.offset = offset;
      this.sectorSize = sectorSize;
      this.maxEntries = maxEntries;
    }

    byte[] readBytes(int len) throws IOException {
      if (offset + len > data.length) {
        throw new IOException("Directory EOF");
      }
      byte[] result = new byte[len];
      System.arraycopy(data, offset, result, 0, len);
      offset += len;
      return result;
    }

    int readByte() {
      if (offset >= data.length) {
        return 0;
      }
      return data[offset++] & 0xFF;
    }

    int readUShort() {
      if (offset + 2 > data.length) {
        return 0;
      }
      int v = (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
      offset += 2;
      return v;
    }

    int readInt() {
      if (offset + 4 > data.length) {
        return 0;
      }
      int v = (data[offset] & 0xFF)
          | ((data[offset + 1] & 0xFF) << 8)
          | ((data[offset + 2] & 0xFF) << 16)
          | ((data[offset + 3] & 0xFF) << 24);
      offset += 4;
      return v;
    }

    void skip(int len) {
      offset += len;
    }

    @Override
    public void close() {
      // no-op
    }
  }
}
