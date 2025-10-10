package kz.egov.egovmobile_qr_sign_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Builder
public record Api2Response(
        @NotNull(message = "Sign method is required")
        @Size(max = 50, message = "Sign method too long (max 50 characters)")
        String signMethod, // xml, cms-with-data...
        
        @Min(value = 1, message = "Version must be at least 1")
        @Max(value = 10, message = "Version too high (max 10)")
        int version, // По умолчанию 1
        
        @NotEmpty(message = "Documents to sign list cannot be empty")
        @Size(max = 50, message = "Too many documents (max 50)")
        @Valid
        List<DocumentToSign> documentsToSign
) {
    @Builder
    public record DocumentToSign(
            @Min(value = 1, message = "Document ID must be positive")
            int id,
            
            @Size(max = 50, message = "Sign method too long (max 50 characters)")
            String signMethod, // Только для MIX_SIGN
            
            @JsonProperty("nameRu") 
            @Size(max = 255, message = "Document name (RU) too long (max 255 characters)")
            String nameRu,
            
            @JsonProperty("nameKz") 
            @Size(max = 255, message = "Document name (KZ) too long (max 255 characters)")
            String nameKz,
            
            @JsonProperty("nameEn") 
            @Size(max = 255, message = "Document name (EN) too long (max 255 characters)")
            String nameEn,
            
            @Size(max = 20, message = "Too many metadata entries (max 20)")
            @Valid
            List<Meta> meta,
            
            @Size(max = 1048576, message = "Document XML too large (max 1MB)")
            String documentXml,
            
            @Valid
            DocumentData document // Для CMS и SIGN_BYTES_ARRAY
    ) {}

    @Builder
    public record Meta(
            @NotNull(message = "Meta name is required")
            @Size(max = 100, message = "Meta name too long (max 100 characters)")
            String name,
            
            @NotNull(message = "Meta value is required")
            @Size(max = 500, message = "Meta value too long (max 500 characters)")
            String value
    ) {}

    @Builder
    public record DocumentData(
            File file
    ) {}

    @Builder
    public record File(
            @NotNull(message = "MIME type is required")
            @Size(max = 100, message = "MIME type too long (max 100 characters)")
            String mime,
            
            @NotNull(message = "File data is required")
            @Size(max = 52428800, message = "File data too large (max 50MB)")
            String data
    ) {}
}