package com.pharmacy.dto;

import com.pharmacy.model.CreditStatus;
import com.pharmacy.model.PaymentMode;
import com.pharmacy.model.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BillingDtos {
    private BillingDtos() {}

    public record CustomerRequest(@NotBlank String name, String mobile, String address,
                                  @NotNull @PositiveOrZero BigDecimal creditLimit) {}

    public record CustomerView(Long id, String name, String mobile, String address,
                               BigDecimal creditLimit, BigDecimal outstanding) {}

    public record SaleLineRequest(@NotNull Long medicineId, @NotNull @Positive BigDecimal quantity, int packSize, Long batchId, String batchNo) {
        public SaleLineRequest(Long medicineId, BigDecimal quantity, int packSize) {
            this(medicineId, quantity, packSize, null, null);
        }
    }

    public record SaleRequest(Long customerId, String customerAge, String doctorName,
                              @NotEmpty List<@Valid SaleLineRequest> items,
                              @NotNull PaymentMode paymentMode,
                              @NotNull @PositiveOrZero BigDecimal discountAmount,
                              LocalDate creditDueDate) {}

    public record SaleView(Long id, String billNo, String customerName, String customerMobile, String customerAddress,
                           String customerAge, String doctorName,
                           BigDecimal totalAmount,
                           BigDecimal discountAmount, BigDecimal gstAmount, BigDecimal roundingAmount,
                           BigDecimal netAmount,
                           PaymentMode paymentMode, PaymentStatus paymentStatus, Instant createdAt,
                           List<SaleLineView> items,
                           BigDecimal purchaseBase, BigDecimal profit, BigDecimal inputGst, BigDecimal gstPayable) {}

    public record SaleLineView(String medicineName, String manufacturer, String batchNo, LocalDate expiryDate,
                               BigDecimal quantity, BigDecimal sellingPrice,
                               BigDecimal gstPercentage, BigDecimal totalAmount, BigDecimal gstAmount,
                               BigDecimal mrp, BigDecimal discount, String category) {}

    public record CreditPaymentRequest(@NotNull Long creditId, @NotNull @Positive BigDecimal amount,
                                       String paymentMode, String referenceNo) {}

    public record CustomerPaymentView(Long id, BigDecimal amount, Instant paymentDate, String paymentMode, String referenceNo) {}

    public record CreditView(Long id, Long customerId, String customerName, String billNo,
                             LocalDate billDate, BigDecimal creditAmount, BigDecimal paidAmount, BigDecimal dueAmount,
                             LocalDate dueDate, CreditStatus status, List<CustomerPaymentView> payments) {}

    public record WhatsAppPdfRequest(String pdfBase64, String filename) {}
}
