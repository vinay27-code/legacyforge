package com.legacyforge.rag.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.legacyforge.ingestion.entity.RepoFile;
import com.legacyforge.rag.entity.CodeChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Splits a file into semantic chunks that fit inside an embedding model's
 * context window. For .java files we chunk per method with class context
 * as a prefix. For everything else we chunk as whole file (or windowed if
 * it is very large).
 */
@Component
public class ChunkingService {

    private static final int MAX_CHUNK_CHARS = 4000;   // ~1000 tokens for embedding models
    private static final int MAX_LINES_PER_WINDOW = 200;

    private final JavaParser javaParser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            .setStoreTokens(false));

    public List<CodeChunk> chunk(RepoFile file) {
        List<CodeChunk> out = new ArrayList<>();
        if (file.getContent() == null || file.isBinary()) return out;

        if ("java".equals(file.getLanguage())) {
            out.addAll(chunkJava(file));
        } else {
            out.addAll(chunkGeneric(file));
        }

        for (int i = 0; i < out.size(); i++) out.get(i).setChunkIndex(i);
        return out;
    }

    private List<CodeChunk> chunkJava(RepoFile file) {
        List<CodeChunk> out = new ArrayList<>();
        ParseResult<CompilationUnit> parsed;
        try {
            parsed = javaParser.parse(file.getContent());
        } catch (Exception e) {
            return chunkGeneric(file);
        }
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            return chunkGeneric(file);
        }
        CompilationUnit cu = parsed.getResult().get();

        String pkg = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse(null);
        String imports = cu.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .map(i -> "import " + i + ";")
                .collect(Collectors.joining("\n"));

        for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = cls.getNameAsString();
            String classHeader = cls.getModifiers().stream().map(m -> m.getKeyword().asString())
                    .collect(Collectors.joining(" "))
                    + " " + (cls.isInterface() ? "interface" : "class") + " " + className;

            List<MethodDeclaration> methods = cls.getMethods();
            if (methods.isEmpty()) {
                // No methods: emit the whole class as one chunk
                out.add(build(file, CodeChunk.ChunkType.CLASS, className, null,
                        cls.getBegin().map(p -> p.line).orElse(null),
                        cls.getEnd().map(p -> p.line).orElse(null),
                        header(pkg, imports, classHeader) + "\n\n" + trim(cls.toString())));
                continue;
            }

            for (MethodDeclaration m : methods) {
                String methodSrc = m.toString();
                String body = header(pkg, imports, classHeader) + "\n\n" + trim(methodSrc);
                out.add(build(file, CodeChunk.ChunkType.METHOD, className, m.getNameAsString(),
                        m.getBegin().map(p -> p.line).orElse(null),
                        m.getEnd().map(p -> p.line).orElse(null),
                        body));
            }
        }

        // Fallback: if no classes were found (weird source), chunk generically
        if (out.isEmpty()) return chunkGeneric(file);
        return out;
    }

    private List<CodeChunk> chunkGeneric(RepoFile file) {
        List<CodeChunk> out = new ArrayList<>();
        String content = file.getContent();
        String[] lines = content.split("\r?\n", -1);

        if (content.length() <= MAX_CHUNK_CHARS) {
            out.add(build(file, CodeChunk.ChunkType.WHOLE_FILE, null, null, 1, lines.length,
                    "// " + file.getPath() + "\n\n" + trim(content)));
            return out;
        }

        // Slide a window of MAX_LINES_PER_WINDOW lines with a 20-line overlap.
        int step = MAX_LINES_PER_WINDOW;
        int overlap = 20;
        int start = 0;
        while (start < lines.length) {
            int end = Math.min(lines.length, start + step);
            StringBuilder sb = new StringBuilder("// " + file.getPath() + " (lines " + (start + 1) + "-" + end + ")\n\n");
            for (int i = start; i < end; i++) {
                sb.append(lines[i]).append('\n');
            }
            out.add(build(file, CodeChunk.ChunkType.BLOCK, null, null, start + 1, end, trim(sb.toString())));
            if (end == lines.length) break;
            start = end - overlap;
        }
        return out;
    }

    private String header(String pkg, String imports, String classHeader) {
        StringBuilder h = new StringBuilder();
        if (pkg != null) h.append("package ").append(pkg).append(";\n");
        if (!imports.isBlank()) h.append(imports).append("\n");
        h.append("// context: ").append(classHeader);
        return h.toString();
    }

    private String trim(String s) {
        if (s.length() <= MAX_CHUNK_CHARS) return s;
        return s.substring(0, MAX_CHUNK_CHARS) + "\n// ... (truncated)";
    }

    private CodeChunk build(RepoFile file, CodeChunk.ChunkType type, String className, String methodName,
                            Integer start, Integer end, String content) {
        CodeChunk c = new CodeChunk();
        c.setRepoId(file.getRepoId());
        c.setFileId(file.getId());
        c.setFilePath(file.getPath());
        c.setChunkType(type);
        c.setClassName(className);
        c.setMethodName(methodName);
        c.setStartLine(start);
        c.setEndLine(end);
        c.setContent(content);
        return c;
    }
}
