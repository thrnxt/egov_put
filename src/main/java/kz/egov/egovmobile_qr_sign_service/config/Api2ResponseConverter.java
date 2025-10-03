package kz.egov.egovmobile_qr_sign_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import kz.egov.egovmobile_qr_sign_service.dto.Api2Response;

@Converter(autoApply = true)
public class Api2ResponseConverter implements AttributeConverter<Api2Response, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Api2Response api2Response) {
        if (api2Response == null) {
            return null;
        }
        try {
            // Преобразуем объект в JSON строку
            return objectMapper.writeValueAsString(api2Response);
        } catch (JsonProcessingException e) {
            // В реальном проекте здесь должно быть более сложное логирование
            throw new RuntimeException("Could not convert Api2Response to JSON string", e);
        }
    }

    @Override
    public Api2Response convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            // Преобразуем JSON строку обратно в объект
            return objectMapper.readValue(dbData, Api2Response.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not convert JSON string to Api2Response object", e);
        }
    }
}