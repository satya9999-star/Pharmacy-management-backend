package com.pharmacy.controller;

import com.pharmacy.dto.AnalyticsDtos.*;
import com.pharmacy.dto.AuthDtos.*;
import com.pharmacy.dto.BillingDtos.*;
import com.pharmacy.dto.ConfigDtos.*;
import com.pharmacy.dto.DistributorDtos.*;
import com.pharmacy.dto.InventoryDtos.*;
import com.pharmacy.service.AiPharmacistService;
import com.pharmacy.service.MasterMedicineService;
import com.pharmacy.service.PharmacyService;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = { "http://localhost:4200", "http://127.0.0.1:4200" })
public class PharmacyController {
    private final PharmacyService pharmacy;
    private final AiPharmacistService aiPharmacist;
    private final MasterMedicineService masterMedicineService;

    public PharmacyController(PharmacyService pharmacy, AiPharmacistService aiPharmacist,
            MasterMedicineService masterMedicineService) {
        this.pharmacy = pharmacy;
        this.aiPharmacist = aiPharmacist;
        this.masterMedicineService = masterMedicineService;
    }

    @GetMapping("/medicines/master/search")
    public List<MasterMedicineView> searchMasterMedicines(@RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {
        return masterMedicineService.search(query, limit);
    }

    @PostMapping("/medicines/master/reload-csv")
    @PreAuthorize("hasRole('ADMIN')")
    public String reloadMasterCsv() {
        masterMedicineService.reloadCsvData();
        return "Master medicine CSV re-seeding initiated successfully.";
    }

    @GetMapping("/ai-pharmacist/search")
    public String searchAiPharmacist(@RequestParam String medicine) {
        return aiPharmacist.searchMedicineDetails(medicine);
    }

    @PostMapping("/ai-pharmacist/translate")
    public String translateAiPharmacist(@Valid @RequestBody TranslateRequest request) {
        return aiPharmacist.translateDetails(request.detailsJson(), request.targetLanguage());
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard() {
        return pharmacy.dashboard();
    }

    @GetMapping("/medicines")
    public List<MedicineView> medicines() {
        return pharmacy.medicines();
    }

    @PostMapping("/medicines")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PHARMACIST')")
    public MedicineView addMedicine(@Valid @RequestBody MedicineRequest request) {
        return pharmacy.addMedicine(request);
    }

    @PutMapping("/medicines/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PHARMACIST')")
    public MedicineView updateMedicine(@PathVariable Long id, @Valid @RequestBody MedicineRequest request) {
        return pharmacy.updateMedicine(id, request);
    }

    @PutMapping("/medicines/{id}/order-status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PHARMACIST') or hasRole('STAFF')")
    public MedicineView updateOrderStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        return pharmacy.updateOrderStatus(id, request);
    }

    @PostMapping("/batches")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PHARMACIST')")
    public BatchView addBatch(@Valid @RequestBody BatchRequest request) {
        return pharmacy.addBatch(request);
    }

    @PutMapping("/batches/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BatchView updateBatch(@PathVariable Long id, @Valid @RequestBody BatchEditRequest request) {
        return pharmacy.updateBatch(id, request);
    }

    @GetMapping("/distributors")
    public List<DistributorView> distributors() {
        return pharmacy.distributors();
    }

    @PostMapping("/distributors")
    @PreAuthorize("hasRole('ADMIN')")
    public DistributorView addDistributor(@Valid @RequestBody DistributorRequest request) {
        return pharmacy.addDistributor(request);
    }

    @PutMapping("/distributors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DistributorView updateDistributor(@PathVariable Long id, @Valid @RequestBody DistributorRequest request) {
        return pharmacy.updateDistributor(id, request);
    }

    @GetMapping("/distributors/{id}/bills")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PHARMACIST')")
    public List<DistributorBillView> distributorBills(@PathVariable Long id) {
        return pharmacy.distributorBills(id);
    }

    @GetMapping("/distributors/bills")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DistributorBillView> allDistributorBills() {
        return pharmacy.allDistributorBills();
    }

    @GetMapping("/distributors/bills/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PHARMACIST')")
    public DistributorBillView distributorBill(@PathVariable Long id) {
        return pharmacy.distributorBill(id);
    }

    @GetMapping("/distributors/{id}/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DistributorPaymentView> distributorPayments(@PathVariable Long id) {
        return pharmacy.distributorPayments(id);
    }

    @PostMapping("/distributors/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public DistributorBillView payDistributorBill(@Valid @RequestBody DistributorPaymentRequest request) {
        return pharmacy.payDistributorBill(request);
    }

    @PostMapping("/distributors/bills/bulk")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PHARMACIST')")
    public DistributorBillView uploadBulkBill(@Valid @RequestBody BulkBillRequest request) {
        return pharmacy.uploadBulkBill(request);
    }

    @DeleteMapping("/distributors/bills/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDistributorBill(@PathVariable Long id) {
        pharmacy.deleteDistributorBill(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/customers")
    public List<CustomerView> customers() {
        return pharmacy.customers();
    }

    @PostMapping("/customers")
    public CustomerView addCustomer(@Valid @RequestBody CustomerRequest request) {
        return pharmacy.addCustomer(request);
    }

    @PutMapping("/customers/{id}")
    public CustomerView updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return pharmacy.updateCustomer(id, request);
    }

    @PostMapping("/sales")
    public SaleView createSale(@Valid @RequestBody SaleRequest request, @AuthenticationPrincipal UserDetails user) {
        return pharmacy.createSale(request, user.getUsername());
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PHARMACIST')")
    public List<SaleView> sales() {
        return pharmacy.sales();
    }

    @GetMapping("/sales/{id}")
    public SaleView sale(@PathVariable Long id) {
        return pharmacy.sale(id);
    }

    @GetMapping("/sales/bill/{billNo}")
    public SaleView saleByBillNo(@PathVariable String billNo) {
        return pharmacy.saleByBillNo(billNo);
    }

    @GetMapping("/customers/{id}/credits")
    public List<CreditView> customerCredits(@PathVariable Long id) {
        return pharmacy.customerCredits(id);
    }

    @GetMapping("/credits/outstanding")
    public List<CreditView> credits() {
        return pharmacy.openCredits();
    }

    @GetMapping("/credits/settled")
    public List<CreditView> settledCredits() {
        return pharmacy.settledCredits();
    }

    @GetMapping("/credits/all")
    public List<CreditView> allCredits() {
        return pharmacy.allCredits();
    }

    @PostMapping("/credits/payment")
    public CreditView pay(@Valid @RequestBody CreditPaymentRequest request) {
        return pharmacy.payCredit(request);
    }

    @PostMapping("/credits/seed-test-data")
    public java.util.Map<String, Object> seedTestCreditCustomers() {
        int seededBills = pharmacy.seedTestCreditCustomers();
        return java.util.Map.of("status", "SUCCESS", "seededBills", seededBills, "message", "10 test credit customers initialized successfully.");
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public AnalyticsView analytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "30") int days) {
        return pharmacy.analytics(startDate, endDate, days);
    }

    @GetMapping("/distributor-comparison")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DistributorPriceComparison> distributorComparison() {
        return pharmacy.distributorComparison();
    }

    @GetMapping("/activity-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ActivityLogView> activityLogs() {
        return pharmacy.getActivityLogs();
    }

    @GetMapping("/settings/store-config")
    public StoreConfigDto getStoreConfig() {
        return pharmacy.getStoreConfig();
    }

    @PostMapping("/settings/store-config")
    @PreAuthorize("hasRole('ADMIN')")
    public StoreConfigDto saveStoreConfig(@Valid @RequestBody StoreConfigDto config) {
        return pharmacy.saveStoreConfig(config);
    }

    @PostMapping("/sales/{id}/send-whatsapp")
    public void sendWhatsAppInvoice(@PathVariable Long id, @RequestBody(required = false) WhatsAppPdfRequest pdfReq) {
        if (pdfReq != null) {
            if (pdfReq.pdfBase64() != null && pdfReq.pdfBase64().length() > 5_000_000) {
                throw new IllegalArgumentException("PDF payload exceeds allowable size limit (5MB).");
            }
            pharmacy.sendWhatsAppInvoice(id, pdfReq.pdfBase64(), pdfReq.filename());
        } else {
            pharmacy.sendWhatsAppInvoice(id);
        }
    }

    @PostMapping("/credits/{id}/send-reminder")
    @PreAuthorize("hasRole('ADMIN')")
    public void sendCreditReminder(@PathVariable Long id) {
        pharmacy.sendCreditReminder(id);
    }

    @PostMapping("/settings/whatsapp/trigger-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    public void triggerDailyReminders() {
        pharmacy.triggerDailyReminders();
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> users() {
        return pharmacy.users();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView createUser(@Valid @RequestBody UserRequest request) {
        return pharmacy.createUser(request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return pharmacy.updateUser(id, request);
    }

    @PutMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        return pharmacy.resetUserPassword(id, request);
    }

    @PutMapping("/users/{id}/toggle-active")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView toggleActive(@PathVariable Long id, @RequestParam boolean active) {
        return pharmacy.toggleUserActiveStatus(id, active);
    }

    @PostMapping("/users/{id}/update-mobile-otp")
    @PreAuthorize("hasRole('ADMIN')")
    public void sendUpdateMobileOtp(@PathVariable Long id, @RequestParam String mobile) {
        pharmacy.sendUpdateMobileOtp(id, mobile);
    }

    @PostMapping("/users/{id}/delete-otp")
    @PreAuthorize("hasRole('ADMIN')")
    public void sendDeleteUserOtp(@PathVariable Long id, @AuthenticationPrincipal UserDetails admin) {
        pharmacy.sendDeleteUserOtp(id, admin.getUsername());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(@PathVariable Long id, @RequestParam String otp,
            @AuthenticationPrincipal UserDetails admin) {
        pharmacy.deleteUser(id, otp, admin.getUsername());
    }
}
