package com.legacyforge.ingestion.dto;

import com.legacyforge.ingestion.repo.RepoFileMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Recursive tree node for the frontend file browser.
 * `type` is "dir" or "file". Directories carry children; files carry metadata.
 */
public record FileTreeNode(
        String name,
        String path,
        String type,
        Long sizeBytes,
        String language,
        Boolean binary,
        List<FileTreeNode> children
) {

    /** Build a tree from a flat list of file paths. */
    public static FileTreeNode buildTree(List<RepoFileMetadata> files) {
        DirBuilder root = new DirBuilder("", "");
        for (RepoFileMetadata f : files) {
            String[] parts = f.path().split("/");
            DirBuilder cursor = root;
            for (int i = 0; i < parts.length - 1; i++) {
                final DirBuilder parent = cursor;
                cursor = parent.dirs.computeIfAbsent(parts[i],
                        n -> new DirBuilder(n, joinPath(parent.path, n)));
            }
            String leaf = parts[parts.length - 1];
            cursor.files.add(new FileTreeNode(
                    leaf, f.path(), "file",
                    f.sizeBytes(), f.language(), f.binary(),
                    null
            ));
        }
        return root.toNode();
    }

    private static String joinPath(String parent, String child) {
        return parent.isEmpty() ? child : parent + "/" + child;
    }

    /** Mutable helper we throw away once the tree is built. */
    private static class DirBuilder {
        final String name;
        final String path;
        final Map<String, DirBuilder> dirs = new TreeMap<>();
        final List<FileTreeNode> files = new ArrayList<>();
        DirBuilder(String name, String path) { this.name = name; this.path = path; }

        FileTreeNode toNode() {
            List<FileTreeNode> children = new ArrayList<>();
            for (DirBuilder d : dirs.values()) children.add(d.toNode());
            files.sort(Comparator.comparing(FileTreeNode::name));
            children.addAll(files);
            return new FileTreeNode(
                    name, path, "dir",
                    null, null, null,
                    children
            );
        }
    }
}
