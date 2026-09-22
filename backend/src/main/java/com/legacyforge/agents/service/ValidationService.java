package com.legacyforge.agents.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ParserConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Static validation of generated code. Java files get an AST parse via
 * JavaParser (already on the classpath from Week 5's chunking service).
 * Non-Java files are returned as SKIPPED — a full Angular/TypeScript
 * validator would require spawning a node process per file, which is
 * expensive and out of scope for this pass.
 *
 * Syntax-level checks catch the most common LLM failures: missing braces,
 * unterminated strings, invalid keywords, and truncated output. Semantic
 * errors (missing imports, wrong types) would need a real classpath.
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final JavaParser javaParser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            .setStoreTokens(false));

    public Result validate(String targetPath, String code) {
        if (code == null || code.isBlank()) {
            return Result.invalid(List.of("Generated code is empty"));
        }
        if (targetPath == null) {
            return Result.skipped("No TARGET header — cannot infer language");
        }
        String lower = targetPath.toLowerCase();
        if (lower.endsWith(".java")) {
            return validateJava(code);
        }
        return Result.skipped("Unsupported extension for validation");
    }

    private Result validateJava(String code) {
        try {
            ParseResult<CompilationUnit> pr = javaParser.parse(code);
            if (pr.isSuccessful() && pr.getResult().isPresent()) {
                return Result.valid();
            }
            List<String> errors = pr.getProblems().stream()
                    .map(this::formatProblem)
                    .toList();
            if (errors.isEmpty()) errors = List.of("Java parse returned no AST but reported no problems");
            return Result.invalid(errors);
        } catch (Exception e) {
            log.warn("JavaParser threw while validating: {}", e.getMessage());
            return Result.invalid(List.of("Parser threw: " + e.getMessage()));
        }
    }

    private String formatProblem(Problem p) {
        String loc = p.getLocation()
                .flatMap(tr -> tr.getBegin().getRange())
                .map(r -> "line " + r.begin.line + ":" + r.begin.column)
                .orElse("<no location>");
        return loc + " — " + p.getMessage();
    }

    public record Result(Status status, List<String> errors, String note) {
        public enum Status { VALID, INVALID, SKIPPED }
        public static Result valid() { return new Result(Status.VALID, List.of(), null); }
        public static Result invalid(List<String> errs) { return new Result(Status.INVALID, errs, null); }
        public static Result skipped(String note) { return new Result(Status.SKIPPED, List.of(), note); }
    }
}
