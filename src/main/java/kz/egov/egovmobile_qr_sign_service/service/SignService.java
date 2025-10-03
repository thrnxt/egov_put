package kz.egov.egovmobile_qr_sign_service.service;

import kz.egov.egovmobile_qr_sign_service.dto.Api1Response;
import kz.egov.egovmobile_qr_sign_service.dto.Api2Response;
import kz.egov.egovmobile_qr_sign_service.model.SignTransaction;
import kz.egov.egovmobile_qr_sign_service.dto.InitSignRequest;
import kz.egov.egovmobile_qr_sign_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignService {

    private final TransactionRepository repository;
    private static final String API2_URI_TEMPLATE = "/api/v1/sign-process/";

    @Autowired
    private WebClient webClient;

    @Value("${ncanode.url}")
    private String ncanodeUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    // --- Валидация входного запроса InitSignRequest / API #2 структуры ---
    public Optional<String> validateInitRequest(InitSignRequest request) {
        if (request == null) return Optional.of("Пустой запрос.");
        if (request.getDocuments() == null) return Optional.of("Отсутствует объект documents (API №2).");
        Api2Response docs = request.getDocuments();
        if (docs.version() <= 0) return Optional.of("Поле version должно быть положительным.");
        if (docs.documentsToSign() == null || docs.documentsToSign().isEmpty()) return Optional.of("Список documentsToSign пуст.");

        String topMethod = docs.signMethod();
        boolean isMix = "MIX_SIGN".equalsIgnoreCase(topMethod);

        for (Api2Response.DocumentToSign d : docs.documentsToSign()) {
            String method = isMix ? d.signMethod() : topMethod;
            if (method == null) {
                return Optional.of("Не указан signMethod для документа (и не задан общий signMethod).");
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
                        return Optional.of("Для методов CMS/SIGN_BYTES_ARRAY требуется объект document.file.");
                    }
                    if (d.document().file().mime() == null || d.document().file().mime().isBlank()) {
                        return Optional.of("Поле mime в document.file обязательно.");
                    }
                    if (d.document().file().data() == null || d.document().file().data().isBlank()) {
                        return Optional.of("Поле data в document.file обязательно.");
                    }
                    break;
                default:
                    return Optional.of("Недопустимый signMethod: " + method + ". Допустимые: XML, CMS_WITH_DATA, CMS_SIGN_ONLY, SIGN_BYTES_ARRAY, MIX_SIGN.");
            }
        }
        return Optional.empty();
    }


    // Логика инициации (вызывается нашим клиентом для запуска процесса)
    @Transactional
    public String initNewSigningTransaction(String baseUrl, InitSignRequest request, String clientIdentifier) {
        String id = UUID.randomUUID().toString();

        String authType = request.getDocument() != null ? request.getDocument().getAuthType() : "None";
        String authToken = request.getDocument() != null ? request.getDocument().getAuthToken() : null;
        if ("Token".equals(authType) && (authToken == null || authToken.isBlank())) {
            authToken = "token-auth-" + UUID.randomUUID();
        }

        String api2Uri = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl) + API2_URI_TEMPLATE + id;

        SignTransaction transaction = new SignTransaction();
        transaction.setTransactionId(id);
        transaction.setExpiryDate(request.getExpiryDate() != null ? request.getExpiryDate() : ZonedDateTime.now().plusHours(24));
        transaction.setAuthType(authType);
        transaction.setAuthToken(authToken);
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : ("Подписание документов для клиента: " + clientIdentifier));
        transaction.setApi2Uri(api2Uri);
        if (request.getOrganisation() != null) {
            transaction.setOrgNameRu(request.getOrganisation().getNameRu());
            transaction.setOrgNameKz(request.getOrganisation().getNameKz());
            transaction.setOrgNameEn(request.getOrganisation().getNameEn());
            transaction.setOrgBin(request.getOrganisation().getBin());
        }
        transaction.setBackUrl(request.getBackUrl() != null ? request.getBackUrl() : (baseUrl + "/back"));
        transaction.setStatus("PENDING");
        transaction.setDocumentsForSigning(request.getDocuments());

        repository.save(transaction);
        return id;
    }

    public Optional<Api1Response> generateApi1Response(String transactionId) {
        return repository.findById(transactionId).map(tx ->
                Api1Response.builder()
                        .description(tx.getDescription())
                        .expiryDate(tx.getExpiryDate())
                        .organisation(Api1Response.Organisation.builder()
                                .nameRu(tx.getOrgNameRu())
                                .nameKz(tx.getOrgNameKz())
                                .nameEn(tx.getOrgNameEn())
                                .bin(tx.getOrgBin())
                                .build())
                        .document(Api1Response.Document.builder()
                                .uri(tx.getApi2Uri())
                                .authType(tx.getAuthType())
                                .authToken(tx.getAuthToken())
                                .build())
                        .build()
        );
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
        log.debug("Transaction status: {}", tx.getStatus());

        if (!"PENDING".equals(tx.getStatus())) {
            log.error("Transaction status is not PENDING, current status: {}", tx.getStatus());
            return false;
        }

        log.info("Transaction is valid, proceeding to signature validation");
        // Реальная валидация подписи через NCANode
        boolean signatureValid = validateSignatureViaNcaNode(signedData);

        if (signatureValid) {
            log.info("Signature validation successful for transactionId: {}", transactionId);
            tx.setSignedDocuments(signedData);
            tx.setStatus("SIGNED");
            repository.save(tx);
            return true;
        } else {
            log.error("Signature validation failed for transactionId: {}", transactionId);
            tx.setStatus("FAILED");
            repository.save(tx);
            return false;
        }
    }

    public Optional<String> getBackUrl(String transactionId) {
        return repository.findById(transactionId).map(SignTransaction::getBackUrl);
    }

    // --- Простая имитация проверки подписей (в реальном проекте заменить) ---
    public boolean validateSignedPayload(Api2Response original, Api2Response signed) {
        if (original == null || signed == null) return false;
        if (original.documentsToSign() == null || signed.documentsToSign() == null) return false;
        if (original.documentsToSign().size() != signed.documentsToSign().size()) return false;
        // Простейшая проверка соответствия id и наличия новых данных
        for (int i = 0; i < original.documentsToSign().size(); i++) {
            Api2Response.DocumentToSign src = original.documentsToSign().get(i);
            Api2Response.DocumentToSign dst = signed.documentsToSign().get(i);
            if (src.id() != dst.id()) return false;
            // XML: ожидаем, что поле documentXml стало непустым (подписанный XML)
            if ("XML".equalsIgnoreCase(src.signMethod() != null ? src.signMethod() : original.signMethod())) {
                if (dst.documentXml() == null || dst.documentXml().isBlank()) return false;
            } else {
                if (dst.document() == null || dst.document().file() == null || dst.document().file().data() == null) return false;
            }
        }
        return true;
    }

    // Новый метод для валидации через NCANode
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
                    return false;  // Если хоть один документ невалиден, вся валидация проваливается
                } else {
                    log.debug("Validation successful for document ID: {}, signMethod: {}", doc.id(), signMethod);
                }
            }
            log.info("All documents passed signature validation");
            return true;  // Все документы прошли валидацию
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
        
        // NCANode v3 использует /cms/verify для проверки CMS подписи
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
        
        // NCANode v3 использует /xml/verify для проверки XML подписи
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
        
        // NCANode v3 использует /raw/verify для проверки подписи байтов
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
                .bodyToMono(String.class);

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
        } catch (WebClientResponseException e) {
            log.error("WebClient error calling NCANode ({}): {} - Response body: {}", endpoint, e.getMessage(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("General error calling NCANode ({}): {}", endpoint, e.getMessage(), e);
        }
        return false;
    }
}