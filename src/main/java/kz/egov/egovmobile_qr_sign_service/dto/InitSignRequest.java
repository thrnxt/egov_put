package kz.egov.egovmobile_qr_sign_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.egov.egovmobile_qr_sign_service.validation.BinValid;

import java.time.ZonedDateTime;

@Data
public class InitSignRequest {

    @Size(max = 5000, message = "Description too long (max 5000 characters)")
    private String description;

    @JsonProperty("expiry_date")
    private ZonedDateTime expiryDate;

    @Valid
    private Organisation organisation;

    @Valid
    private DocumentAuth document;
    @Size(max = 512, message = "Back URL too long (max 512 characters)")
    private String backUrl;

    @Valid
    @NotNull(message = "Documents are required")
    private Api2Response documents;

    @Data
    public static class Organisation {
        @JsonProperty("nameRu") 
        @Size(max = 255, message = "Organization name (RU) too long (max 255 characters)")
        private String nameRu;
        
        @JsonProperty("nameKz") 
        @Size(max = 255, message = "Organization name (KZ) too long (max 255 characters)")
        private String nameKz;
        
        @JsonProperty("nameEn") 
        @Size(max = 255, message = "Organization name (EN) too long (max 255 characters)")
        private String nameEn;
        
        @BinValid
        private String bin;
    }

    @Data
    public static class DocumentAuth {
        @JsonProperty("auth_type") 
        @Size(max = 50, message = "Auth type too long (max 50 characters)")
        private String authType;
    }
}



