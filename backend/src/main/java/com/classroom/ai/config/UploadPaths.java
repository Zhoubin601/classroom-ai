package com.classroom.ai.config;

import java.nio.file.Files;
import java.nio.file.Path;

/** Shared disk location for uploads and their HTTP resource mapping. */
public final class UploadPaths {
    private UploadPaths() {}

    public static Path resolve(String configured) {
        return resolve(Path.of(""), configured);
    }

    public static Path resolve(Path workingDirectory, String configured) {
        Path root = workingDirectory.toAbsolutePath().normalize();
        if (configured != null && !configured.isBlank()) {
            return root.resolve(configured).normalize();
        }
        // Support both the project launcher and Maven/IDE runs inside backend/.
        if (root.getFileName() != null && root.getFileName().toString().equals("backend")
                && root.getParent() != null && Files.isDirectory(root.getParent().resolve("vision"))) {
            root = root.getParent();
        }
        return root.resolve("runtime/uploads/faces");
    }
}
