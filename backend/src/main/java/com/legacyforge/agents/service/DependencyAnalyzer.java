package com.legacyforge.agents.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.legacyforge.agents.entity.MigrationArtifact;
import com.legacyforge.agents.entity.MigrationDependency;
import com.legacyforge.agents.repo.MigrationArtifactRepository;
import com.legacyforge.agents.repo.MigrationDependencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Extracts class references from every generated Java artifact via JavaParser
 * and builds a dependency graph. Week 10: java.lang.* auto-imports are now
 * treated as implicit (so String, Long, etc. no longer appear as "broken").
 */
@Service
public class DependencyAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DependencyAnalyzer.class);

    private final MigrationArtifactRepository artifacts;
    private final MigrationDependencyRepository deps;

    private final JavaParser javaParser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            .setStoreTokens(false));

    public DependencyAnalyzer(MigrationArtifactRepository artifacts,
                              MigrationDependencyRepository deps) {
        this.artifacts = artifacts;
        this.deps = deps;
    }

    @Transactional
    public int rebuildFor(UUID repoId) {
        deps.deleteByRepoId(repoId);
        deps.flush();

        List<MigrationArtifact> all = artifacts.findByRepoIdOrderByPhaseNumberAscFilePathAsc(repoId);

        Map<String, UUID> fqnToId = new HashMap<>();
        for (MigrationArtifact a : all) {
            if (!isJava(a) || a.getGeneratedCode() == null) continue;
            String fqn = declaredFqn(a.getGeneratedCode());
            if (fqn != null) {
                a.setDeclaredFqn(fqn);
                fqnToId.put(fqn, a.getId());
            }
        }
        artifacts.saveAll(all);
        artifacts.flush();

        int edges = 0;
        for (MigrationArtifact a : all) {
            if (!isJava(a) || a.getGeneratedCode() == null) continue;
            edges += extractEdges(a, fqnToId);
        }
        log.info("Rebuilt dependency graph for repo {}: {} edges across {} artifacts",
                repoId, edges, all.size());
        return edges;
    }

    // ---- extraction ----------------------------------------------------

    private String declaredFqn(String code) {
        try {
            ParseResult<CompilationUnit> pr = javaParser.parse(code);
            if (!pr.isSuccessful() || pr.getResult().isEmpty()) return null;
            CompilationUnit cu = pr.getResult().get();
            String pkg = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
            List<TypeDeclaration<?>> types = cu.getTypes();
            if (types.isEmpty()) return null;
            String name = types.get(0).getNameAsString();
            return pkg.isEmpty() ? name : pkg + "." + name;
        } catch (Exception e) {
            return null;
        }
    }

    private int extractEdges(MigrationArtifact a, Map<String, UUID> fqnToId) {
        ParseResult<CompilationUnit> pr;
        try {
            pr = javaParser.parse(a.getGeneratedCode());
        } catch (Exception e) {
            return 0;
        }
        if (!pr.isSuccessful() || pr.getResult().isEmpty()) return 0;
        CompilationUnit cu = pr.getResult().get();

        String ownPkg = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");

        Map<String, String> importMap = new HashMap<>();
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk()) continue;
            String fq = imp.getNameAsString();
            String simple = fq.substring(fq.lastIndexOf('.') + 1);
            importMap.put(simple, fq);
        }

        List<Edge> found = new ArrayList<>();

        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk()) continue;
            String fq = imp.getNameAsString();
            if (shouldIgnore(fq)) continue;
            found.add(new Edge(fq, MigrationDependency.EdgeType.IMPORT));
        }
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            for (com.github.javaparser.ast.body.VariableDeclarator v : f.getVariables()) {
                collectType(v.getType(), importMap, ownPkg, MigrationDependency.EdgeType.FIELD, found);
            }
        }
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            for (Parameter p : m.getParameters()) {
                collectType(p.getType(), importMap, ownPkg, MigrationDependency.EdgeType.METHOD_PARAM, found);
            }
        }
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            collectType(expr.getType(), importMap, ownPkg, MigrationDependency.EdgeType.INSTANTIATION, found);
        }

        Map<String, Edge> unique = new LinkedHashMap<>();
        for (Edge e : found) unique.putIfAbsent(e.className + "|" + e.type, e);

        int saved = 0;
        for (Edge e : unique.values()) {
            MigrationDependency md = new MigrationDependency();
            md.setRepoId(a.getRepoId());
            md.setFromArtifactId(a.getId());
            md.setToClassName(e.className);
            md.setEdgeType(e.type);
            UUID target = fqnToId.get(e.className);
            if (target != null) {
                md.setToArtifactId(target);
                md.setResolved(true);
            } else {
                md.setResolved(false);
            }
            deps.save(md);
            saved++;
        }
        return saved;
    }

    private void collectType(com.github.javaparser.ast.type.Type type,
                             Map<String, String> importMap, String ownPkg,
                             MigrationDependency.EdgeType edgeType,
                             List<Edge> out) {
        if (!(type instanceof ClassOrInterfaceType cit)) return;
        String simple = cit.getNameAsString();
        String fq = resolveFqn(simple, importMap, ownPkg);
        if (fq == null || shouldIgnore(fq)) return;
        out.add(new Edge(fq, edgeType));
        cit.getTypeArguments().ifPresent(args -> {
            for (com.github.javaparser.ast.type.Type arg : args) {
                collectType(arg, importMap, ownPkg, edgeType, out);
            }
        });
    }

    /**
     * java.lang.* is auto-imported so a bare `String` in source refers to
     * `java.lang.String`. Keep this list small and current — everything
     * outside it that isn't explicitly imported gets treated as a
     * same-package reference.
     */
    private static final Set<String> JAVA_LANG_IMPLICITS = Set.of(
            "String", "Object", "Class", "Boolean", "Byte", "Character", "Double", "Float",
            "Integer", "Long", "Short", "Number", "Void",
            "Throwable", "Exception", "RuntimeException", "Error", "Iterable", "Comparable",
            "Runnable", "Thread", "StringBuilder", "StringBuffer",
            "System", "Math", "Enum", "Record",
            "IllegalArgumentException", "IllegalStateException", "NullPointerException",
            "UnsupportedOperationException", "ClassCastException", "IndexOutOfBoundsException",
            "NumberFormatException", "ArithmeticException", "SecurityException",
            "AutoCloseable", "CharSequence", "Cloneable", "Deprecated", "Override",
            "SuppressWarnings", "FunctionalInterface", "SafeVarargs"
    );

    private String resolveFqn(String simpleOrDotted, Map<String, String> importMap, String ownPkg) {
        if (simpleOrDotted.contains(".")) return simpleOrDotted;
        if (importMap.containsKey(simpleOrDotted)) return importMap.get(simpleOrDotted);
        if (JAVA_LANG_IMPLICITS.contains(simpleOrDotted)) return "java.lang." + simpleOrDotted;
        if (Character.isUpperCase(simpleOrDotted.charAt(0)) && !ownPkg.isEmpty()) {
            return ownPkg + "." + simpleOrDotted;
        }
        return null;
    }

    private static final List<String> IGNORE_PREFIXES = List.of(
            "java.", "javax.", "jakarta.",
            "org.springframework.", "org.springframework",
            "com.fasterxml.", "com.google.",
            "org.slf4j.", "org.junit.", "org.mockito.",
            "lombok.", "kotlin.",
            "org.apache.", "io.swagger.", "io.micrometer.",
            "com.zaxxer.", "org.hibernate."
    );

    private boolean shouldIgnore(String fqn) {
        for (String p : IGNORE_PREFIXES) if (fqn.startsWith(p)) return true;
        return !fqn.contains(".");
    }

    private boolean isJava(MigrationArtifact a) {
        return a.getTargetPath() != null && a.getTargetPath().toLowerCase().endsWith(".java");
    }

    private record Edge(String className, MigrationDependency.EdgeType type) {}
}
