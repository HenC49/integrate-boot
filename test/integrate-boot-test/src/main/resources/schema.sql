CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(64),
    age INT
);

-- Object metadata of the sample app's file storage (see FileMetadataStore): the
-- business-owned half of integrate-boot-oss, user metadata kept as a JSON string.
CREATE TABLE IF NOT EXISTS `oss_object` (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    content_type VARCHAR(200),
    content_length BIGINT NOT NULL,
    checksum VARCHAR(64),
    directory VARCHAR(200),
    metadata TEXT,
    storage VARCHAR(50),
    created_at TIMESTAMP
);
