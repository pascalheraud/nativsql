-- Test schema initialization for MySQL
-- Creates all necessary types and tables for testing

CREATE TABLE user_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    external_id CHAR(36) UNIQUE,
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED'),
    address JSON,
    preferences JSON,
    group_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES user_group(id) ON DELETE SET NULL
);

CREATE TABLE contact_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contact_type ENUM('EMAIL', 'PHONE', 'FACEBOOK', 'TWITTER', 'LINKEDIN', 'WEBSITE') NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_contact_info_user_id (user_id),
    INDEX idx_contact_info_type (contact_type)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_group_id ON users(group_id);
