-- Test schema initialization for MariaDB
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
    age BIGINT,
    preferences JSON,
    position POINT,
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

-- Test tables for each data type
CREATE TABLE data_type_long (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data BIGINT
);

CREATE TABLE data_type_integer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data INT
);

CREATE TABLE data_type_double (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data DOUBLE
);

CREATE TABLE data_type_float (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data FLOAT
);

CREATE TABLE data_type_short (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data SMALLINT
);

CREATE TABLE data_type_byte (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data TINYINT
);

CREATE TABLE data_type_big_decimal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data DECIMAL(19, 10)
);

CREATE TABLE data_type_big_integer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data BIGINT
);

CREATE TABLE data_type_boolean (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data BOOLEAN
);

CREATE TABLE data_type_string (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data VARCHAR(255)
);

CREATE TABLE data_type_uuid (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data CHAR(36)
);

CREATE TABLE data_type_local_date (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data DATE
);

CREATE TABLE data_type_local_datetime (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data DATETIME
);

CREATE TABLE data_type_byte_array (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data LONGBLOB
);
