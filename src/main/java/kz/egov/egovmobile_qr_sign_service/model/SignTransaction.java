package kz.egov.egovmobile_qr_sign_service.model;

import jakarta.persistence.*;
import kz.egov.egovmobile_qr_sign_service.config.Api2ResponseConverter;
import kz.egov.egovmobile_qr_sign_service.dto.Api2Response;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "sign_transactions")
@Data
@NoArgsConstructor
public class SignTransaction {

    @Id
    private String transactionId; // Используем внешний ID как PK

    @Column(nullable = false)
    private ZonedDateTime creationDate = ZonedDateTime.now();

    @Column(nullable = false)
    private ZonedDateTime expiryDate;

    @Column(nullable = false)
    private String authType; // Token, Eds, None

    // В реале: токен может быть зашифрован или храниться в другом месте
    @Column
    private String authToken;

    @Column(nullable = false)
    private String description;

    // Organisation info for API #1
    @Column private String orgNameRu;
    @Column private String orgNameKz;
    @Column private String orgNameEn;
    @Column private String orgBin;

    @Column(nullable = false)
    private String api2Uri;

    @Column(nullable = false)
    private String backUrl;

    @Column(nullable = false)
    private String status; // PENDING, SIGNED, FAILED

    // Документы для подписания (хранятся как JSON в БД)
    @Lob
    @Convert(converter = Api2ResponseConverter.class)
    @Column(name = "documents_to_sign", columnDefinition = "TEXT")
    private Api2Response documentsForSigning;

    // Результат подписания (хранятся как JSON в БД)
    @Lob
    @Convert(converter = Api2ResponseConverter.class)
    @Column(name = "signed_documents", columnDefinition = "TEXT")
    private Api2Response signedDocuments;
}