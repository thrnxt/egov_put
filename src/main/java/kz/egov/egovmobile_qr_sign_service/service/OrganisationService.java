package kz.egov.egovmobile_qr_sign_service.service;

import kz.egov.egovmobile_qr_sign_service.dto.InitSignRequest;
import kz.egov.egovmobile_qr_sign_service.model.Organisation;
import kz.egov.egovmobile_qr_sign_service.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganisationService {

    private final OrganisationRepository organisationRepository;

    /**
     * Найти или создать организацию по данным из запроса
     * Если организация с таким БИН существует - возвращаем её
     * Если нет - создаём новую
     */
    @Transactional
    public Organisation findOrCreateOrganisation(InitSignRequest.Organisation orgData) {
        if (orgData == null || orgData.getBin() == null || orgData.getBin().isBlank()) {
            log.warn("Organisation data is null or BIN is empty, creating default organisation");
            // Создаём организацию по умолчанию
            return createDefaultOrganisation();
        }

        String bin = orgData.getBin().trim();
        
        log.debug("Looking for organisation with BIN: {}", bin);
        
        Optional<Organisation> existing = organisationRepository.findByBin(bin);
        
        if (existing.isPresent()) {
            log.debug("Organisation found with BIN: {}", bin);
            // Можно здесь обновить данные организации, если они изменились
            Organisation org = existing.get();
            boolean needsUpdate = false;
            
            if (orgData.getNameRu() != null && !orgData.getNameRu().equals(org.getNameRu())) {
                org.setNameRu(orgData.getNameRu());
                needsUpdate = true;
            }
            if (orgData.getNameKz() != null && !orgData.getNameKz().equals(org.getNameKz())) {
                org.setNameKz(orgData.getNameKz());
                needsUpdate = true;
            }
            if (orgData.getNameEn() != null && !orgData.getNameEn().equals(org.getNameEn())) {
                org.setNameEn(orgData.getNameEn());
                needsUpdate = true;
            }
            
            if (needsUpdate) {
                log.info("Updating organisation data for BIN: {}", bin);
                org.setUpdatedAt(ZonedDateTime.now());
                return organisationRepository.save(org);
            }
            
            return org;
        } else {
            log.info("Creating new organisation with BIN: {}", bin);
            Organisation newOrg = Organisation.builder()
                    .bin(bin)
                    .nameRu(orgData.getNameRu())
                    .nameKz(orgData.getNameKz())
                    .nameEn(orgData.getNameEn())
                    .createdAt(ZonedDateTime.now())
                    .updatedAt(ZonedDateTime.now())
                    .build();
            
            return organisationRepository.save(newOrg);
        }
    }

    /**
     * Создать организацию по умолчанию (если данные не переданы)
     */
    private Organisation createDefaultOrganisation() {
        String defaultBin = "000000000000";
        
        Optional<Organisation> existing = organisationRepository.findByBin(defaultBin);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        Organisation defaultOrg = Organisation.builder()
                .bin(defaultBin)
                .nameRu("Неизвестная организация")
                .nameKz("Белгісіз ұйым")
                .nameEn("Unknown organisation")
                .createdAt(ZonedDateTime.now())
                .updatedAt(ZonedDateTime.now())
                .build();
        
        return organisationRepository.save(defaultOrg);
    }

    public Optional<Organisation> findById(Long id) {
        return organisationRepository.findById(id);
    }

    public Optional<Organisation> findByBin(String bin) {
        return organisationRepository.findByBin(bin);
    }
}




