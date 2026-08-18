package com.pharmacy.repository;

import com.pharmacy.model.MasterMedicine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MasterMedicineRepository extends JpaRepository<MasterMedicine, Long> {
    long countByMedicineDescIsNull();

    @Query("SELECT m FROM MasterMedicine m WHERE " +
           "(COALESCE(LOWER(m.name), '') LIKE LOWER(CONCAT('%', :term1, '%')) OR COALESCE(LOWER(m.saltComposition), '') LIKE LOWER(CONCAT('%', :term1, '%')) OR COALESCE(LOWER(m.manufacturerName), '') LIKE LOWER(CONCAT('%', :term1, '%'))) AND " +
           "(:term2 IS NULL OR COALESCE(LOWER(m.name), '') LIKE LOWER(CONCAT('%', :term2, '%')) OR COALESCE(LOWER(m.saltComposition), '') LIKE LOWER(CONCAT('%', :term2, '%')) OR COALESCE(LOWER(m.manufacturerName), '') LIKE LOWER(CONCAT('%', :term2, '%')))")
    List<MasterMedicine> searchByTerms(@Param("term1") String term1, @Param("term2") String term2, Pageable pageable);
}
