-- Insert projects (MariaDB)
INSERT IGNORE INTO project (id, name, slug, description, tech_stack, demo_url, github_url, thumbnail_url, sort_order, visible, created_at, updated_at) VALUES
(1, 'ITSM', 'itsm', '실제 사용자 없이 임의로 데이터를 주입해 트래픽이 발생하는 것처럼 시뮬레이션하는 학습용 사이트. 의도적으로 트래픽 폭주와 메모리 장애를 유발하며 트래픽 관리와 장애 대응을 공부합니다.',
 '["Vue.js", "Spring Boot", "MariaDB", "Docker"]',
 'https://itsm.kyuhyeong.com', 'https://github.com/LeeKyuHyeong/itsm', '/thumbnails/itsm.png', 1, FALSE, NOW(), NOW()),
(2, 'Song Quiz', 'song-quiz', '실시간 멀티플레이어 노래 맞추기 게임. 짧은 음악 클립을 듣고 누가 가장 빠르게 곡을 맞추는지 겨루는 게임입니다.',
 '["React", "Spring Boot", "WebSocket", "Redis", "Docker"]',
 'https://game.kyuhyeong.com', 'https://github.com/LeeKyuHyeong/quiz', '/thumbnails/song-quiz.png', 2, TRUE, NOW(), NOW()),
-- id 3 은 구 kh-shop 자리 (2026-07-23 서비스 종료, 저장소는 GitHub Archive 로 보존) — account 가 승계
(3, 'Account', 'account', '부부/가구 단위 가계부 앱. 영수증 사진을 Claude Vision 으로 분석해 지출을 자동 분류·기록합니다.',
 '["Spring Boot", "MariaDB", "Docker", "Claude Vision"]',
 'https://account.kyuhyeong.com', 'https://github.com/LeeKyuHyeong/account', NULL, 3, TRUE, NOW(), NOW());

-- Insert achievements
INSERT IGNORE INTO project_achievement (id, project_id, title, description, metric_value, sort_order, created_at) VALUES
(1, 1, '트래픽 시뮬레이션', '실제 사용자 없이 임의 데이터를 주기적으로 주입해 실서비스와 유사한 트래픽 패턴을 재현', NULL, 1, NOW()),
(2, 1, '의도적 장애 유발 실험', '트래픽 폭주와 메모리 누수를 인위적으로 발생시켜 장애 상황을 관찰하고 트래픽 관리·대응 방법을 학습', NULL, 2, NOW()),
(3, 2, '실시간 멀티플레이어', 'WebSocket 기반 실시간 게임 세션으로 동시 접속 플레이어 지원', '최대 8명', 1, NOW()),
(4, 2, '오디오 스트리밍', '프리로딩을 활용한 효율적인 음악 클립 스트리밍으로 끊김 없는 게임 플레이 제공', NULL, 2, NOW()),
-- id 5, 6 은 구 kh-shop 성과 자리 — 5 를 account 가 승계
(5, 3, '영수증 자동 분류', 'Claude Vision 으로 영수증 사진을 분석해 품목·금액·카테고리를 자동 입력', NULL, 1, NOW());
