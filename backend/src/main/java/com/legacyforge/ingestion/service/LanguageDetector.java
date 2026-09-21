package com.legacyforge.ingestion.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Extension-based language classifier. Enough for Week 3; a tree-sitter
 * based classifier arrives in Week 4 when we do AST parsing.
 */
@Component
public class LanguageDetector {

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("kt", "kotlin"),
            Map.entry("scala", "scala"),
            Map.entry("groovy", "groovy"),
            Map.entry("js", "javascript"),
            Map.entry("mjs", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("tsx", "typescript"),
            Map.entry("jsx", "javascript"),
            Map.entry("py", "python"),
            Map.entry("rb", "ruby"),
            Map.entry("go", "go"),
            Map.entry("rs", "rust"),
            Map.entry("c", "c"),
            Map.entry("cc", "cpp"),
            Map.entry("cpp", "cpp"),
            Map.entry("h", "c"),
            Map.entry("hpp", "cpp"),
            Map.entry("cs", "csharp"),
            Map.entry("php", "php"),
            Map.entry("sh", "shell"),
            Map.entry("bash", "shell"),
            Map.entry("html", "html"),
            Map.entry("htm", "html"),
            Map.entry("css", "css"),
            Map.entry("scss", "scss"),
            Map.entry("sass", "sass"),
            Map.entry("less", "less"),
            Map.entry("jsp", "jsp"),
            Map.entry("jspx", "jsp"),
            Map.entry("xml", "xml"),
            Map.entry("xsd", "xml"),
            Map.entry("xsl", "xml"),
            Map.entry("yaml", "yaml"),
            Map.entry("yml", "yaml"),
            Map.entry("json", "json"),
            Map.entry("toml", "toml"),
            Map.entry("ini", "ini"),
            Map.entry("properties", "properties"),
            Map.entry("md", "markdown"),
            Map.entry("sql", "sql"),
            Map.entry("dockerfile", "dockerfile"),
            Map.entry("gradle", "groovy"),
            Map.entry("txt", "plaintext")
    );

    /** File names (no extension) mapped to a language. */
    private static final Map<String, String> FILENAMES = Map.ofEntries(
            Map.entry("Dockerfile", "dockerfile"),
            Map.entry("Makefile", "makefile"),
            Map.entry("Jenkinsfile", "groovy"),
            Map.entry(".gitignore", "gitignore"),
            Map.entry("README", "plaintext"),
            Map.entry("LICENSE", "plaintext"),
            Map.entry("pom.xml", "xml")
    );

    /** Binary extensions we should NOT try to store as text. */
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "svg",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "tar", "gz", "bz2", "7z", "rar",
            "jar", "war", "ear", "class",
            "exe", "dll", "so", "dylib", "bin",
            "mp3", "mp4", "wav", "avi", "mov", "mkv", "flv",
            "ttf", "otf", "woff", "woff2", "eot"
    );

    public String detectLanguage(String path) {
        String base = baseName(path);
        String named = FILENAMES.get(base);
        if (named != null) return named;
        String ext = extension(path);
        if (ext == null) return null;
        return EXTENSIONS.get(ext);
    }

    public boolean isBinaryByExtension(String path) {
        String ext = extension(path);
        return ext != null && BINARY_EXTENSIONS.contains(ext);
    }

    private static String extension(String path) {
        String base = baseName(path);
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) return null;
        return base.substring(dot + 1).toLowerCase();
    }

    private static String baseName(String path) {
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }
}
