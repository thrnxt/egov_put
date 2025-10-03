package kz.egov.egovmobile_qr_sign_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SignErrorResponse {
    String message;
}
