package com.github.henc.test.file.domain;

import com.github.henc.integrateboot.oss.OssMetadataStore;
import com.github.henc.integrateboot.oss.OssObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The business-side {@link OssMetadataStore}: object metadata in a table of the primary
 * database, user metadata as a JSON column. This is the piece integrate-boot-oss
 * deliberately leaves to the service — registering this bean is what activates the
 * auto-configured filesystem storage.
 */
@Repository
public class FileMetadataStore implements OssMetadataStore {

    private static final String COLUMNS =
            "id, name, content_type, content_length, checksum, directory, metadata, storage, created_at";

    private static final TypeReference<Map<String, String>> METADATA_TYPE =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FileMetadataStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(OssObject object) {
        jdbcTemplate.update(
                "insert into oss_object (" + COLUMNS + ") values (?,?,?,?,?,?,?,?,?)",
                object.id(), object.name(), object.contentType(), object.contentLength(),
                object.checksum(), object.directory(), toJson(object.metadata()),
                object.storage(), Timestamp.from(object.createdAt()));
    }

    @Override
    public Optional<OssObject> find(String objectId) {
        List<OssObject> hits = jdbcTemplate.query(
                "select " + COLUMNS + " from oss_object where id = ?", rowMapper(), objectId);
        return hits.stream().findFirst();
    }

    @Override
    public void delete(String objectId) {
        jdbcTemplate.update("delete from oss_object where id = ?", objectId);
    }

    private RowMapper<OssObject> rowMapper() {
        return (rs, rowNum) -> new OssObject(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("content_type"),
                rs.getLong("content_length"),
                rs.getString("checksum"),
                rs.getString("directory"),
                fromJson(rs.getString("metadata")),
                rs.getString("storage"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String toJson(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize object metadata", e);
        }
    }

    private Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, METADATA_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("failed to deserialize object metadata", e);
        }
    }
}
