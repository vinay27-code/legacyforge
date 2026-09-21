package com.legacyforge.ingestion.service;

import com.legacyforge.common.ApiException;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.entity.RepoFile;
import com.legacyforge.ingestion.repo.RepoFileRepository;
import com.legacyforge.ingestion.repo.RepoRepository;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    /** Max size of a single file we will store as text. */
    private static final long MAX_FILE_TEXT_BYTES = 1024L * 1024L;
    /** Max files we ingest per repo. */
    private static final int MAX_FILES = 5000;
    /** Max total uncompressed bytes to protect against zip bombs. */
    private static final long MAX_TOTAL_BYTES = 200L * 1024L * 1024L;
    /** Folders and files we ignore during ingestion. */
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".idea", ".vscode",
            "__pycache__", ".gradle", "out", ".next", ".angular"
    );

    private static final Pattern GITHUB_URL = Pattern.compile(
            "^https?://github\\.com/[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+(?:\\.git)?/?$"
    );

    private final RepoRepository repos;
    private final RepoFileRepository repoFiles;
    private final LanguageDetector languageDetector;

    public IngestionService(RepoRepository repos, RepoFileRepository repoFiles, LanguageDetector languageDetector) {
        this.repos = repos;
        this.repoFiles = repoFiles;
        this.languageDetector = languageDetector;
    }

    /** Ingest an uploaded zip file. */
    @Transactional
    public Repo ingestZip(UUID userId, MultipartFile file) {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Empty upload");
        String name = stripZipExt(file.getOriginalFilename());
        Repo repo = repos.save(new Repo(userId, name, Repo.SourceType.ZIP, null));

        try (InputStream in = file.getInputStream();
             ZipInputStream zip = new ZipInputStream(in)) {
            IngestStats stats = ingestZipStream(repo, zip);
            markReady(repo, stats);
        } catch (ApiException e) {
            markFailed(repo, e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("Failed to ingest zip for repo {}", repo.getId(), e);
            markFailed(repo, "Failed to read zip: " + e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid zip file");
        }
        return repo;
    }

    /** Ingest a public GitHub repository via shallow clone. */
    @Transactional
    public Repo ingestGithub(UUID userId, String url) {
        String normalized = url.trim().replaceAll("/$", "");
        if (!GITHUB_URL.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "URL must look like https://github.com/OWNER/REPO");
        }
        String repoName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("\\.git$", "");
        Repo repo = repos.save(new Repo(userId, repoName, Repo.SourceType.GITHUB, normalized));

        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("legacyforge-clone-");
            try (Git ignored = Git.cloneRepository()
                    .setURI(normalized.endsWith(".git") ? normalized : normalized + ".git")
                    .setDirectory(tmpDir.toFile())
                    .setDepth(1)
                    .setCloneAllBranches(false)
                    .call()) {
                IngestStats stats = ingestDirectory(repo, tmpDir);
                markReady(repo, stats);
            }
        } catch (ApiException e) {
            markFailed(repo, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to clone {}", normalized, e);
            markFailed(repo, "Clone failed: " + e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Could not clone that GitHub repo. Is it public?");
        } finally {
            if (tmpDir != null) deleteQuietly(tmpDir);
        }
        return repo;
    }

    private IngestStats ingestZipStream(Repo repo, ZipInputStream zip) throws IOException {
        int fileCount = 0;
        long totalSize = 0L;
        String rootPrefix = null; // GitHub-style archives put everything under a top-level dir

        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;

            String rawName = entry.getName();
            if (containsPathTraversal(rawName)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Zip contains an unsafe entry: " + rawName);
            }

            // Detect and strip a common top-level directory (like "jpetstore-6-master/")
            if (rootPrefix == null) {
                int slash = rawName.indexOf('/');
                rootPrefix = slash > 0 ? rawName.substring(0, slash + 1) : "";
            }
            String path = rawName.startsWith(rootPrefix) ? rawName.substring(rootPrefix.length()) : rawName;
            if (path.isEmpty() || isIgnored(path)) continue;

            byte[] bytes = zip.readAllBytes();
            totalSize += bytes.length;
            if (totalSize > MAX_TOTAL_BYTES) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Uncompressed size exceeds the 200 MB cap");
            }
            if (persistOne(repo.getId(), path, bytes)) fileCount++;
            if (fileCount > MAX_FILES) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Repo exceeds the " + MAX_FILES + " file cap");
            }
        }
        return new IngestStats(fileCount, totalSize);
    }

    private IngestStats ingestDirectory(Repo repo, Path root) throws IOException {
        int[] fileCount = {0};
        long[] totalSize = {0L};

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                return IGNORED_DIRS.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = root.relativize(file);
                String path = rel.toString().replace('\\', '/');
                if (isIgnored(path)) return FileVisitResult.CONTINUE;

                byte[] bytes = Files.readAllBytes(file);
                totalSize[0] += bytes.length;
                if (totalSize[0] > MAX_TOTAL_BYTES) {
                    throw new UncheckedIOException(new IOException("Total size cap exceeded"));
                }
                if (persistOne(repo.getId(), path, bytes)) fileCount[0]++;
                if (fileCount[0] > MAX_FILES) {
                    throw new UncheckedIOException(new IOException("File count cap exceeded"));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return new IngestStats(fileCount[0], totalSize[0]);
    }

    /** Store one file, deciding text vs binary. Returns true if we saved a row. */
    private boolean persistOne(UUID repoId, String path, byte[] bytes) {
        boolean binary = languageDetector.isBinaryByExtension(path) || looksBinary(bytes);
        String content = null;
        if (!binary && bytes.length <= MAX_FILE_TEXT_BYTES) {
            content = new String(bytes, StandardCharsets.UTF_8);
        }
        String language = languageDetector.detectLanguage(path);
        String sha = sha256(bytes);
        RepoFile file = new RepoFile(repoId, path, bytes.length, language, sha, binary, content);
        repoFiles.save(file);
        return true;
    }

    private void markReady(Repo repo, IngestStats stats) {
        repo.setFileCount(stats.fileCount());
        repo.setTotalSizeBytes(stats.totalBytes());
        repo.setStatus(Repo.Status.READY);
        repos.save(repo);
        log.info("Ingested repo {}: {} files, {} bytes", repo.getId(), stats.fileCount(), stats.totalBytes());
    }

    private void markFailed(Repo repo, String message) {
        repo.setStatus(Repo.Status.FAILED);
        repo.setErrorMessage(message);
        repos.save(repo);
    }

    // --- helpers ---

    private static boolean containsPathTraversal(String name) {
        return name.contains("..") || name.startsWith("/") || name.startsWith("\\") || name.contains(":");
    }

    private static boolean isIgnored(String path) {
        for (String seg : path.split("/")) {
            if (IGNORED_DIRS.contains(seg)) return true;
        }
        return false;
    }

    /** Poor-man binary detection: null byte in the first 1 KB. */
    private static boolean looksBinary(byte[] bytes) {
        int end = Math.min(bytes.length, 1024);
        for (int i = 0; i < end; i++) if (bytes[i] == 0) return true;
        return false;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stripZipExt(String name) {
        if (name == null || name.isBlank()) return "repo";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void deleteQuietly(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.deleteIfExists(f); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    Files.deleteIfExists(d); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }

    private record IngestStats(int fileCount, long totalBytes) {}
}
