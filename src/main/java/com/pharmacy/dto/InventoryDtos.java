package com.pharmacy.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record MedicineRequest(@NotBlank String code, @NotBlank String name, String genericName,
                                  String manufacturer, String category, String hsnCode,
                                  @NotNull @PositiveOrZero BigDecimal gstPercentage,
                                  @NotNull @Positive BigDecimal mrp,
                                  @NotNull @Positive BigDecimal sellingPrice,
                                  boolean prescriptionRequired,
                                  @NotNull @Min(0) Integer stockWatchQty,
                                  String sideEffects) {}

    public record BatchRequest(@NotNull Long medicineId, @NotBlank String batchNo,
                               @NotNull @Future LocalDate expiryDate,
                               @NotNull @Positive BigDecimal purchasePrice,
                               @NotNull @Positive BigDecimal sellingPrice,
                               @NotNull @Positive BigDecimal mrp,
                               @Positive int quantity, Long distributorId,
                               String billNo, LocalDate billDate, LocalDate dueDate) {}

    public record BatchEditRequest(@NotBlank String batchNo,
                                   @NotNull LocalDate expiryDate,
                                   @NotNull @Positive BigDecimal purchasePrice,
                                   @NotNull @Positive BigDecimal sellingPrice,
                                   @NotNull @Positive BigDecimal mrp,
                                   @Min(0) int availableQuantity) {}

    public record MedicineView(Long id, String code, String name, String genericName, String manufacturer,
                               String category, String hsnCode, BigDecimal gstPercentage, BigDecimal mrp,
                               BigDecimal sellingPrice, boolean prescriptionRequired, Integer stockWatchQty,
                               long availableQuantity, List<BatchView> batches,
                               String orderStatus, LocalDate orderedDate, Long orderedDistributorId, String orderedDistributorName,
                               Integer orderedQuantity, String sideEffects) {}

    public record OrderStatusRequest(@NotBlank String status, String orderedDate, Long distributorId, Integer orderedQuantity) {}

    public record BatchView(Long id, String batchNo, LocalDate expiryDate, BigDecimal purchasePrice, BigDecimal sellingPrice,
                            int availableQuantity, int quantity, BigDecimal gstPercentage, BigDecimal mrp,
                            String distributorName, String billNo, LocalDate billDate, LocalDate dueDate,
                            Integer free, BigDecimal discountPercentage,
                            Long medicineId, String medicineCode, String medicineName,
                            String manufacturer, String category, String hsnCode,
                            int looseUnitsAvailable) {}

    public record BulkBatchItemRequest(
        @NotBlank String medicineCode,
        String medicineName,
        String genericName,
        String manufacturer,
        String hsnCode,
        String category,
        @NotBlank String batchNo,
        @NotNull LocalDate expiryDate,
        @NotNull @Positive BigDecimal purchasePrice,
        @NotNull @Positive BigDecimal sellingPrice,
        @Positive int quantity,
        @NotNull @Positive BigDecimal mrp,
        @NotNull @PositiveOrZero BigDecimal gstPercentage,
        Integer free,
        BigDecimal discountPercentage,
        String sideEffects
    ) {}

    public record BulkBillRequest(
        @NotNull Long distributorId,
        @NotBlank String billNo,
        @NotNull LocalDate billDate,
        @NotNull LocalDate dueDate,
        @NotEmpty List<BulkBatchItemRequest> items
    ) {}

    public record MasterMedicineView(
        Long id,
        String name,
        String saltComposition,
        String medicineDesc,
        String sideEffects,
        String drugInteractions,
        String manufacturerName,
        String category,
        BigDecimal price,
        String packSizeLabel,
        boolean discontinued
    ) {}
}
