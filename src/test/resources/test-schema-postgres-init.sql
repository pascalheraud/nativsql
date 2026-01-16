-- Test schema initialization
-- Creates all necessary types and tables for testing

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');
CREATE TYPE contact_type AS ENUM ('EMAIL', 'PHONE', 'FACEBOOK', 'TWITTER', 'LINKEDIN', 'WEBSITE');
CREATE TYPE address_type AS (street VARCHAR(255), city VARCHAR(100), postal_code VARCHAR(20), country VARCHAR(100));

CREATE TABLE user_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    status user_status DEFAULT 'ACTIVE',
    address address_type,
    preferences JSONB,
    location GEOMETRY(Point, 4326),
    group_id BIGINT REFERENCES user_group(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE contact_info (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    contact_type contact_type NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_group_id ON users(group_id);
CREATE INDEX idx_contact_info_user_id ON contact_info(user_id);
CREATE INDEX idx_contact_info_type ON contact_info(contact_type);
