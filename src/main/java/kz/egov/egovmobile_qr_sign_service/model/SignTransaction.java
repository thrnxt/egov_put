package kz.egov.egovmobile_qr_sign_service.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import kz.egov.egovmobile_qr_sign_service.dto.Api2Response;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.ZonedDateTime;

@Entity
@Table(name = "sign_transactions", indexes = {
        @Index(name = "idx_transactions_status", columnList = "status"),
        @Index(name = "idx_transactions_expiry_date", columnList = "expiry_date"),
        @Index(name = "idx_transactions_creation_date", columnList = "creation_date"),
        @Index(name = "idx_transactions_organisation", columnList = "organisation_id")
})
@Data
@NoArgsConstructor
public class SignTransaction {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", referencedColumnName = "id")
    private Organisation organisation;

    @Column(name = "creation_date", nullable = false)
    private ZonedDateTime creationDate = ZonedDateTime.now();

    @Column(name = "expiry_date", nullable = false)
    private ZonedDateTime expiryDate;

    @Column(name = "auth_type", nullable = false, length = 50)
    private String authType; // Token, Eds, None

    // Хешированный токен (BCrypt)
    @Column(name = "auth_token_hash", length = 255)
    private String authTokenHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "api2_uri", nullable = false, length = 512)
    private String api2Uri;

    @Column(name = "back_url", nullable = false, length = 512)
    private String backUrl;

    @Column(nullable = false, length = 50)
    private String status; // PENDING, SIGNED, FAILED

    // Документы для подписания (хранятся как JSONB в PostgreSQL)
    @Type(JsonBinaryType.class)
    @Column(name = "documents_to_sign", columnDefinition = "jsonb")
    private Api2Response documentsForSigning;

    // Результат подписания (хранятся как JSONB в PostgreSQL)
    @Type(JsonBinaryType.class)
    @Column(name = "signed_documents", columnDefinition = "jsonb")
    private Api2Response signedDocuments;
}