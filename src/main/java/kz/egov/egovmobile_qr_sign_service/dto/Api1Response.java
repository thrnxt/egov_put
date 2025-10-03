package kz.egov.egovmobile_qr_sign_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.time.ZonedDateTime;

@Builder
public record Api1Response(
        String description,
        @JsonProperty("expiry_date") ZonedDateTime expiryDate,
        Organisation organisation,
        Document document
) {
    @Builder
    public record Organisation(
            @JsonProperty("nameRu") String nameRu,
            @JsonProperty("nameKz") String nameKz,
            @JsonProperty("nameEn") String nameEn,
            String bin
    ) {}

    @Builder
    public record Document(
            String uri, // API №2
            @JsonProperty("auth_type") String authType, // Token, Eds, None
            @JsonProperty("auth_token") String authToken // Опционально
    ) {}
}