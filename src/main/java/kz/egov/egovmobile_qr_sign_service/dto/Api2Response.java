package kz.egov.egovmobile_qr_sign_service.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.util.List;

@Builder
public record Api2Response(
        String signMethod, // XML, CMS_WITH_DATA, etc.
        int version, // По умолчанию 1
        List<DocumentToSign> documentsToSign
) {
    @Builder
    public record DocumentToSign(
            int id,
            String signMethod, // Только для MIX_SIGN
            @JsonProperty("nameRu") String nameRu,
            @JsonProperty("nameKz") String nameKz,
            @JsonProperty("nameEn") String nameEn,
            List<Meta> meta,
            String documentXml,
            DocumentData document // Для CMS и SIGN_BYTES_ARRAY
    ) {}

    @Builder
    public record Meta(
            String name,
            String value
    ) {}

    @Builder
    public record DocumentData(
            File file
    ) {}

    @Builder
    public record File(
            String mime,
            String data
    ) {}
}