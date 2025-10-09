# Настройка базы данных для eGov Mobile QR Sign Service

## Обзор изменений архитектуры БД

### Что изменилось?

1. **Нормализация данных организаций**
   - Создана отдельная таблица `organisations`
   - Связь через `organisation_id` в `sign_transactions`

2. **Безопасность**
   - Токены теперь хешируются с помощью BCrypt
   - Поле `auth_token_hash` вместо `auth_token`

3. **Аудит и история**
   - Новая таблица `transaction_status_history`
   - Автоматическое логирование всех изменений статусов

4. **Оптимизация производительности**
   - Индексы на часто используемых полях
   - JSONB вместо TEXT для документов
   - GIN индекс для поиска внутри JSONB

### Структура таблиц

```
organisations
├── id (PK, BIGSERIAL)
├── bin (UNIQUE)
├── name_ru, name_kz, name_en
└── created_at, updated_at

sign_transactions
├── transaction_id (PK)
├── organisation_id (FK → organisations.id)
├── auth_token_hash (BCrypt)
├── documents_to_sign (JSONB)
├── signed_documents (JSONB)
└── ... другие поля

transaction_status_history
├── id (PK)
├── transaction_id (FK → sign_transactions)
├── old_status, new_status
└── changed_at, changed_reason
```

---

## Инструкция по установке

### Шаг 1: Подготовка

**⚠️ ВНИМАНИЕ:** Следующие действия удалят все существующие данные в БД `egov_sign_db`!

1. Сделайте резервную копию БД, если нужно:
   ```bash
   pg_dump -U postgres -d egov_sign_db > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. Откройте файл `database_init.sql` в редакторе

3. Если хотите автоматически удалить старую БД, раскомментируйте строку:
   ```sql
   DROP DATABASE IF EXISTS egov_sign_db;
   ```

### Шаг 2: Выполнение SQL скрипта

#### Вариант A: Через psql (командная строка)

```bash
# Подключитесь к PostgreSQL
psql -U postgres

# Если раскомментировали DROP DATABASE - выполните скрипт напрямую:
\i database_init.sql

# Если НЕ раскомментировали - сначала удалите БД вручную:
DROP DATABASE IF EXISTS egov_sign_db;
\i database_init.sql
```

#### Вариант B: Через pgAdmin

1. Откройте pgAdmin
2. Подключитесь к серверу PostgreSQL
3. Правой кнопкой на "Databases" → "Create" → "Database..."
4. Или если нужно удалить старую:
   - Правой кнопкой на `egov_sign_db` → "Delete/Drop"
5. "Tools" → "Query Tool"
6. Откройте файл `database_init.sql`
7. Нажмите F5 (Execute)

#### Вариант C: Автоматическое выполнение из командной строки

```bash
# Windows PowerShell
$env:PGPASSWORD="postgres"; psql -U postgres -f database_init.sql

# Linux/Mac
PGPASSWORD=postgres psql -U postgres -f database_init.sql
```

### Шаг 3: Проверка

После выполнения скрипта вы должны увидеть:

```
CREATE DATABASE
CREATE TABLE
CREATE INDEX
...
```

И в конце таблицу с созданными объектами:

```
            table_name             | column_count
-----------------------------------+-------------
 organisations                     |           6
 sign_transactions                 |          12
 transaction_status_history        |           6
```

### Шаг 4: Настройка application.properties

Убедитесь, что в `src/main/resources/application.properties` указаны правильные настройки:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/egov_sign_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# ВАЖНО: Теперь используем update, а не create-drop
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### Шаг 5: Запуск приложения

```bash
# Если используете Maven
./mvnw spring-boot:run

# Или через Maven Wrapper в Windows
mvnw.cmd spring-boot:run
```

При первом запуске Hibernate должен найти все таблицы и не вносить изменений (если использовали `spring.jpa.hibernate.ddl-auto=update`).

---

## Проверка работоспособности

### 1. Проверка таблиц

```sql
-- Проверить, что все таблицы созданы
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public';

-- Проверить структуру sign_transactions
\d+ sign_transactions
```

### 2. Проверка индексов

```sql
-- Посмотреть все индексы
SELECT tablename, indexname 
FROM pg_indexes 
WHERE schemaname = 'public'
ORDER BY tablename;
```

Вы должны увидеть:

- `idx_organisations_bin`
- `idx_transactions_status`
- `idx_transactions_expiry_date`
- `idx_transactions_creation_date`
- `idx_transactions_organisation`
- `idx_documents_sign_method`
- `idx_status_history_transaction`
- `idx_status_history_changed_at`

### 3. Тестовый запрос к API

```bash
# POST запрос для создания транзакции
curl -X POST http://localhost:8080/api/v1/mgovSign \
  -H "Content-Type: application/json" \
  -H "X-Client-ID: test-client" \
  -d '{
    "description": "Тестовое подписание",
    "organisation": {
      "bin": "123456789012",
      "nameRu": "Тестовая организация",
      "nameKz": "Тестілік ұйым",
      "nameEn": "Test Organisation"
    },
    "document": {
      "auth_type": "Token"
    },
    "documents": {
      "signMethod": "XML",
      "version": 2,
      "documentsToSign": [
        {
          "id": 1,
          "nameRu": "Тестовый документ",
          "nameKz": "Тестілік құжат",
          "nameEn": "Test document",
          "documentXml": "<?xml version=\"1.0\"?><test>data</test>"
        }
      ]
    }
  }'
```

### 4. Проверка БД после теста

```sql
-- Проверить, что организация создана
SELECT * FROM organisations;

-- Проверить, что транзакция создана
SELECT transaction_id, auth_type, status, organisation_id 
FROM sign_transactions;

-- Проверить, что история статусов записана
SELECT * FROM transaction_status_history;
```

---

## Часто встречающиеся проблемы

### Ошибка: "database is being accessed by other users"

**Решение:**
```sql
-- Отключить все соединения к БД
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'egov_sign_db'
  AND pid <> pg_backend_pid();

-- Затем удалить БД
DROP DATABASE egov_sign_db;
```

### Ошибка: "relation already exists"

**Решение:** Скрипт уже был выполнен. Либо удалите таблицы вручную, либо пересоздайте всю БД.

### Ошибка: "could not connect to database"

**Решение:** Проверьте, что PostgreSQL запущен:
```bash
# Windows
net start postgresql-x64-14

# Linux
sudo systemctl start postgresql
```

### Ошибка с кодировкой LC_COLLATE

**Решение:** Если у вас другая локаль, измените в `database_init.sql`:
```sql
CREATE DATABASE egov_sign_db
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'  -- Измените на вашу локаль
    LC_CTYPE = 'en_US.UTF-8';    -- Измените на вашу локаль
```

Или вообще уберите эти параметры:
```sql
CREATE DATABASE egov_sign_db
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8';
```

---

## Дополнительные SQL запросы

### Посмотреть статистику по таблицам

```sql
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
    pg_total_relation_size(schemaname||'.'||tablename) AS size_bytes
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY size_bytes DESC;
```

### Очистить все данные (но оставить таблицы)

```sql
TRUNCATE TABLE transaction_status_history CASCADE;
TRUNCATE TABLE sign_transactions CASCADE;
TRUNCATE TABLE organisations CASCADE;
```

### Посмотреть последние транзакции

```sql
SELECT 
    t.transaction_id,
    t.status,
    t.creation_date,
    o.bin,
    o.name_ru
FROM sign_transactions t
LEFT JOIN organisations o ON t.organisation_id = o.id
ORDER BY t.creation_date DESC
LIMIT 10;
```

### Посмотреть историю изменений статуса транзакции

```sql
SELECT 
    old_status,
    new_status,
    changed_at,
    changed_reason
FROM transaction_status_history
WHERE transaction_id = 'ваш-transaction-id'
ORDER BY changed_at;
```

---

## Контакты

Если возникли проблемы при установке, проверьте:

1. ✅ PostgreSQL версии 12+ установлен и запущен
2. ✅ Пользователь `postgres` имеет права на создание БД
3. ✅ Порт 5432 не занят другим приложением
4. ✅ Файл `database_init.sql` в кодировке UTF-8

---

**Дата создания:** 2025-10-09  
**Версия:** 1.0




