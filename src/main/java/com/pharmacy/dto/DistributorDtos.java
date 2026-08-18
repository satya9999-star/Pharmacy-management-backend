package com.pharmacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DistributorDtos {
    private DistributorDtos() {}

    public record DistributorRequest(@NotBlank String name, String contactPerson, String mobile,
                                     String email, String gstNumber, String address, String upiId,
                                     String bankName, String bankAccountNo, String bankIfscCode) {}

    public record DistributorView(Long id, String name, String contactPerson, String mobile,
                                  String email, String gstNumber, String address, String upiId,
                                  String bankName, String bankAccountNo, String bankIfscCode) {}

    public record DistributorBillView(Long id, String distributorName, String billNo, LocalDate billDate, LocalDate dueDate,
                                      BigDecimal totalAmount, BigDecimal gstAmount, BigDecimal netAmount,
                                      BigDecimal paidAmount, BigDecimal dueAmount, String status,
                                      List<InventoryDtos.BatchView> items) {}

    public record DistributorPaymentRequest(@NotNull Long distributorId, Long billId,
                                            @NotNull @Positive BigDecimal amount,
                                            @NotBlank String paymentMode, String referenceNo) {}

    public record DistributorPaymentView(Long id, String billNo, BigDecimal amount,
                                         Instant paymentDate, String paymentMode, String referenceNo) {}

    public record DistributorPriceEntry(String distributorName, BigDecimal purchasePrice, BigDecimal sellingPrice,
                                        BigDecimal mrp, String batchNo, LocalDate expiryDate, int availableQuantity,
                                        String billNo, LocalDate billDate) {}

    public record DistributorPriceComparison(Long medicineId, String medicineName, String genericName,
                                             List<DistributorPriceEntry> distributorPrices) {}
}
