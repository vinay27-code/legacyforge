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
 * Walks every generated Java artifact with JavaParser and records the classes
 * it references (imports, fields, method params, constructor calls). Each
 * reference becomes a row in migration_dependencies; if the referenced class
 * matches another artifact's declared FQN, the row is marked resolved and
 * carries the target artifact id.
 *
 * Unresolved edges are the interesting signal: they either point at classes
 * the plan missed, classes whose migration failed, or classes the LLM
 * hallucinated a name for. Either way they're structural coherence gaps.
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

    /** Rebuild the dependency graph for one repo. Idempotent — deletes existing edges first. */
    @Transactional
    public int rebuildFor(UUID repoId) {
        deps.deleteByRepoId(repoId);
        deps.flush();

        List<MigrationArtifact> all = artifacts.findByRepoIdOrderByPhaseNumberAscFilePathAsc(repoId);

        // Extract declared FQN for every VALID Java artifact, and build FQN → artifactId lookup
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

        // Build import table: simpleName → fullyQualifiedName
        Map<String, String> importMap = new HashMap<>();
        for (ImportDeclaration imp : cu.getImports()) {
            String fq = imp.getNameAsString();
            if (imp.isAsterisk()) continue; // skip star imports
            String simple = fq.substring(fq.lastIndexOf('.') + 1);
            importMap.put(simple, fq);
        }

        List<Edge> found = new ArrayList<>();

        // 1. IMPORT edges: every non-JDK, non-external import
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk()) continue;
            String fq = imp.getNameAsString();
            if (shouldIgnore(fq)) continue;
            found.add(new Edge(fq, MigrationDependency.EdgeType.IMPORT));
        }

        // 2. FIELD types
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            for (com.github.javaparser.ast.body.VariableDeclarator v : f.getVariables()) {
                collectType(v.getType(), importMap, ownPkg, MigrationDependency.EdgeType.FIELD, found);
            }
        }

        // 3. METHOD_PARAM types
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            for (Parameter p : m.getParameters()) {
                collectType(p.getType(), importMap, ownPkg, MigrationDependency.EdgeType.METHOD_PARAM, found);
            }
        }

        // 4. INSTANTIATION expressions (new Foo(...))
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            collectType(expr.getType(), importMap, ownPkg, MigrationDependency.EdgeType.INSTANTIATION, found);
        }

        // Deduplicate edges by (toClassName, type)
        Map<String, Edge> unique = new LinkedHashMap<>();
        for (Edge e : found) {
            unique.putIfAbsent(e.className + "|" + e.type, e);
        }

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
        // Recurse into generic type arguments (e.g. List<Foo>, Map<String, Bar>)
        cit.getTypeArguments().ifPresent(args -> {
            for (com.github.javaparser.ast.type.Type arg : args) {
                collectType(arg, importMap, ownPkg, edgeType, out);
            }
        });
    }

    private String resolveFqn(String simpleOrDotted, Map<String, String> importMap, String ownPkg) {
        if (simpleOrDotted.contains(".")) return simpleOrDotted;
        if (importMap.containsKey(simpleOrDotted)) return importMap.get(simpleOrDotted);
        // Assume same-package if it looks like a project class (starts uppercase)
        if (Character.isUpperCase(simpleOrDotted.charAt(0)) && !ownPkg.isEmpty()) {
            return ownPkg + "." + simpleOrDotted;
        }
        return null;
    }

    /** Ignore JDK, Spring, Jakarta, Lombok, JUnit, Mockito, etc — anything not the project. */
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
        return !fqn.contains("."); // ignore unresolved single names
    }

    private boolean isJava(MigrationArtifact a) {
        return a.getTargetPath() != null && a.getTargetPath().toLowerCase().endsWith(".java");
    }

    private record Edge(String className, MigrationDependency.EdgeType type) {}
}
