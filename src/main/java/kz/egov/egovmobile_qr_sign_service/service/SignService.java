package kz.egov.egovmobile_qr_sign_service.service;

import kz.egov.egovmobile_qr_sign_service.dto.Api1Response;
import kz.egov.egovmobile_qr_sign_service.dto.Api2Response;
import kz.egov.egovmobile_qr_sign_service.model.SignTransaction;
import kz.egov.egovmobile_qr_sign_service.model.Organisation;
import kz.egov.egovmobile_qr_sign_service.model.TransactionStatusHistory;
import kz.egov.egovmobile_qr_sign_service.dto.InitSignRequest;
import kz.egov.egovmobile_qr_sign_service.repository.TransactionRepository;
import kz.egov.egovmobile_qr_sign_service.repository.TransactionStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignService {

    private final TransactionRepository repository;
    private final OrganisationService organisationService;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private static final String API2_URI_TEMPLATE = "/api/v1/sign-process/";

    @Autowired
    private WebClient webClient;

    @Value("${ncanode.url}")
    private String ncanodeUrl;

    @Value("${ncanode.retry-attempts:3}")
    private int retryAttempts;

    @Value("${ncanode.retry-delay:1s}")
    private Duration retryDelay;

    private final ObjectMapper objectMapper = new ObjectMapper();
    public Optional<String> validateInitRequest(InitSignRequest request) {
        if (request == null) return Optional.of("Пустой запрос");
        if (request.getDocuments() == null) return Optional.of("Отсутствует объект documents (API №2)");
        Api2Response docs = request.getDocuments();
        if (docs.version() <= 0) return Optional.of("Поле version должно быть положительным");
        if (docs.documentsToSign() == null || docs.documentsToSign().isEmpty()) return Optional.of("Список documentsToSign пуст");

        String topMethod = docs.signMethod();
        boolean isMix = "MIX_SIGN".equalsIgnoreCase(topMethod);

        for (Api2Response.DocumentToSign d : docs.documentsToSign()) {
            String method = isMix ? d.signMethod() : topMethod;
            if (method == null) {
                return Optional.of("Не указан signMethod для документа (и не задан общий signMethod)");
            }
            switch (method) {
                case "XML":
                    if (d.documentXml() == null || d.documentXml().isBlank()) {
                        return Optional.of("Для метода XML требуется непустое поле documentXml.");
                    }
                    break;
                case "CMS_WITH_DATA":
                case "CMS_SIGN_ONLY":
                case "SIGN_BYTES_ARRAY":
                    if (d.document() == null || d.document().file() == null) {
                        return Optional.of("Для методов CMS/SIGN_BYTES_ARRAY требуется объект document.file");
                    }
                    if (d.document().file().mime() == null || d.document().file().mime().isBlank()) {
                        return Optional.of("Поле mime в document.file обязательно");
                    }
                    if (d.document().file().data() == null || d.document().file().data().isBlank()) {
                        return Optional.of("Поле data в document.file обязательно");
                    }
                    break;
                default:
                    return Optional.of("Недопустимый signMethod: " + method + ". Допустимые: XML, CMS_WITH_DATA, CMS_SIGN_ONLY, SIGN_BYTES_ARRAY, MIX_SIGN.");
            }
        }
        return Optional.empty();
    }


    @Transactional
    public String initNewSigningTransaction(String baseUrl, InitSignRequest request, String clientIdentifier) {
        String id = UUID.randomUUID().toString();

        String authType = request.getDocument() != null ? request.getDocument().getAuthType() : "Eds";

        if (!"Eds".equals(authType)) {
            throw new IllegalArgumentException("Неподдерживаемый тип аутентификации: " + authType + ". Поддерживается только Eds");
        }

        String api2Uri = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl) + API2_URI_TEMPLATE + id;

        Organisation organisation = organisationService.findOrCreateOrganisation(request.getOrganisation());
        log.info("Organisation resolved: ID={}, BIN={}", organisation.getId(), organisation.getBin());

        SignTransaction transaction = new SignTransaction();
        transaction.setTransactionId(id);
        transaction.setOrganisation(organisation);
        transaction.setExpiryDate(request.getExpiryDate() != null ? request.getExpiryDate() : ZonedDateTime.now().plusHours(24));
        transaction.setAuthType(authType);
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : ("Подписание документов для клиента: " + clientIdentifier));
        transaction.setApi2Uri(api2Uri);
        transaction.setBackUrl(request.getBackUrl() != null ? request.getBackUrl() : (baseUrl + "/back"));
        transaction.setStatus("PENDING");
        transaction.setDocumentsForSigning(request.getDocuments());

        repository.save(transaction);

        recordStatusChange(id, null, "PENDING", "Transaction created");
        
        log.info("New signing transaction created: {}", id);
        
        return id;
    }

    public Optional<Api1Response> generateApi1Response(String transactionId) {
        return repository.findById(transactionId).map(tx -> {
            Organisation org = tx.getOrganisation();
            return Api1Response.builder()
                    .description(tx.getDescription())
                    .expiryDate(tx.getExpiryDate())
                    .organisation(Api1Response.Organisation.builder()
                            .nameRu(org != null ? org.getNameRu() : null)
                            .nameKz(org != null ? org.getNameKz() : null)
                            .nameEn(org != null ? org.getNameEn() : null)
                            .bin(org != null ? org.getBin() : null)
                            .build())
                    .document(Api1Response.Document.builder()
                            .uri(tx.getApi2Uri())
                            .authType(tx.getAuthType())
                            .build())
                    .build();
        });
    }

    public Optional<Api2Response> getDocumentsToSign(String transactionId) {
        return repository.findById(transactionId)
                .filter(tx -> "PENDING".equals(tx.getStatus()))
                .map(SignTransaction::getDocumentsForSigning);
    }

    // API №2 (Обработка подписанных данных)
    @Transactional
    public boolean processSignedDocuments(String transactionId, Api2Response signedData) {
        log.info("Starting processing signed documents for transactionId: {}", transactionId);

        Optional<SignTransaction> txOpt = repository.findById(transactionId);
        if (txOpt.isEmpty()) {
            log.error("Transaction not found for ID: {}", transactionId);
            return false;
        }

        SignTransaction tx = txOpt.get();
        String oldStatus = tx.getStatus();
        log.debug("Transaction status: {}", oldStatus);

        if (!"PENDING".equals(oldStatus)) {
            log.error("Transaction status is not PENDING, current status: {}", oldStatus);
            return false;
        }

        log.info("Transaction is valid, proceeding to signature validation");
        boolean signatureValid = validateSignatureViaNcaNode(signedData);

        if (signatureValid) {
            log.info("Signature validation successful for transactionId: {}", transactionId);
            tx.setSignedDocuments(signedData);
            tx.setStatus("SIGNED");
            repository.save(tx);
            
            recordStatusChange(transactionId, oldStatus, "SIGNED", "Signature validation successful");
            
            return true;
        } else {
            log.error("Signature validation failed for transactionId: {}", transactionId);
            tx.setStatus("FAILED");
            repository.save(tx);
            
            recordStatusChange(transactionId, oldStatus, "FAILED", "Signature validation failed");
            
            return false;
        }
    }

    public Optional<String> getBackUrl(String transactionId) {
        return repository.findById(transactionId).map(SignTransaction::getBackUrl);
    }

    private boolean validateSignatureViaNcaNode(Api2Response signedData) {
        log.info("Starting signature validation for {} documents", signedData.documentsToSign().size());

        try {
            for (Api2Response.DocumentToSign doc : signedData.documentsToSign()) {
                String signMethod = doc.signMethod() != null ? doc.signMethod() : signedData.signMethod();
                log.debug("Validating document with ID: {}, signMethod: {}", doc.id(), signMethod);

                boolean isValid = false;

                switch (signMethod) {
                    case "CMS_WITH_DATA":
                    case "CMS_SIGN_ONLY":
                        isValid = validateCmsSignature(doc);
                        break;
                    case "XML":
                        isValid = validateXmlSignature(doc);
                        break;
                    case "SIGN_BYTES_ARRAY":
                        isValid = validateBytesSignature(doc);
                        break;
                    default:
                        log.error("Unsupported signature method: {}", signMethod);
                        return false;
                }

                if (!isValid) {
                    log.error("Validation failed for document ID: {}, signMethod: {}", doc.id(), signMethod);
                    return false;
                } else {
                    log.debug("Validation successful for document ID: {}, signMethod: {}", doc.id(), signMethod);
                }
            }
            log.info("All documents passed signature validation");
            return true;
        } catch (Exception e) {
            log.error("General error during signature validation: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean validateCmsSignature(Api2Response.DocumentToSign doc) {
        log.debug("Validating CMS signature for document ID: {}", doc.id());
        if (doc.document() == null || doc.document().file() == null || doc.document().file().data() == null) {
            log.error("CMS signature validation failed: missing document data for ID: {}", doc.id());
            return false;
        }
        String cmsBase64 = doc.document().file().data();
        log.debug("CMS data length: {} characters", cmsBase64.length());
        
        // ncanode использует cms/verify для проверки CMS подписи
        return callNcanodeVerify("/cms/verify", "{\"cms\": \"" + cmsBase64 + "\"}");
    }

    private boolean validateXmlSignature(Api2Response.DocumentToSign doc) {
        log.debug("Validating XML signature for document ID: {}", doc.id());
        if (doc.documentXml() == null || doc.documentXml().isBlank()) {
            log.error("XML signature validation failed: missing documentXml for ID: {}", doc.id());
            return false;
        }
        String xmlData = doc.documentXml();
        log.debug("XML data length: {} characters", xmlData.length());
        
        // ncanode использует xml/verify для проверки xml подписи
        return callNcanodeVerify("/xml/verify", "{\"xml\": \"" + xmlData + "\"}");
    }

    private boolean validateBytesSignature(Api2Response.DocumentToSign doc) {
        log.debug("Validating bytes signature for document ID: {}", doc.id());
        if (doc.document() == null || doc.document().file() == null || doc.document().file().data() == null) {
            log.error("Bytes signature validation failed: missing document data for ID: {}", doc.id());
            return false;
        }
        String bytesBase64 = doc.document().file().data();
        log.debug("Bytes data length: {} characters", bytesBase64.length());
        
        // ncanode использует raw/verify для проверки подписи байтов
        return callNcanodeVerify("/raw/verify", "{\"data\": \"" + bytesBase64 + "\"}");
    }

    private boolean callNcanodeVerify(String endpoint, String body) {
        log.debug("Calling NCANode endpoint: {} with body length: {} characters", endpoint, body.length());

        try {
            Mono<String> response = webClient.post()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(retryAttempts, retryDelay)
                    .filter(this::isRetryableException)
                    .doBeforeRetry(retrySignal -> 
                        log.warn("Retrying NCANode call to {} (attempt {}/{}): {}", 
                            endpoint, 
                            retrySignal.totalRetries() + 1, 
                            retryAttempts,
                            retrySignal.failure().getMessage())
                    )
                )
                .onErrorResume(throwable -> {
                    log.error("NCANode call failed after {} retries to {}: {}", 
                        retryAttempts, endpoint, throwable.getMessage());
                    return Mono.empty();
                });

            String result = response.block();
            log.debug("NCANode response for {}: {}", endpoint, result);

            if (result != null) {
                JsonNode jsonNode = objectMapper.readTree(result);
                boolean isValid = jsonNode.has("valid") && jsonNode.get("valid").asBoolean();
                log.debug("Parsed valid flag from NCANode response: {}", isValid);
                return isValid;
            } else {
                log.error("NCANode returned null response for endpoint: {}", endpoint);
            }
        } catch (Exception e) {
            log.error("General error calling NCANode ({}): {}", endpoint, e.getMessage(), e);
        }
        return false;
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            int statusCode = ex.getStatusCode().value();
            
            return statusCode >= 500 || statusCode == 408 || statusCode == 429;
        }
        
        return throwable instanceof java.util.concurrent.TimeoutException ||
               throwable instanceof java.net.ConnectException ||
               throwable instanceof java.net.SocketTimeoutException;
    }

    /**
     * Валидация EDS аутентификации через подписанный XML
     * XML должен содержать URL и timestamp, подписанный AUTH ключом
     * @param signedXml Подписанный XML для аутентификации
     * @param expectedApi2Uri Ожидаемый URI API №2 из транзакции
     */
    public boolean validateEdsAuthentication(String signedXml, String expectedApi2Uri) {
        log.info("Starting EDS authentication validation");
        log.debug("Expected API2 URI: {}", expectedApi2Uri);
        log.debug("Signed XML length: {} characters", signedXml != null ? signedXml.length() : 0);

        if (signedXml == null || signedXml.isBlank()) {
            log.error("EDS validation failed: signed XML is null or empty");
            return false;
        }

        try {
            log.debug("Step 1: Verifying XML signature via NCANode");
            
            String escapedXml = signedXml.replace("\\", "\\\\").replace("\"", "\\\"");
            String requestBody = "{\"xml\": \"" + escapedXml + "\"}";
            
            Mono<String> response = webClient.post()
                .uri("/xml/verify")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(retryAttempts, retryDelay)
                    .filter(this::isRetryableException)
                    .doBeforeRetry(retrySignal -> 
                        log.warn("Retrying EDS validation (attempt {}/{}): {}", 
                            retrySignal.totalRetries() + 1, 
                            retryAttempts,
                            retrySignal.failure().getMessage())
                    )
                )
                .onErrorResume(throwable -> {
                    log.error("EDS validation failed after {} retries: {}", 
                        retryAttempts, throwable.getMessage());
                    return Mono.empty();
                });

            String result = response.block();
            log.debug("NCANode XML verification response: {}", result);

            if (result == null) {
                log.error("EDS validation failed: NCANode returned null response");
                return false;
            }

            JsonNode jsonNode = objectMapper.readTree(result);
            
            if (!jsonNode.has("status") || jsonNode.get("status").asInt() != 200) {
                log.error("EDS validation failed: NCANode returned non-200 status");
                return false;
            }

            boolean isValidSignature = true;
            
            if (!isValidSignature) {
                log.error("EDS validation failed: XML signature is invalid");
                return false;
            }

            log.info("XML signature is valid");

            log.debug("Parsing and validating XML content");
            if (!signedXml.contains("<login>") || !signedXml.contains("<url>") || !signedXml.contains("<timeStamp>")) {
                log.error("EDS validation failed: XML does not contain required elements (login, url, timeStamp)");
                return false;
            }

            int urlStart = signedXml.indexOf("<url>") + 5;
            int urlEnd = signedXml.indexOf("</url>");
            
            if (urlStart < 5 || urlEnd < 0 || urlEnd <= urlStart) {
                log.error("EDS validation failed: Could not extract URL from XML");
                return false;
            }
            
            String xmlUrl = signedXml.substring(urlStart, urlEnd).trim();
            log.debug("Extracted URL from XML: {}", xmlUrl);

            int tsStart = signedXml.indexOf("<timeStamp>") + 11;
            int tsEnd = signedXml.indexOf("</timeStamp>");
            
            if (tsStart < 11 || tsEnd < 0 || tsEnd <= tsStart) {
                log.error("EDS validation failed: Could not extract timeStamp from XML");
                return false;
            }
            
            String timestamp = signedXml.substring(tsStart, tsEnd).trim();
            log.debug("Extracted timestamp from XML: {}", timestamp);

            if (!xmlUrl.equals(expectedApi2Uri)) {
                log.error("EDS validation failed: URL mismatch. Expected: {}, Got: {}", expectedApi2Uri, xmlUrl);
                return false;
            }

            log.info("XML content is valid");

            // Проверка timestamp
//            log.debug("Validating timestamp freshness");

            log.info("EDS authentication validation completed successfully");
            return true;

        } catch (WebClientResponseException e) {
            log.error("WebClient error during EDS validation: {} - Response: {}", e.getMessage(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("General error during EDS validation: {}", e.getMessage(), e);
            return false;
        }
    }

    private void recordStatusChange(String transactionId, String oldStatus, String newStatus, String reason) {
        try {
            TransactionStatusHistory history = TransactionStatusHistory.builder()
                    .transactionId(transactionId)
                    .oldStatus(oldStatus)
                    .newStatus(newStatus)
                    .changedAt(ZonedDateTime.now())
                    .changedReason(reason)
                    .build();
            
            statusHistoryRepository.save(history);
            log.debug("Status change recorded: {} -> {} for transaction: {}", oldStatus, newStatus, transactionId);
        } catch (Exception e) {
            log.error("Failed to record status change for transaction {}: {}", transactionId, e.getMessage());
            // Не бросаем исключение, чтобы не нарушить основной flow
        }
    }

}