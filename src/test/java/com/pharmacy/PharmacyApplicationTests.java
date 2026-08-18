package com.pharmacy;

import com.pharmacy.dto.BillingDtos.SaleLineRequest;
import com.pharmacy.dto.BillingDtos.SaleRequest;
import com.pharmacy.model.PaymentMode;
import com.pharmacy.service.PharmacyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
class PharmacyApplicationTests {
    @Autowired
    PharmacyService pharmacy;

    @Test
    void cashSaleReducesStockAndCreatesInvoice() {
        long startingStock = pharmacy.medicines().stream()
                .filter(medicine -> medicine.code().equals("MED-001"))
                .findFirst()
                .orElseThrow()
                .availableQuantity();

        var sale = pharmacy.createSale(new SaleRequest(null, null, null, List.of(new SaleLineRequest(1L, BigDecimal.valueOf(20), 10)),
                PaymentMode.CASH, BigDecimal.ZERO, null), "staff");

        assertThat(sale.billNo()).startsWith("RX-");
        assertThat(sale.netAmount()).isEqualByComparingTo("63.00");
        assertThat(pharmacy.medicines().stream()
                .filter(medicine -> medicine.code().equals("MED-001"))
                .findFirst()
                .orElseThrow()
                .availableQuantity()).isEqualTo(startingStock - 2);
    }
}
