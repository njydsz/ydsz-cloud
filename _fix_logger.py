import pathlib

base = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-base/src/main/java/com/njydsz/common/base/config')

# Fix OpenApiAutoConfiguration
f = base / 'OpenApiAutoConfiguration.java'
text = f.read_text(encoding='utf-8')
text = text.replace(
    'import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\nimport org.springdoc.core.models.GroupedOpenApi;',
    'import org.springdoc.core.models.GroupedOpenApi;'
)
text = text.replace(
    'import lombok.RequiredArgsConstructor;',
    'import lombok.RequiredArgsConstructor;\nimport lombok.extern.slf4j.Slf4j;'
)
text = text.replace('@AutoConfiguration\n@RequiredArgsConstructor', '@AutoConfiguration\n@Slf4j\n@RequiredArgsConstructor')
text = text.replace(
    '    private static final Logger logger = LoggerFactory.getLogger(OpenApiAutoConfiguration.class);\n\n    /** 文档模块配置属性，由 Spring 注入 */',
    '    /** 文档模块配置属性，由 Spring 注入 */'
)
text = text.replace('logger.', 'log.')
f.write_text(text, encoding='utf-8')
print('Fixed: OpenApiAutoConfiguration.java')

# Fix Knife4jAutoConfiguration
f = base / 'Knife4jAutoConfiguration.java'
text = f.read_text(encoding='utf-8')
text = text.replace(
    'import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;\nimport org.springframework.boot.autoconfigure.AutoConfiguration;',
    'import org.springframework.boot.autoconfigure.AutoConfiguration;'
)
text = text.replace(
    'import lombok.RequiredArgsConstructor;',
    'import lombok.RequiredArgsConstructor;\nimport lombok.extern.slf4j.Slf4j;'
)
text = text.replace('@AutoConfiguration\n@RequiredArgsConstructor', '@AutoConfiguration\n@Slf4j\n@RequiredArgsConstructor')
text = text.replace(
    '    private static final Logger logger = LoggerFactory.getLogger(Knife4jAutoConfiguration.class);\n\n    /** 文档模块配置属性，由 Spring 注入 */',
    '    /** 文档模块配置属性，由 Spring 注入 */'
)
text = text.replace('logger.', 'log.')
f.write_text(text, encoding='utf-8')
print('Fixed: Knife4jAutoConfiguration.java')
