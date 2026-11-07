package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.exception.MedicationNotFoundException;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.exception.ShipNotFoundException;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.dto.StockRequirementMapper;
import com.shipmed.shipmed.domain.stock.dto.request.StockRequirementCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementListDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementResult;
import com.shipmed.shipmed.domain.stock.entity.StockRequirement;
import com.shipmed.shipmed.domain.stock.exception.StockRequirementConflictException;
import com.shipmed.shipmed.domain.stock.repository.StockRequirementRepository;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class StockRequirementServiceImpl implements StockRequirementService {

    private static final String UQ_REQUIREMENT = "uq_requirement";

    private final StockRequirementRepository stockRequirementRepository;
    private final ShipRepository shipRepository;
    private final MedicationRepository medicationRepository;
    private final StockRequirementMapper stockRequirementMapper;

    @Transactional
    @Override
    public StockRequirementResult upsertRequirement(Long shipId, Long medicationId, StockRequirementCreateRequest stockRequirementCreateRequest) {
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(ShipNotFoundException::new);

        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(MedicationNotFoundException::new);

        StockRequirement stockRequirement = stockRequirementRepository.findByShipIdAndMedicationId(shipId, medicationId);

        if (stockRequirement != null) {
            stockRequirement.changeMinQuantity(stockRequirementCreateRequest.minQuantity());

            return new StockRequirementResult(stockRequirementMapper.toDto(stockRequirement), false);
        }

        StockRequirement newRequirement = new StockRequirement(
                stockRequirementCreateRequest.minQuantity(),
                ship,
                medication
        );

        try {
            stockRequirementRepository.saveAndFlush(newRequirement);
        } catch (DataIntegrityViolationException ex) {
            // 신규 요청이 겹친 경우. 다른 제약 위반은 409로 바꾸지 않는다.
            if (ex.getCause() instanceof ConstraintViolationException cause
                    && UQ_REQUIREMENT.equals(cause.getConstraintName())) {
                throw new StockRequirementConflictException();
            }
            throw ex;
        }

        return new StockRequirementResult(stockRequirementMapper.toDto(newRequirement), true);
    }

    @Override
    public PageResponse<StockRequirementListDto> getRequirements(Long shipId, PageQuery pageQuery) {
        boolean existsShip = shipRepository.existsById(shipId);

        if (!existsShip) {
            throw new ShipNotFoundException();
        }

        Page<StockRequirement> requirements = stockRequirementRepository.findAllByShip(pageQuery.toPageable(), shipId);

        return PageResponse.from(requirements.map(stockRequirementMapper::toDtos));
    }
}
