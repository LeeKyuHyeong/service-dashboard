CREATE TABLE IF NOT EXISTS project (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    tech_stack  TEXT,
    demo_url    VARCHAR(500),
    github_url  VARCHAR(500),
    thumbnail_url VARCHAR(500),
    sort_order  INT DEFAULT 0,
    visible     BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 기존 DB 마이그레이션 (컬럼이 없을 때만 추가)
ALTER TABLE project ADD COLUMN IF NOT EXISTS visible BOOLEAN DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS project_achievement (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id   BIGINT NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    metric_value VARCHAR(255),
    sort_order   INT DEFAULT 0,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES project(id)
);
