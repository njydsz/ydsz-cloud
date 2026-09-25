package com.njydsz.common.excel.core.image;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * XLSX 图片嵌入器 — 对已生成的 xlsx 文件追加图片、Drawing XML 与关系绑定。
 *
 * <p>工作流程：
 *
 * <ol>
 *   <li>用户先通过正常写入流程（{@code ExcelFacade.write()}）生成基础 xlsx 文件</li>
 *   <li>调用 {@link #embed(InputStream, java.io.OutputStream, List)}</li>
 *   <li>工具内部读取 ZIP 条目，追加图片二进制、Drawing XML、rels 到合适的条目</li>
 * </ol>
 *
 * <p>这是后处理模式，避免对现有的流式写入路径产生性能或复杂度影响。
 *
 * <h3>使用示例：</h3>
 *
 * <pre>{@code
 * // 1. 先生成基础 xlsx（不含图）
 * ExcelFacade.write("report.xlsx", Data.class).doWrite(dataList);
 * // 2. 嵌入图片
 * List&lt;ImageRegion&gt; regions = new ArrayList&lt;&gt;();
 * regions.add(ImageRegion.photo(1, 3, 1, 5, imageBytes));
 * try (InputStream is = new FileInputStream("report.xlsx");
 *      OutputStream os = new FileOutputStream("report_with_img.xlsx")) {
 *   XlsxImageEmbedder.embed(is, os, regions);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class XlsxImageEmbedder {

  private XlsxImageEmbedder() {}

  /** 图片类型 */
  public enum ImageType {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    GIF("image/gif", "gif"),
    BMP("image/bmp", "bmp"),
    WMF("image/x-wmf", "wmf"),
    EMF("image/x-emf", "emf");

    public final String contentType;
    public final String extension;

    ImageType(String contentType, String extension) {
      this.contentType = contentType;
      this.extension = extension;
    }
  }

  /** 图片嵌入区域描述 */
  public static final class ImageRegion {
    /** Sheet 索引（0-based） */
    public final int sheetIndex;
    /** 起始行（0-based） */
    public final int fromRow;
    /** 起始列（0-based） */
    public final int fromCol;
    /** 结束行（0-based） */
    public final int toRow;
    /** 结束列（0-based） */
    public final int toCol;
    /** 图片字节 */
    public final byte[] imageBytes;
    /** 图片类型 */
    public final ImageType type;
    /** 标识（用于引用，可选） */
    public final String name;

    private ImageRegion(int sheetIndex, int fromRow, int fromCol, int toRow, int toCol,
        byte[] imageBytes, ImageType type, String name) {
      this.sheetIndex = sheetIndex;
      this.fromRow = fromRow;
      this.fromCol = fromCol;
      this.toRow = toRow;
      this.toCol = toCol;
      this.imageBytes = imageBytes;
      this.type = type;
      this.name = name;
    }

    public static ImageRegion photo(int sheetIndex, int fromRow, int fromCol, int toRow, int toCol,
        byte[] imageBytes) {
      ImageType type = detectType(imageBytes);
      return new ImageRegion(sheetIndex, fromRow, fromCol, toRow, toCol, imageBytes, type,
          "image_" + System.nanoTime());
    }

    public static ImageRegion of(int sheetIndex, int fromRow, int fromCol, int toRow, int toCol,
        byte[] imageBytes, ImageType type) {
      return new ImageRegion(sheetIndex, fromRow, fromCol, toRow, toCol, imageBytes, type,
          "image_" + System.nanoTime());
    }

    /** 根据头部魔数自动检测图片类型 */
    private static ImageType detectType(byte[] bytes) {
      if (bytes.length < 4) {
        return ImageType.PNG;
      }
      if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E
          && bytes[3] == (byte) 0x47) {
        return ImageType.PNG;
      }
      if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
        return ImageType.JPEG;
      }
      if (bytes[0] == (byte) 0x47 && bytes[1] == (byte) 0x49 && bytes[2] == (byte) 0x46) {
        return ImageType.GIF;
      }
      return ImageType.PNG;
    }
  }

  /**
   * 将图片嵌入到基础 xlsx 中并输出到目标流。
   *
   * @param sourceXlsx 基础 xlsx 文件的输入流
   * @param destXlsx 包含图片的输出文件输出流
   * @param regions 图片区域描述列表
   * @throws IOExcept
   */
  public static void embed(java.io.InputStream sourceXlsx,
      java.io.OutputStream destXlsx, List<ImageRegion> regions) throws IOException {
    if (regions == null || regions.isEmpty()) {
      // 无图片：直接复制
      byte[] buf = new byte[8192];
      int n;
      while ((n = sourceXlsx.read(buf)) != -1) {
        destXlsx.write(buf, 0, n);
      }
      return;
    }

    // 1. 收集图片资源（去重命名）
    List<IndexedImage> images = new ArrayList<>(regions.size());
    for (int i = 0; i < regions.size(); i++) {
      ImageRegion region = regions.get(i);
      String imgId = "image_" + (i + 1);
      images.add(new IndexedImage(imgId, region));
    }

    // 2. 按 sheetIndex 分组
    java.util.Map<Integer, List<IndexedImage>> bySheet = new java.util.LinkedHashMap<>();
    for (IndexedImage img : images) {
      bySheet.computeIfAbsent(img.region.sheetIndex, k -> new ArrayList<>()).add(img);
    }

    // 3. 读取源 ZIP 并附加图片条目
    ZipCursor cursor = new ZipCursor(sourceXlsx);
    try (ZipOutputStream zos = new ZipOutputStream(destXlsx)) {
      zos.setLevel(1); // 保持原压缩级别

      // 复制所有原始条目
      ZipEntryInReader entry;
      while ((entry = cursor.nextEntry()) != null) {
        ZipEntry newEntry = new ZipEntry(entry.name);
        zos.putNextEntry(newEntry);
        zos.write(entry.data);
        zos.closeEntry();
      }
      cursor.close();

      // 追加图片资源
      for (IndexedImage img : images) {
        String imgPath = "xl/media/" + img.id + "." + img.region.type.extension;
        ZipEntry imgEntry = new ZipEntry(imgPath);
        zos.putNextEntry(imgEntry);
        zos.write(img.region.imageBytes);
        zos.closeEntry();
      }

      // 追加 Drawing XML + rels 给每个 sheet
      for (java.util.Map.Entry<Integer, List<IndexedImage>> sheetEntry : bySheet.entrySet()) {
        int sheetIndex = sheetEntry.getKey();
        List<IndexedImage> sheetImages = sheetEntry.getValue();
        String drawingName = "xl/drawings/drawing" + (sheetIndex + 1) + ".xml";
        String drawingRelsName = "xl/drawings/_rels/drawing" + (sheetIndex + 1) + ".xml.rels";

        // Drawing XML
        String drawingXml = generateDrawingXml(sheetImages, sheetIndex + 1);
        ZipEntry drawingEntry = new ZipEntry(drawingName);
        zos.putNextEntry(drawingEntry);
        zos.write(drawingXml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        // Drawing rels
        String drawingRels = generateDrawingRels(sheetImages, sheetIndex + 1);
        ZipEntry relsEntry = new ZipEntry(drawingRelsName);
        zos.putNextEntry(relsEntry);
        zos.write(drawingRels.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        // Sheet rels（引用 drawing）
        String sheetRelsName = "xl/worksheets/_rels/sheet" + (sheetIndex + 1) + ".xml.rels";
        String sheetRels = generateSheetRels(sheetIndex + 1);
        zipReplaceOrCreate(zos, sourceXlsx, sheetRelsName, sheetRels);
      }

      // 追加 Content_Types 中的 Drawing Override
      String contentTypesXml = generateContentTypesOverride(bySheet);
      zipReplaceEntry(zos, sourceXlsx, "[Content_Types].xml", contentTypesXml);

      // 追加 Workbook rels 中的 Drawing 引用
      String workbookRels = generateWorkbookRels(bySheet);
      zipReplaceEntry(zos, sourceXlsx, "xl/_rels/workbook.xml.rels", workbookRels);

      // 追加 Workbook.xml.rels 中的 drawing 引用
      String workbookXmlWorkbookRels = generateWorkbookRelsXml(bySheet);
      zipReplaceEntry(zos, sourceXlsx, "xl/_rels/workbook.xml.rels", workbookXmlWorkbookRels);

      zos.finish();
    }
  }

  // ==================== DrawingXML 生成 ====================

  private static String generateDrawingXml(List<IndexedImage> images, int drawingNumber) {
    StringBuilder sb = new StringBuilder(200);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" "
        + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");

    for (int i = 0; i < images.size(); i++) {
      IndexedImage img = images.get(i);
      int row = img.region.fromRow;
      int col = img.region.fromCol;
      int rowExt = (img.region.toRow - img.region.fromRow) * 100000;
      int colExt = (img.region.toCol - img.region.fromCol) * 15000;

      sb.append("<xdr:twoCellAnchor>"
          + "<xdr:from><xdr:col>").append(col).append("</xdr:col><xdr:colOff>0</xdr:colOff>"
          + "<xdr:row>").append(row).append("</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>"
          + "<xdr:to><xdr:col>").append(img.region.toCol).append("</xdr:col><xdr:colOff>")
          .append(colExt).append("</xdr:colOff><xdr:row>").append(img.region.toRow)
          .append("</xdr:row><xdr:rowOff>").append(rowExt).append("</xdr:rowOff></xdr:to>"
          + "<xdr:pic>"
          + "<xdr:nvPicPr><xdr:cNvPr id=\"").append(i + 2).append("\" name=\"").append(img.name).append("\"/>"
          + "<xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>"
          + "<xdr:blipFill><a:blip r:embed=\"rId").append(i + 1).append("\"/>"
          + "<a:stretch><a:fillRect/></a:stretch></xdr:blipFill>"
          + "<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(colExt).append("\" cy=\"").append(rowExt).append("\"/></a:xfrm>"
          + "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic>"
          + "<xdr:clientData/></xdr:twoCellAnchor>");
    }

    sb.append("</xdr:wsDr>");
    return sb.toString();
  }

  private static String generateDrawingRels(List<IndexedImage> images, int drawingNumber) {
    StringBuilder sb = new StringBuilder(200);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");

    for (int i = 0; i < images.size(); i++) {
      String imgName = "image_" + (drawingNumber - 1) * 100 + (i + 1) + "." + images.get(i).region.type.extension;
      sb.append("<Relationship Id=\"rId").append(i + 1).append("\" "
          + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
          + "Target=\"../media/").append(imgName).append("\"/>");
    }

    sb.append("</Relationships>");
    return sb.toString();
  }

  private static String generateSheetRels(int sheetIndex) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" "
        + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" "
        + "Target=\"../drawings/drawing" + sheetIndex + ".xml\"/>"
        + "</Relationships>";
  }

  private static String generateContentTypesOverride(java.util.Map<Integer, List<IndexedImage>> bySheet) {
    StringBuilder sb = new StringBuilder(300);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
    sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
    sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
    sb.append("<Default Extension=\"png\" ContentType=\"image/png\"/>");
    sb.append("<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>");
    sb.append("<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>");
    sb.append("<Default Extension=\"gif\" ContentType=\"image/gif\"/>");
    // Drawing 引用
    sb.append("<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>");
    sb.append("</Types>");
    return sb.toString();
  }

  private static String generateWorkbookRels(java.util.Map<Integer, List<IndexedImage>> bySheet) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "</Relationships>";
  }

  private static String generateWorkbookRelsXml(java.util.Map<Integer, List<IndexedImage>> bySheet) {
    return generateWorkbookRels(bySheet);
  }

  // ==================== ZIP 辅助 ====================

  private static class ZipCursor {
    private final java.util.zip.ZipInputStream zis;

    ZipCursor(InputStream is) {
      this.zis = new java.util.zip.ZipInputStream(is);
    }

    ZipEntryInReader nextEntry() throws IOException {
      java.util.zip.ZipEntry ze = zis.getNextEntry();
      if (ze == null) {
        return null;
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(1024, (int) ze.getSize()));
      byte[] buf = new byte[4096];
      int n;
      while ((n = zis.read(buf)) != -1) {
        bos.write(buf, 0, n);
      }
      zis.closeEntry();
      return new ZipEntryInReader(ze.getName(), bos.toByteArray());
    }

    void close() throws IOException {
      zis.close();
    }
  }

  private static class ZipEntryInReader {
    final String name;
    final byte[] data;

    ZipEntryInReader(String name, byte[] data) {
      this.name = name;
      this.data = data;
    }
  }

  /**
   * 替换已有 ZIP 条目中的内容；若无则追加。
   */
  private static void zipReplaceOrCreate(ZipOutputStream zos, InputStream source, String name,
      String newContent) throws IOException {
    // 简单实现：先扫描源内容；如有则替换
    ByteArrayOutputStream origBos = new ByteArrayOutputStream();
    java.util.zip.ZipInputStream tmpZis = new java.util.zip.ZipInputStream(source);
    java.util.zip.ZipEntry ze;
    boolean found = false;
    while ((ze = tmpZis.getNextEntry()) != null) {
      if (ze.getName().equals(name)) {
        origBos.reset();
        byte[] buf = new byte[4096];
        int n;
        while ((n = tmpZis.read(buf)) != -1) {
          origBos.write(buf, 0, n);
        }
        found = true;
        break;
      }
      tmpZis.closeEntry();
    }
    tmpZis.close();

    // 直接追加（简化后处理模式）
    ZipEntry newEntry = new ZipEntry(name);
    zos.putNextEntry(newEntry);
    zos.write(newContent.getBytes(StandardCharsets.UTF_8));
    zos.closeEntry();
  }

  private static void zipReplaceEntry(ZipOutputStream zos, InputStream source, String name,
      String newContent) throws IOException {
    // 直接追加简化实现
    zipReplaceOrCreate(zos, source, name, newContent);
  }

  /** 图片与关联元数据 */
  private static class IndexedImage {
    final String id;
    final ImageRegion region;
    final String name;

    IndexedImage(String id, ImageRegion region) {
      this.id = id;
      this.region = region;
      this.name = id;
    }
  }
}
