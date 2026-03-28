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
    external_id UUID UNIQUE,
    status user_status DEFAULT 'ACTIVE',
    address address_type,
    age integer,
    preferences JSONB,
    position GEOMETRY(Point, 4326),
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

CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_group_id ON users(group_id);
CREATE INDEX idx_contact_info_user_id ON contact_info(user_id);
CREATE INDEX idx_contact_info_type ON contact_info(contact_type);

-- Test tables for each data type
CREATE TABLE data_type_long (
    id BIGSERIAL PRIMARY KEY,
    data BIGINT
);

CREATE TABLE data_type_integer (
    id BIGSERIAL PRIMARY KEY,
    data INTEGER
);

CREATE TABLE data_type_double (
    id BIGSERIAL PRIMARY KEY,
    data DOUBLE PRECISION
);

CREATE TABLE data_type_float (
    id BIGSERIAL PRIMARY KEY,
    data REAL
);

CREATE TABLE data_type_short (
    id BIGSERIAL PRIMARY KEY,
    data SMALLINT
);

CREATE TABLE data_type_byte (
    id BIGSERIAL PRIMARY KEY,
    data SMALLINT
);

CREATE TABLE data_type_big_decimal (
    id BIGSERIAL PRIMARY KEY,
    data NUMERIC(19, 10)
);

CREATE TABLE data_type_big_integer (
    id BIGSERIAL PRIMARY KEY,
    data BIGINT
);

CREATE TABLE data_type_boolean (
    id BIGSERIAL PRIMARY KEY,
    data BOOLEAN
);

CREATE TABLE data_type_string (
    id BIGSERIAL PRIMARY KEY,
    data VARCHAR(255)
);

CREATE TABLE data_type_uuid (
    id BIGSERIAL PRIMARY KEY,
    data UUID
);

CREATE TABLE data_type_local_date (
    id BIGSERIAL PRIMARY KEY,
    data DATE
);

CREATE TABLE data_type_local_datetime (
    id BIGSERIAL PRIMARY KEY,
    data TIMESTAMP
);

CREATE TABLE data_type_byte_array (
    id BIGSERIAL PRIMARY KEY,
    data BYTEA
);
