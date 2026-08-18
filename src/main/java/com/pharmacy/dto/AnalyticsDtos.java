package com.pharmacy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record DashboardView(BigDecimal todaySales, BigDecimal monthRevenue, long lowStockBatches,
                                long expiringBatches, BigDecimal pendingCredits, long todayBills,
                                BigDecimal customerCredits, BigDecimal paymentsToDistributors,
                                BigDecimal customerDues, BigDecimal distributorPurchases,
                                BigDecimal distributorDues, BigDecimal expiredCost,
                                BigDecimal totalExpenditure, BigDecimal expWages,
                                BigDecimal expBills, BigDecimal expMaintenance,
                                BigDecimal expMisc) {}

    public record DailySalesPoint(LocalDate date, BigDecimal revenue, long billCount) {}
    public record TopMedicineView(String name, long totalQuantity, BigDecimal totalRevenue) {}
    public record CategoryRevenueView(String category, BigDecimal totalRevenue) {}
    public record PaymentModeShare(String mode, BigDecimal totalRevenue) {}
    public record AnalyticsView(List<DailySalesPoint> dailySales, List<TopMedicineView> topMedicines,
                                List<CategoryRevenueView> categoryRevenue, List<TopMedicineView> slowMedicines,
                                List<PaymentModeShare> paymentModeShare) {}

    public record ActivityLogView(Long id, String action, String performedBy, String details,
                                  String entityType, Long entityId, Instant createdAt) {}
}
