-- ============================================================
-- SQL скрипт для создания БД eGov Mobile QR Sign Service
-- ============================================================
-- 
-- Этот скрипт создает новую БД с нуля с оптимизированной структурой
-- 
-- ВНИМАНИЕ: Этот скрипт удалит существующую БД с таким же именем!
-- ============================================================

-- Опционально: Удалить старую БД (раскомментируйте если нужно)
-- DROP DATABASE IF EXISTS egov_sign_db;

-- Создать новую БД
CREATE DATABASE egov_sign_db
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'Russian_Russia.1251'
    LC_CTYPE = 'Russian_Russia.1251'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Подключаемся к созданной БД
\c egov_sign_db

-- ============================================================
-- 1. Таблица организаций (справочник)
-- ============================================================
CREATE TABLE organisations (
    id BIGSERIAL PRIMARY KEY,
    bin VARCHAR(12) UNIQUE NOT NULL,
    name_ru VARCHAR(255),
    name_kz VARCHAR(255),
    name_en VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для organisations
CREATE INDEX idx_organisations_bin ON organisations(bin);

-- Комментарии
COMMENT ON TABLE organisations IS 'Справочник организаций';
COMMENT ON COLUMN organisations.bin IS 'БИН организации (уникальный)';
COMMENT ON COLUMN organisations.name_ru IS 'Название организации на русском';
COMMENT ON COLUMN organisations.name_kz IS 'Название организации на казахском';
COMMENT ON COLUMN organisations.name_en IS 'Название организации на английском';

-- ============================================================
-- 2. Таблица транзакций подписания
-- ============================================================
CREATE TABLE sign_transactions (
    transaction_id VARCHAR(255) PRIMARY KEY,
    organisation_id BIGINT,
    creation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expiry_date TIMESTAMP NOT NULL,
    auth_type VARCHAR(50) NOT NULL,
    auth_token_hash VARCHAR(255),
    description TEXT NOT NULL,
    api2_uri VARCHAR(512) NOT NULL,
    back_url VARCHAR(512) NOT NULL,
    status VARCHAR(50) NOT NULL,
    documents_to_sign JSONB,
    signed_documents JSONB,
    
    CONSTRAINT fk_organisation 
        FOREIGN KEY (organisation_id) 
        REFERENCES organisations(id)
        ON DELETE SET NULL
);

-- Индексы для sign_transactions
CREATE INDEX idx_transactions_status ON sign_transactions(status);
CREATE INDEX idx_transactions_expiry_date ON sign_transactions(expiry_date);
CREATE INDEX idx_transactions_creation_date ON sign_transactions(creation_date);
CREATE INDEX idx_transactions_organisation ON sign_transactions(organisation_id);

-- JSONB индексы для быстрого поиска внутри JSON
CREATE INDEX idx_documents_sign_method ON sign_transactions USING GIN ((documents_to_sign->'signMethod'));

-- Комментарии
COMMENT ON TABLE sign_transactions IS 'Транзакции подписания документов';
COMMENT ON COLUMN sign_transactions.transaction_id IS 'Уникальный ID транзакции (UUID)';
COMMENT ON COLUMN sign_transactions.organisation_id IS 'FK на организацию';
COMMENT ON COLUMN sign_transactions.auth_type IS 'Тип аутентификации: Token, Eds, None';
COMMENT ON COLUMN sign_transactions.auth_token_hash IS 'BCrypt хеш токена аутентификации';
COMMENT ON COLUMN sign_transactions.status IS 'Статус транзакции: PENDING, SIGNED, FAILED';
COMMENT ON COLUMN sign_transactions.documents_to_sign IS 'Документы для подписания (JSONB)';
COMMENT ON COLUMN sign_transactions.signed_documents IS 'Подписанные документы (JSONB)';

-- ============================================================
-- 3. Таблица истории изменения статусов
-- ============================================================
CREATE TABLE transaction_status_history (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_reason TEXT,
    
    CONSTRAINT fk_transaction 
        FOREIGN KEY (transaction_id) 
        REFERENCES sign_transactions(transaction_id)
        ON DELETE CASCADE
);

-- Индексы для transaction_status_history
CREATE INDEX idx_status_history_transaction ON transaction_status_history(transaction_id);
CREATE INDEX idx_status_history_changed_at ON transaction_status_history(changed_at);

-- Комментарии
COMMENT ON TABLE transaction_status_history IS 'История изменения статусов транзакций';
COMMENT ON COLUMN transaction_status_history.transaction_id IS 'FK на транзакцию';
COMMENT ON COLUMN transaction_status_history.old_status IS 'Предыдущий статус';
COMMENT ON COLUMN transaction_status_history.new_status IS 'Новый статус';
COMMENT ON COLUMN transaction_status_history.changed_reason IS 'Причина изменения статуса';

-- ============================================================
-- Готово!
-- ============================================================

-- Проверка созданных таблиц
SELECT 
    table_name, 
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_name = t.table_name) as column_count
FROM information_schema.tables t
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- Проверка созданных индексов
SELECT 
    tablename, 
    indexname, 
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;




