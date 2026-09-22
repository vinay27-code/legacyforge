package com.legacyforge.rag;

import com.legacyforge.common.ApiException;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.repo.RepoRepository;
import com.legacyforge.rag.dto.IndexStatus;
import com.legacyforge.rag.dto.SearchHit;
import com.legacyforge.rag.dto.SearchRequest;
import com.legacyforge.rag.repo.CodeChunkRepository;
import com.legacyforge.rag.service.EmbeddingProvider;
import com.legacyforge.rag.service.IndexingService;
import com.legacyforge.rag.service.SearchService;
import com.legacyforge.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos/{repoId}")
public class SearchController {

    private final IndexingService indexing;
    private final SearchService search;
    private final CodeChunkRepository chunks;
    private final RepoRepository repos;
    private final EmbeddingProvider embedder;

    public SearchController(IndexingService indexing, SearchService search,
                            CodeChunkRepository chunks, RepoRepository repos,
                            EmbeddingProvider embedder) {
        this.indexing = indexing;
        this.search = search;
        this.chunks = chunks;
        this.repos = repos;
        this.embedder = embedder;
    }

    @PostMapping("/index")
    public ResponseEntity<IndexStatus> reindex(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID repoId
    ) {
        Repo repo = loadOwned(me.getId(), repoId);
        indexing.reindex(repo);
        return ResponseEntity.status(HttpStatus.CREATED).body(status(repo.getId()));
    }

    @GetMapping("/index")
    public IndexStatus status(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID repoId
    ) {
        Repo repo = loadOwned(me.getId(), repoId);
        return status(repo.getId());
    }

    @PostMapping("/search")
    public List<SearchHit> search(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID repoId,
            @Valid @RequestBody SearchRequest req
    ) {
        Repo repo = loadOwned(me.getId(), repoId);
        return search.search(repo.getId(), req.query(), req.limit());
    }

    private IndexStatus status(UUID repoId) {
        return new IndexStatus(repoId, chunks.countByRepoId(repoId), embedder.describe());
    }

    private Repo loadOwned(UUID userId, UUID repoId) {
        return repos.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Repo not found"));
    }
}
