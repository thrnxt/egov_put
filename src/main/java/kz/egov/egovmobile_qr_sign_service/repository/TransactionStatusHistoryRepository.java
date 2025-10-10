package kz.egov.egovmobile_qr_sign_service.repository;

import kz.egov.egovmobile_qr_sign_service.model.TransactionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionStatusHistoryRepository extends JpaRepository<TransactionStatusHistory, Long> {
    List<TransactionStatusHistory> findByTransactionIdOrderByChangedAtDesc(String transactionId);
    TransactionStatusHistory findFirstByTransactionIdOrderByChangedAtDesc(String transactionId);
}




