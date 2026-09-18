package com.classroom.ai;

import com.classroom.ai.config.UploadPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadPathsTest {
    @TempDir Path project;

    @Test
    void rootAndBackendLaunchesUseTheSameSharedPictures() throws Exception {
        Files.createDirectory(project.resolve("vision"));
        Files.createDirectory(project.resolve("backend"));
        Path expected = project.resolve("runtime/uploads/faces");
        assertEquals(expected, UploadPaths.resolve(project, ""));
        assertEquals(expected, UploadPaths.resolve(project.resolve("backend"), ""));
    }

    @Test
    void explicitAbsoluteAndRelativeUploadLocationsAreRespected() {
        Path custom = project.resolve("custom");
        assertEquals(custom, UploadPaths.resolve(project, custom.toString()));
        assertEquals(custom, UploadPaths.resolve(project, "custom"));
    }
}
