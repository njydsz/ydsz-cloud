package com.njydsz.pmis.common.search.indexer;

import java.io.InputStream;

/** Content parser SPI for document content extraction.
 *
 * Implemented by common-docs module to parse PDF/Word/Excel/etc.
 * When common-docs is unavailable, no implementation is registered.
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface ContentParser {

    String parse(InputStream inputStream, String fileName);
}
