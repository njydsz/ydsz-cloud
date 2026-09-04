package com.njydsz.agent.domain.document;

import java.util.Map;

/**
 * 文档模板值对象。
 *
 * <p>定义文档生成的模板参数。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class DocumentTemplate {

    private final String templateName;
    private final String title;
    private final String author;
    private final String system;
    private final String headerText;
    private final String footerText;
    private final Map<String, Object> variables;

    private DocumentTemplate(Builder builder) {
        this.templateName = builder.templateName;
        this.title = builder.title;
        this.author = builder.author;
        this.system = builder.system;
        this.headerText = builder.headerText;
        this.footerText = builder.footerText;
        this.variables = builder.variables != null ? Map.copyOf(builder.variables) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getSystem() {
        return system;
    }

    public String getHeaderText() {
        return headerText;
    }

    public String getFooterText() {
        return footerText;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public static final class Builder {
        private String templateName;
        private String title;
        private String author;
        private String system;
        private String headerText;
        private String footerText;
        private Map<String, Object> variables;

        public Builder templateName(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder system(String system) {
            this.system = system;
            return this;
        }

        public Builder headerText(String headerText) {
            this.headerText = headerText;
            return this;
        }

        public Builder footerText(String footerText) {
            this.footerText = footerText;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public DocumentTemplate build() {
            return new DocumentTemplate(this);
        }
    }
}
