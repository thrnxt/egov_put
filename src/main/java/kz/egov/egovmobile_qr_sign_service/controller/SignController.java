package kz.egov.egovmobile_qr_sign_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import kz.egov.egovmobile_qr_sign_service.dto.Api1Response;
import kz.egov.egovmobile_qr_sign_service.dto.Api2Response;
import kz.egov.egovmobile_qr_sign_service.dto.SignErrorResponse;
import kz.egov.egovmobile_qr_sign_service.dto.InitSignRequest;
import kz.egov.egovmobile_qr_sign_service.service.SignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SignController {

    private final SignService signService;
    private static final String BEARER_PREFIX = "Bearer ";

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
    public ResponseEntity<Map<String, String>> initiateSigning(@RequestBody InitSignRequest body,
                                                               HttpServletRequest request) {
        String clientIdentifier = request.getHeader("X-Client-ID");
        if (clientIdentifier == null) clientIdentifier = "unknown-client";

        // Строгая валидация структуры API №2
        var err = signService.validateInitRequest(body);
        if (err.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", err.get()));
        }

        String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath());
        String id = signService.initNewSigningTransaction(baseUrl, body, clientIdentifier);
        String api1Url = baseUrl + "/api/v1/egov-api1/" + id;

        return ResponseEntity.ok(Map.of(
                "transactionId", id,
                "api1", api1Url,
                "qr", "mobileSign:" + api1Url
//                "cross", "https://m.egov.kz/mobileSign/?link=" + api1Url
        ));
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
    @RequestMapping(value = "/sign-process/{transactionId}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> getDocumentsForSigning(
            @PathVariable String transactionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "Accept-Language", defaultValue = "ru", required = false) String acceptLanguage
            // @RequestBody Map<String, String> edsAuthBody - тело для Eds, опускаем для упрощения
    ) {
        Optional<Api1Response> api1Opt = signService.generateApi1Response(transactionId);
        if (api1Opt.isEmpty()) {
            return localizedError(HttpStatus.NOT_FOUND, acceptLanguage, "Транзакция не найдена.", "Транзакция табылмады.");
        }

        // Проверка авторизации
        String authType = api1Opt.get().document().authType();
        String authToken = api1Opt.get().document().authToken();

        if ("Token".equals(authType) && (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX) || !authToken.equals(authorizationHeader.substring(BEARER_PREFIX.length())))) {
            return localizedError(HttpStatus.FORBIDDEN, acceptLanguage,
                    "Неверный токен авторизации или токен отсутствует.", "Авторизация таңбалауышы қате немесе жоқ.");
        }

        // Получение данных для подписания
        Optional<Api2Response> docs = signService.getDocumentsToSign(transactionId);
        if (docs.isPresent()) {
            return ResponseEntity.ok(docs.get());
        }
        return localizedError(HttpStatus.FORBIDDEN, acceptLanguage,
                "Транзакция истекла или не готова к подписанию.", "Транзакция мерзімі өтті немесе қол қоюға дайын емес.");
    }

    @PutMapping("/sign-process/{transactionId}")
    public ResponseEntity<?> sendSignedDocuments(
            @PathVariable String transactionId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "Accept-Language", defaultValue = "ru", required = false) String acceptLanguage,
            @RequestBody Api2Response signedData
    ) {
        log.info("Received PUT request for transactionId: {}", transactionId);
        log.debug("Signed data: {}", signedData);

        Optional<Api1Response> api1Opt = signService.generateApi1Response(transactionId);
        if (api1Opt.isEmpty()) {
            log.error("Transaction not found for ID: {}", transactionId);
            return localizedError(HttpStatus.NOT_FOUND, acceptLanguage, "Транзакция не найдена.", "Транзакция табылмады.");
        }

        // Проверка авторизации (повтор)
        String authType = api1Opt.get().document().authType();
        String authToken = api1Opt.get().document().authToken();
        if ("Token".equals(authType) && (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX) || !authToken.equals(authorizationHeader.substring(BEARER_PREFIX.length())))) {
            log.error("Authorization failed for transactionId: {}", transactionId);
            return localizedError(HttpStatus.FORBIDDEN, acceptLanguage, "Неверный токен авторизации.", "Авторизация таңбалауышы қате.");
        }

        log.info("Authorization successful, proceeding to validation");

        // Обработка и реальная валидация через NCANode
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