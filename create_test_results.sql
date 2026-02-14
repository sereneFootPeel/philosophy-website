-- 测试结果表 test_results（与 TestResult 实体对应）
USE philosophy_db;

CREATE TABLE IF NOT EXISTS test_results (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    test_type VARCHAR(50) NOT NULL,
    result_summary VARCHAR(200) NOT NULL,
    result_json TEXT,
    is_public TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    INDEX idx_test_results_user_id (user_id),
    INDEX idx_test_results_user_created (user_id, created_at),
    CONSTRAINT fk_test_results_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
