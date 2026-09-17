package com.github.henc.test;

import com.github.henc.integrateboot.oss.OssMetadataStore;
import com.github.henc.integrateboot.oss.OssObject;
import com.github.henc.integrateboot.oss.OssObjectNotFoundException;
import com.github.henc.integrateboot.oss.OssSaveRequest;
import com.github.henc.integrateboot.oss.OssStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end wiring of integrate-boot-oss: the auto-configured filesystem storage
 * coordinates with the sample app's JDBC {@link OssMetadataStore}, exercised both
 * directly through the facade and over HTTP through {@code FileController} (upload,
 * download, metadata, delete, and the 404 rendering of a missing object).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OssIT {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void ossProperties(DynamicPropertyRegistry registry) {
        registry.add("integrate-boot.oss.filesystem.root", () -> storageRoot);
    }

    @Autowired
    private OssStorage ossStorage;

    @Autowired
    private OssMetadataStore metadataStore;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void savePersistsContentUnderTheRootAndMetadataThroughTheBusinessStore() throws IOException {
        byte[] content = "oss integration".getBytes(StandardCharsets.UTF_8);

        OssObject stored = ossStorage.save(
                OssSaveRequest.builder().name("note.txt").directory("notes")
                        .metadata(Map.of("source", "it")).build(),
                new ByteArrayInputStream(content));

        // Content on the filesystem (sharded), metadata in the H2-backed business store.
        assertThat(storageRoot.resolve(stored.id().substring(0, 2)).resolve(stored.id()))
                .hasBinaryContent(content);
        assertThat(stored.contentType()).isEqualTo("text/plain");
        assertThat(stored.checksum()).hasSize(64);

        Optional<OssObject> reloaded = metadataStore.find(stored.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.orElseThrow().metadata()).containsEntry("source", "it");
        assertThat(reloaded.orElseThrow().directory()).isEqualTo("notes");
    }

    @Test
    void uploadDownloadMetadataAndDeleteOverHttp() throws Exception {
        byte[] content = "uploaded over mock mvc".getBytes(StandardCharsets.UTF_8);

        String body = mockMvc.perform(multipart("/files")
                        .file(new MockMultipartFile("file", "hello.txt", "text/plain", content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.name").value("hello.txt"))
                .andExpect(jsonPath("$.result.contentLength").value(content.length))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(body, "$.result.id");

        mockMvc.perform(get("/files/{id}", id))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(content));

        mockMvc.perform(get("/files/{id}/metadata", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.object.id").value(id))
                .andExpect(jsonPath("$.result.object.name").value("hello.txt"));

        mockMvc.perform(delete("/files/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // After the delete the download 404s in the shared ResultInfo envelope.
        mockMvc.perform(get("/files/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingObjectSurfacesAsPlatform404() {
        assertThatThrownBy(() -> ossStorage.openStream("does-not-exist"))
                .isInstanceOf(OssObjectNotFoundException.class)
                .satisfies(ex -> assertThat(((OssObjectNotFoundException) ex).getCode()).isEqualTo(404));
        assertThat(ossStorage.find("does-not-exist")).isEmpty();
    }
}
