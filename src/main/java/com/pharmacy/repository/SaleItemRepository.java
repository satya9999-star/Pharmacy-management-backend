package com.pharmacy.repository;

import com.pharmacy.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    @Query("select si.batch.medicine.name, sum(si.quantity) as totalQty, sum(si.totalAmount) as totalRevenue " +
           "from SaleItem si where si.sale.createdAt between :from and :to " +
           "group by si.batch.medicine.name order by totalRevenue desc")
    List<Object[]> topSellingMedicines(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select si.batch.medicine.category, sum(si.totalAmount) as totalRevenue " +
           "from SaleItem si where si.sale.createdAt between :from and :to " +
           "group by si.batch.medicine.category order by totalRevenue desc")
    List<Object[]> categoryWiseRevenue(@Param("from") Instant from, @Param("to") Instant to);

    List<SaleItem> findByBatchId(Long batchId);
}
