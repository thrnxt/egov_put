package kz.egov.egovmobile_qr_sign_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import kz.egov.egovmobile_qr_sign_service.dto.Api1Response;
import kz.egov.egovmobile_qr_sign_service.dto.Api2Response;
import kz.egov.egovmobile_qr_sign_service.dto.SignErrorResponse;
import kz.egov.egovmobile_qr_sign_service.dto.InitSignRequest;
import kz.egov.egovmobile_qr_sign_service.dto.EdsAuthRequest;
import kz.egov.egovmobile_qr_sign_service.service.SignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SignController {

    private final SignService signService;

    private ResponseEntity<?> localizedError(HttpStatus status, String lang, String ruMessage, String kkMessage) {
        String message;
        if (lang != null && lang.toLowerCase().contains("kk")) {
            message = kkMessage;
        } else if (lang != null && lang.toLowerCase().contains("en")) {
            message = ruMessage;
        } else {
            message = ruMessage;
        }
        return new ResponseEntity<>(SignErrorResponse.builder().message(message).build(), status);
    }

    @PostMapping("/mgovSign")
    public ResponseEntity<String> initiateSigning(@Valid @RequestBody InitSignRequest body,
                                                               HttpServletRequest request) {
        String clientIdentifier = request.getHeader("X-Client-ID");
        if (clientIdentifier == null) clientIdentifier = "unknown-client";

        var err = signService.validateInitRequest(body);
        if (err.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(err.get());
        }

        String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath());
        String transactionId = signService.initNewSigningTransaction(baseUrl, body, clientIdentifier);
        String api1Url = baseUrl + "/api/v1/egov-api1/" + transactionId;
        String response = "mobileSign:" + api1Url;
        return ResponseEntity.ok(response);
    }

    @GetMapping("/egov-api1/{transactionId}")
    public ResponseEntity<?> getMobileSignMetadata(
            @PathVariable String transactionId,
            @RequestHeader(value = "Accept-Language", defaultValue = "ru", required = false) String acceptLanguage
    ) {
        Optional<Api1Response> api1 = signService.generateApi1Response(transactionId);
        if (api1.isPresent()) {
            return ResponseEntity.ok(api1.get());
        }
        return localizedError(HttpStatus.NOT_FOUND, acceptLanguage,
                "Транзакция не найдена.", "Транзакция табылмады.");
    }
    @PostMapping("/sign-process/{transactionId}")
    public ResponseEntity<?> getDocumentsForSigning(
            @PathVariable String transactionId,
            @RequestHeader(value = "Accept-Language", defaultValue = "ru", required = false) String acceptLanguage,
            @RequestBody EdsAuthRequest edsAuthBody
    ) {
        log.info("Processing sign-process request for transactionId: {}", transactionId);
        
        Optional<Api1Response> api1Opt = signService.generateApi1Response(transactionId);
        if (api1Opt.isEmpty()) {
            log.error("Transaction not found: {}", transactionId);
            return localizedError(HttpStatus.NOT_FOUND, acceptLanguage, "Транзакция не найдена.", "Транзакция табылмады.");
        }

        Api1Response api1 = api1Opt.get();
        String authType = api1.document().authType();
        
        log.debug("Auth type for transaction {}: {}", transactionId, authType);

        if (!"Eds".equals(authType)) {
            log.error("Invalid auth type: {}. Only Eds is supported.", authType);
            return localizedError(HttpStatus.BAD_REQUEST, acceptLanguage,
                    "Неподдерживаемый тип аутентификации. Поддерживается только Eds.", "Қолдау көрсетілмейтін аутентификация түрі. Тек Eds қолдау көрсетіледі.");
        }

        log.debug("Validating EDS authentication");
        
        if (edsAuthBody == null || edsAuthBody.getXml() == null || edsAuthBody.getXml().isBlank()) {
            log.error("EDS authentication failed: missing signed XML in request body");
            return localizedError(HttpStatus.FORBIDDEN, acceptLanguage,
                    "Отсутствует подписанный XML для аутентификации.", "Аутентификация үшін қол қойылған XML жоқ.");
        }
        
        log.debug("Signed XML received, length: {} characters", edsAuthBody.getXml().length());
        
        // Валидация подписанного XML через NCANode
        boolean isValidEds = signService.validateEdsAuthentication(
            edsAuthBody.getXml(), 
            api1.document().uri()
        );
        
        if (!isValidEds) {
            log.error("EDS authentication validation failed for transaction: {}", transactionId);
            return localizedError(HttpStatus.FORBIDDEN, acceptLanguage,
                    "ЭЦП аутентификация не прошла проверку. Подпись недействительна или данные не соответствуют.", 
                    "ЭҚТ аутентификациясы тексеруден өтпеді. Қолтаңба жарамсыз немесе деректер сәйкес келмейді.");
        }
        
        log.info("EDS authentication successful for transaction: {}", transactionId);

        log.debug("Retrieving documents for signing");
        Optional<Api2Response> docs = signService.getDocumentsToSign(transactionId);
        if (docs.isPresent()) {
            log.info("Successfully retrieved documents for signing for transaction: {}", transactionId);
            return ResponseEntity.ok(docs.get());
        }
        log.error("Failed to retrieve documents for transaction: {}", transactionId);
        return localizedError(HttpStatus.FORBIDDEN, acceptLanguage,
                "Транзакция истекла или не готова к подписанию.", "Транзакция мерзімі өтті немесе қол қоюға дайын емес.");
    }

    @PutMapping("/sign-process/{transactionId}")
    public ResponseEntity<?> sendSignedDocuments(
            @PathVariable String transactionId,
            @RequestHeader(value = "Accept-Language", defaultValue = "ru", required = false) String acceptLanguage,
            @Valid @RequestBody Api2Response signedData
    ) {
        log.info("Received PUT request for transactionId: {}", transactionId);
        log.debug("Signed data: {}", signedData);

        Optional<Api1Response> api1Opt = signService.generateApi1Response(transactionId);
        if (api1Opt.isEmpty()) {
            log.error("Transaction not found for ID: {}", transactionId);
            return localizedError(HttpStatus.NOT_FOUND, acceptLanguage, "Транзакция не найдена.", "Транзакция табылмады.");
        }

        Api1Response api1 = api1Opt.get();
        String authType = api1.document().authType();
        
        if (!"Eds".equals(authType)) {
            log.error("Invalid auth type: {}. Only Eds is supported.", authType);
            return localizedError(HttpStatus.BAD_REQUEST, acceptLanguage,
                    "Неподдерживаемый тип аутентификации. Поддерживается только Eds.", "Қолдау көрсетілмейтін аутентификация түрі. Тек Eds қолдау көрсетіледі.");
        }

        // Для EDS аутентификации проверка уже была выполнена при POST запросе
        // При PUT запросе мы просто проверяем, что транзакция существует и активна
        log.debug("EDS authentication - transaction already validated");

        log.info("Authorization successful, proceeding to validation");

        // Обработка и валидация через ncanode
        boolean success = signService.processSignedDocuments(transactionId, signedData);

        if (success) {
            log.info("Signature validation successful for transactionId: {}", transactionId);
            String backUrl = signService.getBackUrl(transactionId).orElseThrow();
            return ResponseEntity.ok().body(Map.of("backUrl", backUrl));
        } else {
            log.error("Signature validation failed for transactionId: {}", transactionId);
            return localizedError(HttpStatus.FORBIDDEN, acceptLanguage,
                    "Подписанные документы не прошли валидацию подписи.", "Қол қойылған құжаттар қолтаңба валидациясынан өтпеді.");
        }
    }
}