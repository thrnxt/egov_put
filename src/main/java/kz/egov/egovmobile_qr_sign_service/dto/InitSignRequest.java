package kz.egov.egovmobile_qr_sign_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class InitSignRequest {

    private String description;

    @JsonProperty("expiry_date")
    private ZonedDateTime expiryDate;

    private Organisation organisation;

    private DocumentAuth document;

    private String backUrl;

    private Api2Response documents;

    @Data
    public static class Organisation {
        @JsonProperty("nameRu") private String nameRu;
        @JsonProperty("nameKz") private String nameKz;
        @JsonProperty("nameEn") private String nameEn;
        private String bin;
    }

    @Data
    public static class DocumentAuth {
        @JsonProperty("auth_type") private String authType; // Token, Eds, None
        @JsonProperty("auth_token") private String authToken;
    }
}



