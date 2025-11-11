-- Philosophy Website Database Schema
-- 完整的数据库架构文件，基于实际数据库结构重写
-- WARNING: DO NOT commit database passwords to version control

-- 创建数据库
CREATE DATABASE IF NOT EXISTS philosophy_db;
USE philosophy_db;

-- =============================================
-- 核心表结构
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    role VARCHAR(255) NOT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    enabled TINYINT(1) DEFAULT 1,
    account_locked BIT(1) NOT NULL DEFAULT 0,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    lock_time DATETIME(6) DEFAULT NULL,
    lock_expire_time DATETIME(6) DEFAULT NULL,
    ip_address VARCHAR(255) DEFAULT NULL,
    device_type VARCHAR(255) DEFAULT NULL,
    user_agent VARCHAR(255) DEFAULT NULL,
    admin_login_attempts INT NOT NULL DEFAULT 0,
    comments_private BIT(1) NOT NULL DEFAULT 0,
    contents_private BIT(1) NOT NULL DEFAULT 0,
    avatar_url VARCHAR(500) DEFAULT NULL,
    like_count INT NOT NULL DEFAULT 0,
    assigned_school_id BIGINT DEFAULT NULL,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_role (role),
    INDEX idx_enabled (enabled),
    INDEX idx_users_assigned_school_id (assigned_school_id),
    INDEX idx_users_like_count (like_count),
    FOREIGN KEY (assigned_school_id) REFERENCES schools(id) ON DELETE SET NULL
);

-- 哲学流派表
CREATE TABLE IF NOT EXISTS schools (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parent_id BIGINT DEFAULT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    description_en TEXT,
    name_en VARCHAR(255),
    like_count INT NOT NULL DEFAULT 0,
    user_id BIGINT DEFAULT NULL,
    INDEX idx_name (name),
    INDEX idx_parent_id (parent_id),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at),
    INDEX idx_schools_like_count (like_count),
    INDEX idx_schools_user_id (user_id),
    FOREIGN KEY (parent_id) REFERENCES schools(id) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- 哲学家表
CREATE TABLE IF NOT EXISTS philosophers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    birth_year INT DEFAULT NULL,
    image_url VARCHAR(500) DEFAULT NULL,
    bio TEXT,
    created_at DATETIME(6) DEFAULT NULL,
    era VARCHAR(255) DEFAULT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    biography TEXT,
    death_year INT DEFAULT NULL,
    nationality VARCHAR(255) DEFAULT NULL,
    bio_en TEXT,
    name_en VARCHAR(255),
    like_count INT NOT NULL DEFAULT 0,
    user_id BIGINT DEFAULT NULL,
    INDEX idx_name (name),
    INDEX idx_birth_year (birth_year),
    INDEX idx_era (era),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at),
    INDEX idx_philosophers_like_count (like_count),
    INDEX idx_philosophers_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- 内容表
CREATE TABLE IF NOT EXISTS contents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT DEFAULT NULL,
    philosopher_id BIGINT DEFAULT NULL,
    content MEDIUMTEXT,
    order_index INT DEFAULT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    content_en MEDIUMTEXT,
    like_count INT NOT NULL DEFAULT 0,
    user_id BIGINT DEFAULT NULL,
    locked_by_user_id BIGINT DEFAULT NULL,
    locked_at DATETIME(6) DEFAULT NULL,
    title VARCHAR(255) DEFAULT NULL,
    is_private BIT(1) NOT NULL DEFAULT 0,
    privacy_set_by BIGINT DEFAULT NULL,
    privacy_set_at DATETIME(6) DEFAULT NULL,
    status INT NOT NULL DEFAULT 0,
    is_blocked BIT(1) NOT NULL DEFAULT 0,
    blocked_by BIGINT DEFAULT NULL,
    blocked_at DATETIME(6) DEFAULT NULL,
    INDEX idx_school_id (school_id),
    INDEX idx_philosopher_id (philosopher_id),
    INDEX idx_order_index (order_index),
    INDEX idx_contents_like_count (like_count),
    INDEX idx_contents_user_id (user_id),
    INDEX idx_contents_locked_by_user_id (locked_by_user_id),
    INDEX idx_contents_is_private (is_private),
    INDEX idx_contents_privacy_set_by (privacy_set_by),
    INDEX idx_contents_status (status),
    INDEX idx_contents_is_blocked (is_blocked),
    INDEX idx_contents_blocked_by (blocked_by),
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE SET NULL,
    FOREIGN KEY (philosopher_id) REFERENCES philosophers(id) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (locked_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (privacy_set_by) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (blocked_by) REFERENCES users(id) ON DELETE SET NULL
);

-- 评论表
CREATE TABLE IF NOT EXISTS comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    body TEXT NOT NULL,
    user_id BIGINT DEFAULT NULL,
    content_id BIGINT DEFAULT NULL,
    parent_id BIGINT DEFAULT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    is_private BIT(1) NOT NULL DEFAULT 0,
    privacy_set_at DATETIME(6) DEFAULT NULL,
    privacy_set_by BIGINT DEFAULT NULL,
    like_count INT NOT NULL DEFAULT 0,
    status INT NOT NULL DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_created_at (created_at),
    INDEX idx_comments_is_private (is_private),
    INDEX idx_comments_privacy_set_by (privacy_set_by),
    INDEX idx_comments_like_count (like_count),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE,
    FOREIGN KEY (privacy_set_by) REFERENCES users(id) ON DELETE SET NULL
);

-- =============================================
-- 关联表
-- =============================================

-- 用户屏蔽表
CREATE TABLE IF NOT EXISTS user_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_id BIGINT NOT NULL COMMENT 'Blocker user ID',
    blocked_id BIGINT NOT NULL COMMENT 'Blocked user ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_block_relationship (blocker_id, blocked_id),
    INDEX idx_blocker_id (blocker_id),
    INDEX idx_blocked_id (blocked_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 版主屏蔽用户表
CREATE TABLE IF NOT EXISTS moderator_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    moderator_id BIGINT NOT NULL COMMENT 'Moderator user ID',
    blocked_user_id BIGINT NOT NULL COMMENT 'Blocked user ID',
    school_id BIGINT NOT NULL COMMENT 'School ID',
    reason TEXT COMMENT 'Block reason',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_moderator_block (moderator_id, blocked_user_id, school_id),
    INDEX idx_moderator_id (moderator_id),
    INDEX idx_blocked_user_id (blocked_user_id),
    INDEX idx_school_id (school_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (moderator_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (blocked_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);

-- 哲学家-流派关联表
CREATE TABLE IF NOT EXISTS philosopher_school (
    philosopher_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    PRIMARY KEY (philosopher_id, school_id),
    FOREIGN KEY (philosopher_id) REFERENCES philosophers(id) ON DELETE CASCADE,
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);

-- 点赞表
CREATE TABLE IF NOT EXISTS likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    entity_type ENUM('COMMENT', 'CONTENT', 'PHILOSOPHER', 'SCHOOL', 'USER') NOT NULL,
    entity_id BIGINT NOT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    UNIQUE KEY unique_user_entity (user_id, entity_type, entity_id),
    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_user (user_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 用户关注表
CREATE TABLE IF NOT EXISTS user_follows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    UNIQUE KEY unique_follow (follower_id, following_id),
    INDEX idx_follower (follower_id),
    INDEX idx_following (following_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 用户内容编辑表
CREATE TABLE IF NOT EXISTS user_content_edits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_notes TEXT,
    content TEXT NOT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    status ENUM('APPROVED', 'PENDING', 'REJECTED') NOT NULL,
    title VARCHAR(255) NOT NULL,
    updated_at DATETIME(6) DEFAULT NULL,
    original_content_id BIGINT DEFAULT NULL,
    philosopher_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content_en TEXT,
    INDEX idx_user_id (user_id),
    INDEX idx_original_content_id (original_content_id),
    INDEX idx_status (status),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (original_content_id) REFERENCES contents(id) ON DELETE CASCADE,
    FOREIGN KEY (philosopher_id) REFERENCES philosophers(id) ON DELETE CASCADE,
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);

-- 用户登录信息表
CREATE TABLE IF NOT EXISTS user_login_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    browser VARCHAR(50) DEFAULT NULL,
    device_type VARCHAR(50) DEFAULT NULL,
    ip_address VARCHAR(45) NOT NULL,
    login_time DATETIME(6) DEFAULT NULL,
    operating_system VARCHAR(50) DEFAULT NULL,
    user_agent TEXT,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(128) DEFAULT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_login_time (login_time),
    INDEX idx_user_login_info_device_id (device_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- =============================================
-- 翻译表
-- =============================================

-- 流派翻译表
CREATE TABLE IF NOT EXISTS schools_translation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL DEFAULT 'en',
    name_en VARCHAR(100) NOT NULL,
    description_en TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_school_language (school_id, language_code),
    INDEX idx_school_id (school_id),
    INDEX idx_language_code (language_code),
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE
);

-- 内容翻译表
CREATE TABLE IF NOT EXISTS contents_translation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL DEFAULT 'en',
    content_en MEDIUMTEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_content_language (content_id, language_code),
    INDEX idx_content_id (content_id),
    INDEX idx_language_code (language_code),
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);

-- 哲学家翻译表
CREATE TABLE IF NOT EXISTS philosophers_translation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    philosopher_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL DEFAULT 'en',
    name_en VARCHAR(100) NOT NULL,
    biography_en TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_philosopher_language (philosopher_id, language_code),
    INDEX idx_philosopher_id (philosopher_id),
    INDEX idx_language_code (language_code),
    FOREIGN KEY (philosopher_id) REFERENCES philosophers(id) ON DELETE CASCADE
);

-- =============================================
-- 初始化数据
-- =============================================

-- 插入默认管理员用户
INSERT IGNORE INTO users (username, email, password, role, enabled, account_locked, failed_login_attempts, admin_login_attempts, comments_private, like_count) VALUES
('admin', 'admin@philosophy.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi', 'ADMIN', 1, 0, 0, 0, 0, 0);

-- 插入示例哲学流派
INSERT IGNORE INTO schools (name, description, like_count, user_id) VALUES
('Ancient Greek Philosophy', 'Philosophical thought from ancient Greece, including ideas from Socrates, Plato, Aristotle and other philosophers', 0, NULL),
('Stoicism', 'Philosophical school founded by Zeno, emphasizing reason, self-control and acceptance of fate', 0, NULL),
('Epicureanism', 'Philosophical school founded by Epicurus, emphasizing pleasure and avoiding pain', 0, NULL),
('Existentialism', '20th century philosophical movement focusing on the meaning of human existence and free choice', 0, NULL),
('Marxism', 'Philosophical system founded by Marx and Engels, emphasizing class struggle and social change', 0, NULL);

-- 插入示例哲学家
INSERT IGNORE INTO philosophers (name, birth_year, death_year, nationality, era, bio, like_count, user_id) VALUES
('Socrates', -470, -399, 'Ancient Greece', 'Classical Period', 'Ancient Greek philosopher, one of the founders of Western philosophy', 0, NULL),
('Plato', -428, -348, 'Ancient Greece', 'Classical Period', 'Ancient Greek philosopher, student of Socrates and teacher of Aristotle', 0, NULL),
('Aristotle', -384, -322, 'Ancient Greece', 'Classical Period', 'Ancient Greek philosopher, student of Plato and teacher of Alexander the Great', 0, NULL),
('Descartes', 1596, 1650, 'France', 'Modern Philosophy', 'French philosopher, mathematician, and physicist, father of modern philosophy', 0, NULL),
('Kant', 1724, 1804, 'Germany', 'Modern Philosophy', 'German philosopher, important thinker of the Enlightenment', 0, NULL);
