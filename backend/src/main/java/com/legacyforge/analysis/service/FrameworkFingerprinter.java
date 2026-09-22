package com.legacyforge.analysis.service;

import com.legacyforge.ingestion.entity.RepoFile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Detects which frameworks a legacy repo uses by scanning:
 *   - Maven/Gradle build files for coordinates
 *   - web.xml, struts-config.xml, struts.xml, faces-config.xml, applicationContext.xml
 *   - Java import statements aggregated across all files
 *
 * Returns a scored fingerprint per framework (name, version if detectable,
 * confidence 0..100, and evidence). The higher the score, the more evidence.
 */
@Component
public class FrameworkFingerprinter {

    /** One row per detected framework with its evidence. */
    public record Detected(String key, String name, String version, int confidence, List<String> evidence) {}

    // Signals we look for. Each signal points at one framework and carries points.
    // (Signals in build files or web.xml are worth more than a lone import.)
    private static final List<Signal> SIGNALS = List.of(
            // Build coordinates (strong evidence)
            new Signal("STRUTS_1", "Struts 1", "pom.xml", Pattern.compile("<groupId>struts</groupId>\\s*<artifactId>struts</artifactId>"), 50, null),
            new Signal("STRUTS_2", "Struts 2", "pom.xml", Pattern.compile("<artifactId>struts2-core</artifactId>"), 50, null),
            new Signal("SPRING_MVC", "Spring MVC", "pom.xml", Pattern.compile("<artifactId>spring-webmvc</artifactId>"), 40, "(?s)<artifactId>spring-webmvc</artifactId>\\s*<version>([^<]+)</version>"),
            new Signal("SPRING_BOOT", "Spring Boot", "pom.xml", Pattern.compile("<artifactId>spring-boot(-starter[-a-z]*)?</artifactId>"), 50, "(?s)<parent>.*?<artifactId>spring-boot-starter-parent</artifactId>\\s*<version>([^<]+)</version>"),
            new Signal("HIBERNATE", "Hibernate", "pom.xml", Pattern.compile("<artifactId>hibernate-(core|entitymanager)</artifactId>"), 40, "(?s)<artifactId>hibernate-core</artifactId>\\s*<version>([^<]+)</version>"),
            new Signal("MYBATIS", "MyBatis", "pom.xml", Pattern.compile("<artifactId>mybatis(-spring)?</artifactId>"), 40, "(?s)<artifactId>mybatis</artifactId>\\s*<version>([^<]+)</version>"),
            new Signal("EJB", "EJB", "pom.xml", Pattern.compile("<artifactId>(ejb|javax\\.ejb-api|jakarta\\.ejb-api)</artifactId>"), 40, null),
            new Signal("JSF", "JSF", "pom.xml", Pattern.compile("<artifactId>(jsf-api|jsf-impl|myfaces-api|mojarra)</artifactId>"), 40, null),
            new Signal("JAX_RS", "JAX-RS", "pom.xml", Pattern.compile("<artifactId>(jersey-server|resteasy-jaxrs|javax\\.ws\\.rs-api)</artifactId>"), 40, null),
            new Signal("JUNIT", "JUnit", "pom.xml", Pattern.compile("<artifactId>junit(-jupiter)?(-api|-engine)?</artifactId>"), 20, null),

            // Config files
            new Signal("STRUTS_1", "Struts 1", "struts-config.xml", Pattern.compile("<struts-config"), 60, null),
            new Signal("STRUTS_2", "Struts 2", "struts.xml", Pattern.compile("<!DOCTYPE struts|<struts[\\s>]"), 60, null),
            new Signal("SPRING_MVC", "Spring MVC", "web.xml", Pattern.compile("<servlet-class>org\\.springframework\\.web\\.servlet\\.DispatcherServlet"), 50, null),
            new Signal("SPRING", "Spring", "applicationContext.xml", Pattern.compile("<beans[\\s>]|http://www\\.springframework\\.org/schema/beans"), 40, null),
            new Signal("JSF", "JSF", "faces-config.xml", Pattern.compile("<faces-config"), 60, null),
            new Signal("JSP", "JSP", "web.xml", Pattern.compile("<jsp-config|<welcome-file>[^<]*\\.jsp"), 30, null),

            // Imports (aggregated across all java files)
            new Signal("STRUTS_1", "Struts 1", "__imports__", Pattern.compile("^org\\.apache\\.struts\\.(action|actions|util|taglib)\\."), 20, null),
            new Signal("STRUTS_2", "Struts 2", "__imports__", Pattern.compile("^com\\.opensymphony\\.xwork2|^org\\.apache\\.struts2"), 20, null),
            new Signal("SPRING_MVC", "Spring MVC", "__imports__", Pattern.compile("^org\\.springframework\\.web\\.(servlet|bind)"), 15, null),
            new Signal("SPRING_BOOT", "Spring Boot", "__imports__", Pattern.compile("^org\\.springframework\\.boot"), 15, null),
            new Signal("HIBERNATE", "Hibernate", "__imports__", Pattern.compile("^org\\.hibernate"), 15, null),
            new Signal("MYBATIS", "MyBatis", "__imports__", Pattern.compile("^org\\.mybatis|^org\\.apache\\.ibatis"), 15, null),
            new Signal("EJB", "EJB", "__imports__", Pattern.compile("^javax\\.ejb|^jakarta\\.ejb"), 20, null),
            new Signal("JSF", "JSF", "__imports__", Pattern.compile("^javax\\.faces|^jakarta\\.faces"), 20, null),
            new Signal("SERVLET", "Servlet API", "__imports__", Pattern.compile("^javax\\.servlet|^jakarta\\.servlet"), 10, null),
            new Signal("JDBC", "JDBC", "__imports__", Pattern.compile("^java\\.sql\\."), 5, null),
            new Signal("LOG4J_1", "Log4j 1.x", "__imports__", Pattern.compile("^org\\.apache\\.log4j\\."), 10, null),
            new Signal("LOG4J_2", "Log4j 2", "__imports__", Pattern.compile("^org\\.apache\\.logging\\.log4j"), 10, null),
            new Signal("SLF4J", "SLF4J", "__imports__", Pattern.compile("^org\\.slf4j"), 5, null)
    );

    /**
     * @param files      all files in the repo (with content for text files)
     * @param allImports flattened list of every import statement (with the "import " and ";" stripped),
     *                   e.g. "org.apache.struts.action.Action".
     */
    public List<Detected> fingerprint(List<RepoFile> files, List<String> allImports) {
        Map<String, Aggregate> byKey = new LinkedHashMap<>();

        // Index files by base name for quick lookup
        Map<String, RepoFile> byName = new HashMap<>();
        for (RepoFile f : files) {
            byName.putIfAbsent(baseName(f.getPath()).toLowerCase(), f);
        }

        for (Signal s : SIGNALS) {
            if (s.source.equals("__imports__")) {
                long matches = allImports.stream().filter(i -> s.pattern.matcher(i).find()).count();
                if (matches > 0) {
                    Aggregate agg = byKey.computeIfAbsent(s.key, k -> new Aggregate(s.key, s.name));
                    agg.score += Math.min(60, s.weight + (int) Math.min(30, matches / 3));
                    agg.evidence.add(matches + " import(s) matching " + s.pattern.pattern());
                }
            } else {
                RepoFile file = byName.get(s.source.toLowerCase());
                if (file == null || file.getContent() == null) continue;
                if (s.pattern.matcher(file.getContent()).find()) {
                    Aggregate agg = byKey.computeIfAbsent(s.key, k -> new Aggregate(s.key, s.name));
                    agg.score += s.weight;
                    agg.evidence.add(s.source + " matches " + s.pattern.pattern());
                    if (s.versionPattern != null) {
                        var m = Pattern.compile(s.versionPattern).matcher(file.getContent());
                        if (m.find()) agg.version = m.group(1);
                    }
                }
            }
        }

        return byKey.values().stream()
                .sorted(Comparator.<Aggregate>comparingInt(a -> a.score).reversed())
                .map(a -> new Detected(
                        a.key, a.name, a.version,
                        Math.min(100, a.score),
                        List.copyOf(a.evidence)
                ))
                .toList();
    }

    private static String baseName(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private record Signal(String key, String name, String source, Pattern pattern, int weight, String versionPattern) {}

    private static class Aggregate {
        final String key;
        final String name;
        String version;
        int score;
        final List<String> evidence = new ArrayList<>();
        Aggregate(String key, String name) { this.key = key; this.name = name; }
    }
}
