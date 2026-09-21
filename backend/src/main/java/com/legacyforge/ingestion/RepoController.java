package com.legacyforge.ingestion;

import com.legacyforge.common.ApiException;
import com.legacyforge.ingestion.dto.FileContent;
import com.legacyforge.ingestion.dto.FileTreeNode;
import com.legacyforge.ingestion.dto.GithubIngestRequest;
import com.legacyforge.ingestion.dto.RepoSummary;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.entity.RepoFile;
import com.legacyforge.ingestion.repo.RepoFileMetadata;
import com.legacyforge.ingestion.repo.RepoFileRepository;
import com.legacyforge.ingestion.repo.RepoRepository;
import com.legacyforge.ingestion.service.IngestionService;
import com.legacyforge.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos")
public class RepoController {

    private final IngestionService ingestion;
    private final RepoRepository repos;
    private final RepoFileRepository files;

    public RepoController(IngestionService ingestion, RepoRepository repos, RepoFileRepository files) {
        this.ingestion = ingestion;
        this.repos = repos;
        this.files = files;
    }

    @GetMapping
    public List<RepoSummary> list(@AuthenticationPrincipal UserPrincipal me) {
        return repos.findByUserIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(RepoSummary::from)
                .toList();
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<RepoSummary> uploadZip(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam("file") MultipartFile file
    ) {
        Repo repo = ingestion.ingestZip(me.getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(RepoSummary.from(repo));
    }

    @PostMapping("/github")
    public ResponseEntity<RepoSummary> ingestGithub(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody GithubIngestRequest req
    ) {
        Repo repo = ingestion.ingestGithub(me.getId(), req.url());
        return ResponseEntity.status(HttpStatus.CREATED).body(RepoSummary.from(repo));
    }

    @GetMapping("/{id}")
    public RepoSummary get(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID id
    ) {
        return RepoSummary.from(loadOwned(me.getId(), id));
    }

    @GetMapping("/{id}/tree")
    public FileTreeNode tree(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID id
    ) {
        Repo repo = loadOwned(me.getId(), id);
        List<RepoFileMetadata> meta = files.findMetadataByRepoId(repo.getId());
        return FileTreeNode.buildTree(meta);
    }

    @GetMapping("/{id}/file")
    public FileContent fileContent(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID id,
            @RequestParam String path
    ) {
        Repo repo = loadOwned(me.getId(), id);
        RepoFile f = files.findByRepoIdAndPath(repo.getId(), path)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File not found"));
        return new FileContent(f.getPath(), f.getSizeBytes(), f.getLanguage(), f.isBinary(), f.getContent());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID id
    ) {
        Repo repo = loadOwned(me.getId(), id);
        repos.delete(repo);
        return ResponseEntity.noContent().build();
    }

    private Repo loadOwned(UUID userId, UUID repoId) {
        return repos.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Repo not found"));
    }
}
