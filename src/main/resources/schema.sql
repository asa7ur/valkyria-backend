SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS performances;
DROP TABLE IF EXISTS artist_images;
DROP TABLE IF EXISTS artists;
DROP TABLE IF EXISTS sponsor_stage;
DROP TABLE IF EXISTS stages;
DROP TABLE IF EXISTS sponsors;
DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS ticket_types;
DROP TABLE IF EXISTS campings;
DROP TABLE IF EXISTS camping_types;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS verification_tokens;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. USUARIOS Y SEGURIDAD
CREATE TABLE users
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    email         VARCHAR(100) UNIQUE NOT NULL,
    password      VARCHAR(255),
    enabled       BOOLEAN   DEFAULT TRUE,
    first_name    VARCHAR(100)        NOT NULL,
    last_name     VARCHAR(100),
    birth_date    DATE,
    phone         VARCHAR(30),
    created_date  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    auth_provider VARCHAR(20)  DEFAULT 'LOCAL',
    provider_id   VARCHAR(255)
);

CREATE TABLE roles
(
    id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE user_role
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE verification_tokens
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    token         VARCHAR(255) NOT NULL UNIQUE,
    user_id       BIGINT       NOT NULL,
    expiry_date   TIMESTAMP    NOT NULL,
    pending_email VARCHAR(100),
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 2. VENTAS Y COMPRAS
CREATE TABLE orders
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_date  TIMESTAMP                             DEFAULT CURRENT_TIMESTAMP,
    total_price DECIMAL(10, 2) NOT NULL,
    status      ENUM ('PENDING', 'PAID', 'CANCELLED') DEFAULT 'PENDING',
    user_id     BIGINT,
    guest_email VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE ticket_types
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(50)    NOT NULL,
    name_en         VARCHAR(50),
    price           DECIMAL(10, 2) NOT NULL,
    stock_total     INT            NOT NULL,
    stock_available INT            NOT NULL
);

CREATE TABLE tickets
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name      VARCHAR(100)                    NOT NULL,
    last_name       VARCHAR(100)                    NOT NULL,
    document_type   ENUM ('DNI', 'NIE', 'PASSPORT') NOT NULL,
    document_number VARCHAR(20)                     NOT NULL,
    birth_date      DATE                            NOT NULL,
    qr_code         VARCHAR(255) UNIQUE,
    status          ENUM ('ACTIVE', 'USED', 'CANCELLED') DEFAULT 'ACTIVE',
    ticket_type_id  BIGINT,
    order_id        BIGINT,
    FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE TABLE camping_types
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(50)    NOT NULL,
    name_en         VARCHAR(50),
    price           DECIMAL(10, 2) NOT NULL,
    stock_total     INT            NOT NULL,
    stock_available INT            NOT NULL
);

CREATE TABLE campings
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name      VARCHAR(100)                    NOT NULL,
    last_name       VARCHAR(100)                    NOT NULL,
    document_type   ENUM ('DNI', 'NIE', 'PASSPORT') NOT NULL,
    document_number VARCHAR(20)                     NOT NULL,
    birth_date      DATE                            NOT NULL,
    qr_code         VARCHAR(255) UNIQUE,
    status          ENUM ('ACTIVE', 'USED', 'CANCELLED') DEFAULT 'ACTIVE',
    camping_type_id BIGINT,
    order_id        BIGINT,
    FOREIGN KEY (camping_type_id) REFERENCES camping_types (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- 3. LOGÍSTICA DEL FESTIVAL
CREATE TABLE sponsors
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100)   NOT NULL UNIQUE,
    phone        VARCHAR(20)    NOT NULL,
    email        VARCHAR(100)   NOT NULL,
    contribution DECIMAL(10, 2) NOT NULL,
    image        VARCHAR(255)
);

CREATE TABLE stages
(
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL UNIQUE,
    name_en  VARCHAR(100),
    capacity BIGINT       NOT NULL
);

CREATE TABLE sponsor_stage
(
    sponsor_id BIGINT,
    stage_id   BIGINT,
    PRIMARY KEY (sponsor_id, stage_id),
    FOREIGN KEY (sponsor_id) REFERENCES sponsors (id),
    FOREIGN KEY (stage_id) REFERENCES stages (id)
);

CREATE TABLE artists
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    phone         VARCHAR(20)  NOT NULL,
    email         VARCHAR(100) NOT NULL,
    genre         VARCHAR(100) NOT NULL,
    country       VARCHAR(100) NOT NULL,
    description   TEXT,
    logo          VARCHAR(255),
    official_url  VARCHAR(255),
    instagram_url VARCHAR(255),
    tiktok_url    VARCHAR(255),
    youtube_url   VARCHAR(255),
    tidal_url     VARCHAR(255),
    spotify_url   VARCHAR(255)
);

CREATE TABLE artist_images
(
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    image_url VARCHAR(255) NOT NULL,
    artist_id BIGINT       NOT NULL,
    FOREIGN KEY (artist_id) REFERENCES artists (id) ON DELETE CASCADE
);

CREATE TABLE performances
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    start_time DATETIME NOT NULL,
    end_time   DATETIME NOT NULL,
    artist_id  BIGINT   NOT NULL,
    stage_id   BIGINT   NOT NULL,
    FOREIGN KEY (artist_id) REFERENCES artists (id),
    FOREIGN KEY (stage_id) REFERENCES stages (id)
);