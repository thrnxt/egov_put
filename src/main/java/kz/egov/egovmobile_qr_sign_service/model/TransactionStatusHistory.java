package kz.egov.egovmobile_qr_sign_service.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.ZonedDateTime;

@Entity
@Table(name = "transaction_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "old_status", length = 50)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @Builder.Default
    @Column(name = "changed_at", nullable = false)
    private ZonedDateTime changedAt = ZonedDateTime.now();

    @Column(name = "changed_reason", columnDefinition = "TEXT")
    private String changedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id", insertable = false, updatable = false)
    private SignTransaction transaction;
}

