# Исправления для Production Readiness

## ✅ Реализованные исправления

### 1. Rate Limiting (Защита от массового создания транзакций)

**Проблема:** Кто угодно мог создать миллион транзакций и положить БД

**Решение:**
- ✅ Добавлена зависимость `bucket4j-core` в `pom.xml`
- ✅ Создан `RateLimitingFilter.java` с лимитом **10 запросов/минуту** с одного IP
- ✅ Burst capacity: **20 запросов** (если есть "запас" токенов)
- ✅ Применяется только к `POST /api/v1/mgovSign`
- ✅ Возвращает `429 Too Many Requests` при превышении лимита

**Конфигурация:**
```properties
rate.limit.requests-per-minute=10
rate.limit.burst-capacity=20
```

**Логика:**
- Каждый IP получает bucket с 10 токенами
- Токены восстанавливаются со скоростью 10/минуту
- При исчерпании токенов - блокировка до восстановления

---

### 2. Ограничение размера запросов

**Проблема:** Можно было отправить 1GB JSON и положить сервер

**Решение:**
- ✅ Настройки в `application.properties`:
  - Общий размер запроса: **10MB**
  - Размер одного файла: **50MB**
  - Размер заголовков: **8KB**

- ✅ Валидация в DTO:
  - Максимум документов: **50**
  - Размер XML документа: **1MB**
  - Размер base64 файла: **50MB**
  - Длина description: **5000 символов**

**Конфигурация:**
```properties
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=10MB
server.tomcat.max-swallow-size=10MB
server.max-http-request-header-size=8KB
```

**Валидация в коде:**
```java
@Size(max = 52428800, message = "File data too large (max 50MB)")
private String data;

@Size(max = 50, message = "Too many documents (max 50)")
private List<DocumentToSign> documentsToSign;
```

---

### 3. Валидация БИН

**Проблема:** Можно было отправить БИН "abc123xyz" или "0000000000"

**Решение:**
- ✅ Создана аннотация `@BinValid`
- ✅ Создан `BinValidator.java` с проверками:
  - Формат: ровно 12 цифр
  - Не может быть "000000000000"
  - Предупреждение в логах для подозрительных БИН (начинающихся с 0000)

**Использование:**
```java
@BinValid
private String bin;
```

**Проверки:**
- ✅ `^\\d{12}$` - только 12 цифр
- ✅ Не `000000000000`
- ⚠️ Предупреждение для БИН начинающихся с `0000`

---

### 4. Обработка отказа NCANode

**Проблема:** Если NCANode не отвечает - весь сервис висит/падает

**Решение:**
- ✅ Таймауты в `WebClientConfig`:
  - Подключение: **5 секунд**
  - Ответ: **15 секунд** (настраивается)

- ✅ Retry механизм в `SignService`:
  - **3 попытки** с экспоненциальной задержкой (1, 2, 4 сек)
  - Повтор только при временных ошибках (5xx, таймауты, сетевые ошибки)
  - Логирование всех попыток

- ✅ Graceful degradation:
  - При отказе NCANode возвращается `false` (валидация не прошла)
  - Транзакция помечается как `FAILED`
  - Клиент получает понятную ошибку

**Конфигурация:**
```properties
ncanode.timeout=15s
ncanode.retry-attempts=3
ncanode.retry-delay=1s
```

**Логика retry:**
```java
.retryWhen(Retry.backoff(retryAttempts, retryDelay)
    .filter(this::isRetryableException)
    .doBeforeRetry(retrySignal -> log.warn("Retrying..."))
)
.onErrorResume(throwable -> {
    log.error("NCANode failed after retries: {}", throwable.getMessage());
    return Mono.empty();
});
```

---

## 📊 Итоговые лимиты

| Параметр | Лимит | Описание |
|----------|-------|----------|
| **Rate Limiting** | 10 запросов/минуту | С одного IP адреса |
| **Общий размер запроса** | 10MB | Включая все данные |
| **Размер одного файла** | 50MB | Base64 encoded |
| **Количество документов** | 50 | В одной транзакции |
| **Размер XML** | 1MB | Для XML подписания |
| **Длина description** | 5000 символов | Описание транзакции |
| **БИН** | 12 цифр | Формат БИН РК |
| **NCANode таймаут** | 15 секунд | Максимальное время ожидания |
| **Retry попытки** | 3 | При временных ошибках |

---

## 🔧 Добавленные зависимости

```xml
<!-- Rate Limiting -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>7.6.0</version>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

## 📁 Новые файлы

1. **`RateLimitingFilter.java`** - фильтр для ограничения запросов
2. **`@BinValid`** - аннотация для валидации БИН
3. **`BinValidator.java`** - валидатор БИН
4. **Обновлены DTO** - добавлена валидация размеров
5. **Обновлен `WebClientConfig`** - добавлены таймауты
6. **Обновлен `SignService`** - добавлен retry механизм

---

## 🚀 Готовность к production

### ✅ Исправлено:
- ❌ **Массовое создание транзакций** → ✅ Rate limiting 10/минуту
- ❌ **Большие запросы** → ✅ Лимиты 10MB общий, 50MB файл
- ❌ **Некорректные БИН** → ✅ Валидация формата
- ❌ **Отказ NCANode** → ✅ Retry + graceful degradation

### 📈 Улучшения производительности:
- **Rate limiting** предотвращает DDoS
- **Лимиты размеров** защищают от OOM
- **Таймауты** предотвращают зависание
- **Retry** повышает надежность

### 🛡️ Улучшения безопасности:
- **Валидация БИН** предотвращает некорректные данные
- **Лимиты размеров** защищают от атак
- **Rate limiting** защищает от брутфорса

---

## 🧪 Тестирование

### Тест Rate Limiting:
```bash
# Отправить 15 запросов подряд
for i in {1..15}; do
  curl -X POST http://localhost:8080/api/v1/mgovSign \
    -H "Content-Type: application/json" \
    -d '{"documents":{"signMethod":"XML","version":2,"documentsToSign":[]}}'
done
# Ожидаем: первые 10 успешны, остальные 429
```

### Тест лимитов размера:
```bash
# Создать большой JSON (>10MB)
curl -X POST http://localhost:8080/api/v1/mgovSign \
  -H "Content-Type: application/json" \
  -d '{"documents":{"signMethod":"XML","version":2,"documentsToSign":[{"id":1,"documentXml":"'$(printf 'A%.0s' {1..10485760})'"}]}}'
# Ожидаем: 400 Bad Request
```

### Тест валидации БИН:
```bash
# Некорректный БИН
curl -X POST http://localhost:8080/api/v1/mgovSign \
  -H "Content-Type: application/json" \
  -d '{"organisation":{"bin":"abc123"},"documents":{"signMethod":"XML","version":2,"documentsToSign":[]}}'
# Ожидаем: 400 Bad Request с сообщением о БИН
```

### Тест NCANode resilience:
```bash
# Остановить NCANode и отправить запрос
curl -X POST http://localhost:8080/api/v1/mgovSign \
  -H "Content-Type: application/json" \
  -d '{"documents":{"signMethod":"XML","version":2,"documentsToSign":[]}}'
# Ожидаем: 503 Service Unavailable (если настроено) или 500
```

---

## ⚠️ Важные замечания

1. **Rate Limiting в памяти** - при перезапуске приложения счетчики сбрасываются
2. **БИН валидация** - только формальная, без проверки контрольной суммы
3. **NCANode retry** - может увеличить время ответа до 45 секунд (3 попытки × 15 сек)
4. **Логирование** - добавлено много DEBUG/INFO логов для мониторинга

---

## 🎯 Следующие шаги (опционально)

1. **Redis для Rate Limiting** - для кластера приложений
2. **Circuit Breaker** - для NCANode (Resilience4j)
3. **Метрики** - Prometheus для мониторинга лимитов
4. **Алерты** - уведомления при превышении лимитов
5. **Контрольная сумма БИН** - если найдете алгоритм РК

---

**Версия:** 1.0  
**Дата:** 2025-10-09  
**Статус:** ✅ Готово к тестированию
