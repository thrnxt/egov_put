package kz.egov.egovmobile_qr_sign_service.repository;


import kz.egov.egovmobile_qr_sign_service.model.SignTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<SignTransaction, String> {
}