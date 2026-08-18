package com.pharmacy.repository;

import com.pharmacy.model.MedicineBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {
    @Query("select b from MedicineBatch b where b.medicine.id = :medicineId and (b.availableQuantity > 0 or b.looseUnitsAvailable > 0) order by b.expiryDate asc, b.createdAt asc")
    List<MedicineBatch> findSellableBatches(@Param("medicineId") Long medicineId);

    @Query("select coalesce(sum(b.availableQuantity), 0) from MedicineBatch b where b.medicine.id = :medicineId and b.expiryDate >= current_date")
    long availableForMedicine(@Param("medicineId") Long medicineId);

    long countByAvailableQuantityLessThanEqual(int threshold);
    long countByExpiryDateBetween(LocalDate from, LocalDate to);

    List<MedicineBatch> findByDistributorBillId(Long distributorBillId);

    @Query("select b from MedicineBatch b where b.medicine.id = :medicineId and b.batchNo = :batchNo and (b.availableQuantity > 0 or b.looseUnitsAvailable > 0)")
    List<MedicineBatch> findByMedicineIdAndBatchNo(@Param("medicineId") Long medicineId, @Param("batchNo") String batchNo);

    @Query("select coalesce(sum(b.availableQuantity * b.purchasePrice), 0) from MedicineBatch b where b.expiryDate < :today")
    BigDecimal expiredCost(@Param("today") LocalDate today);
}
