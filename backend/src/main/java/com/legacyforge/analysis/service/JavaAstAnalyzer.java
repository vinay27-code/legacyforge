package com.legacyforge.analysis.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.stmt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Parses a single .java file into an AST and pulls out:
 *   - package + primary class name
 *   - all imports
 *   - method count
 *   - cyclomatic complexity per method (summed for the file)
 *   - simple framework annotations spotted (@Controller, @Service, @Entity, etc.)
 *   - findings (raw JDBC use, field-injected @Autowired, System.out, etc.)
 */
@Component
public class JavaAstAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaAstAnalyzer.class);

    private final JavaParser parser;

    public JavaAstAnalyzer() {
        // Java 21 grammar accepts everything from Java 8, so parsing legacy code works.
        this.parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setStoreTokens(false));
    }

    public Result analyze(String path, String source) {
        Result r = new Result();
        r.filePath = path;

        if (source == null || source.isBlank()) return r;

        ParseResult<CompilationUnit> parsed;
        try {
            parsed = parser.parse(source);
        } catch (Exception e) {
            log.debug("Failed to parse {}: {}", path, e.getMessage());
            return r;
        }
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) return r;

        CompilationUnit cu = parsed.getResult().get();

        r.packageName = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse(null);
        r.className = cu.getPrimaryTypeName().orElse(null);
        r.loc = source.split("\r?\n", -1).length;

        for (ImportDeclaration imp : cu.getImports()) {
            r.imports.add(imp.getNameAsString());
        }

        int totalComplexity = 0;
        int methodCount = 0;
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            methodCount++;
            totalComplexity += cyclomatic(m);
        }
        r.methodCount = methodCount;
        r.complexity = Math.max(1, totalComplexity);

        // Framework annotation sniffing on types
        cu.findAll(MarkerAnnotationExpr.class).forEach(a -> recordAnnotation(r, a.getNameAsString()));
        cu.findAll(NormalAnnotationExpr.class).forEach(a -> recordAnnotation(r, a.getNameAsString()));
        cu.findAll(SingleMemberAnnotationExpr.class).forEach(a -> recordAnnotation(r, a.getNameAsString()));

        // Findings: obvious smells worth flagging
        scanFindings(r, cu, source);

        return r;
    }

    /** Simple cyclomatic complexity: 1 baseline + each branching statement. */
    private int cyclomatic(MethodDeclaration m) {
        int c = 1;
        c += m.findAll(IfStmt.class).size();
        c += m.findAll(ForStmt.class).size();
        c += m.findAll(ForEachStmt.class).size();
        c += m.findAll(WhileStmt.class).size();
        c += m.findAll(DoStmt.class).size();
        c += m.findAll(CatchClause.class).size();
        c += m.findAll(SwitchEntry.class).size();
        c += m.findAll(com.github.javaparser.ast.expr.ConditionalExpr.class).size();
        return c;
    }

    private void recordAnnotation(Result r, String name) {
        switch (name) {
            case "Controller", "RestController", "RequestMapping", "GetMapping", "PostMapping" ->
                    r.frameworks.add("SPRING_MVC");
            case "SpringBootApplication", "EnableAutoConfiguration" -> r.frameworks.add("SPRING_BOOT");
            case "Service", "Component", "Repository", "Configuration" -> r.frameworks.add("SPRING");
            case "Entity", "Table", "MappedSuperclass" -> r.frameworks.add("JPA");
            case "Stateless", "Stateful", "Singleton", "EJB", "MessageDriven" -> r.frameworks.add("EJB");
            case "ManagedBean", "ViewScoped", "SessionScoped" -> r.frameworks.add("JSF");
            case "Path", "GET", "POST", "PUT", "DELETE", "Consumes", "Produces" -> r.frameworks.add("JAX_RS");
            case "Autowired", "Inject" -> r.findings.add(finding(
                    "field-injection", "medium",
                    "Uses @" + name + " field injection instead of constructor injection"));
            default -> { /* ignore */ }
        }
    }

    private void scanFindings(Result r, CompilationUnit cu, String source) {
        if (source.contains("System.out.print")) {
            r.findings.add(finding("system-out", "low", "Uses System.out; prefer a logger"));
        }
        if (source.contains("DriverManager.getConnection")) {
            r.findings.add(finding("raw-jdbc", "high", "Raw JDBC connection; migrate to Spring Data JPA"));
        }
        if (source.contains("printStackTrace()")) {
            r.findings.add(finding("printstacktrace", "low", "printStackTrace() found; use a logger"));
        }
        if (source.contains(".executeQuery(") || source.contains(".executeUpdate(")) {
            long q = source.split("\\.executeQuery|\\.executeUpdate", -1).length - 1;
            if (q > 0) r.findings.add(finding("jdbc-execution", "medium",
                    q + " raw JDBC execution call(s); consider JdbcTemplate or JPA"));
        }
        if (source.contains("new SimpleDateFormat")) {
            r.findings.add(finding("date-time", "low",
                    "Uses java.text.SimpleDateFormat; prefer java.time.format.DateTimeFormatter"));
        }
    }

    private Map<String, Object> finding(String code, String severity, String message) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("severity", severity);
        m.put("message", message);
        return m;
    }

    public static class Result {
        public String filePath;
        public String packageName;
        public String className;
        public int loc;
        public int methodCount;
        public int complexity;
        public List<String> imports = new ArrayList<>();
        public Set<String> frameworks = new LinkedHashSet<>();
        public List<Map<String, Object>> findings = new ArrayList<>();
    }
}
