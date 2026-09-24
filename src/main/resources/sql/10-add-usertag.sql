USE ad_platform;

-- 素材标签，每行存储一个标签。
CREATE TABLE IF NOT EXISTS material_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tags VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_material_tags_tags (tags)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 素材与标签的多对多关联。
CREATE TABLE IF NOT EXISTS material_tags_relations (
    material_id BIGINT NOT NULL COMMENT '素材 ID，对应 material.id',
    tag_id BIGINT NOT NULL COMMENT '标签 ID，对应 material_tags.id',
    PRIMARY KEY (material_id, tag_id),
    KEY idx_material_tags_relations_tag_id (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
