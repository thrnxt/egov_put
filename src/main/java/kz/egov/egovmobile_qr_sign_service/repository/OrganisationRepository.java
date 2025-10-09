package kz.egov.egovmobile_qr_sign_service.repository;

import kz.egov.egovmobile_qr_sign_service.model.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
    Optional<Organisation> findByBin(String bin);
    boolean existsByBin(String bin);
}




