import pathlib
pg = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-search/src/main/java/com/njydsz/pmis/common/search/engine/pg/PgSearchEngine.java')
t = pg.read_text(encoding='utf-8')
old14 = '    private static final int MAX_MEMORY_INDEX_SIZE = 10000;'
new14 = old14 + '\n\n    /** P2-14: ALLOWED_COLUMNS */\n    private static final java.util.Set<String> ALLOWED_COLUMNS = java.util.Set.of(\n            "id", "doc_type", "title", "subtitle", "content", "snippet",\n            "tags", "status", "path", "tenant_id", "created_by", "created_at",\n            "updated_by", "updated_at", "searchable_text", "metadata",\n            "created_at_ts", "updated_at_ts\n    );'
if old14 in t
    t = t.replace(old14, new14, 1)
    print('P2-14: ALLOWfd_COLUMNS added')
else:
    print('P2-14: SKIP - not found')
pg.write_text(t, encoding='utf-8')
print('completed')