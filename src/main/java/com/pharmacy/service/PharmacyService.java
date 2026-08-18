package com.pharmacy.service;

import com.pharmacy.dto.AnalyticsDtos.*;
import com.pharmacy.dto.AuthDtos.*;
import com.pharmacy.dto.BillingDtos.*;
import com.pharmacy.dto.ConfigDtos.*;
import com.pharmacy.dto.DistributorDtos.*;
import com.pharmacy.dto.InventoryDtos.*;
import com.pharmacy.model.*;
import com.pharmacy.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class PharmacyService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final MedicineRepository medicines;
    private final MedicineBatchRepository batches;
    private final DistributorRepository distributors;
    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final CreditTransactionRepository credits;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final DistributorBillRepository distributorBills;
    private final DistributorPaymentRepository distributorPayments;
    private final CustomerPaymentRepository customerPayments;
    private final ExpenseRepository expenses;
    private final ActivityLogRepository activityLogs;
    private final StoreConfigRepository configRepository;
    private final WhatsAppService whatsAppService;
    private final PasswordEncoder passwordEncoder;

    public PharmacyService(MedicineRepository medicines, MedicineBatchRepository batches, DistributorRepository distributors,
                           CustomerRepository customers, SaleRepository sales, SaleItemRepository saleItems,
                           CreditTransactionRepository credits, StockMovementRepository movements,
                           UserRepository users, DistributorBillRepository distributorBills,
                           DistributorPaymentRepository distributorPayments, CustomerPaymentRepository customerPayments,
                           ExpenseRepository expenses, ActivityLogRepository activityLogs,
                           StoreConfigRepository configRepository, WhatsAppService whatsAppService, PasswordEncoder passwordEncoder) {
        this.medicines = medicines;
        this.batches = batches;
        this.distributors = distributors;
        this.customers = customers;
        this.sales = sales;
        this.saleItems = saleItems;
        this.credits = credits;
        this.movements = movements;
        this.users = users;
        this.distributorBills = distributorBills;
        this.distributorPayments = distributorPayments;
        this.customerPayments = customerPayments;
        this.expenses = expenses;
        this.activityLogs = activityLogs;
        this.configRepository = configRepository;
        this.whatsAppService = whatsAppService;
        this.passwordEncoder = passwordEncoder;
    }

    public MedicineView addMedicine(MedicineRequest request) {
        medicines.findByCode(request.code()).ifPresent(existing -> {
            throw new IllegalArgumentException("Medicine code already exists");
        });
        Medicine saved = medicines.save(new Medicine(
                request.code(), request.name(), request.genericName(), request.manufacturer(), request.category(),
                request.hsnCode(), money(request.gstPercentage()), money(request.mrp()), money(request.sellingPrice()),
                request.prescriptionRequired(), request.stockWatchQty(), request.sideEffects()));
        logActivity("MEDICINE_ADDED", "Added medicine: " + saved.name + " (" + saved.code + ")", "Medicine", saved.id);
        return medicineView(saved);
    }

    public MedicineView updateMedicine(Long id, MedicineRequest request) {
        Medicine medicine = medicines.findById(id).orElseThrow(() -> new EntityNotFoundException("Medicine not found"));
        medicines.findByCode(request.code()).ifPresent(existing -> {
            if (!existing.id.equals(id)) {
                throw new IllegalArgumentException("Medicine code already exists");
            }
        });
        medicine.code = request.code();
        medicine.name = request.name();
        medicine.genericName = request.genericName();
        medicine.manufacturer = request.manufacturer();
        medicine.category = request.category();
        medicine.hsnCode = request.hsnCode();
        medicine.gstPercentage = money(request.gstPercentage());
        medicine.mrp = money(request.mrp());
        medicine.sellingPrice = money(request.sellingPrice());
        medicine.prescriptionRequired = request.prescriptionRequired();
        medicine.stockWatchQty = request.stockWatchQty();
        medicine.sideEffects = request.sideEffects();
        Medicine saved = medicines.save(medicine);
        checkReplenishment(saved);
        logActivity("MEDICINE_UPDATED", "Updated medicine: " + saved.name + " (" + saved.code + ")", "Medicine", saved.id);
        return medicineView(saved);
    }

    public MedicineView updateOrderStatus(Long id, OrderStatusRequest request) {
        Medicine medicine = medicines.findById(id).orElseThrow(() -> new EntityNotFoundException("Medicine not found"));
        medicine.orderStatus = request.status();
        if ("Ordered".equalsIgnoreCase(request.status()) || "Received".equalsIgnoreCase(request.status())) {
            medicine.orderedDate = parseLocalDate(request.orderedDate());
            medicine.orderedQuantity = request.orderedQuantity() != null ? request.orderedQuantity() : 30;
            if (request.distributorId() != null) {
                Distributor dist = distributors.findById(request.distributorId())
                        .orElseThrow(() -> new EntityNotFoundException("Distributor not found"));
                medicine.orderedDistributorId = dist.id;
                medicine.orderedDistributorName = dist.name;
            } else {
                medicine.orderedDistributorId = null;
                medicine.orderedDistributorName = null;
            }
        } else {
            medicine.orderStatus = "Low Stock";
            medicine.orderedDate = null;
            medicine.orderedDistributorId = null;
            medicine.orderedDistributorName = null;
            medicine.orderedQuantity = null;
        }
        return medicineView(medicines.save(medicine));
    }

    public List<MedicineView> medicines() {
        return medicines.findAll().stream().sorted(Comparator.comparing(m -> m.name)).map(this::medicineView).toList();
    }

    public BatchView addBatch(BatchRequest request) {
        Medicine medicine = medicines.findById(request.medicineId()).orElseThrow(() -> new EntityNotFoundException("Medicine not found"));
        Distributor distributor = request.distributorId() == null ? null : distributors.findById(request.distributorId())
                .orElseThrow(() -> new EntityNotFoundException("Distributor not found"));

        DistributorBill bill = null;
        if (distributor != null && request.billNo() != null && !request.billNo().trim().isEmpty()) {
            String bNo = request.billNo().trim();
            bill = distributorBills.findByDistributorIdAndBillNo(distributor.id, bNo).orElseGet(() -> {
                LocalDate bDate = request.billDate() != null ? request.billDate() : LocalDate.now();
                LocalDate dDate = request.dueDate() != null ? request.dueDate() : bDate.plusDays(30);
                return distributorBills.save(new DistributorBill(distributor, bNo, bDate, dDate));
            });
            BigDecimal cost = request.purchasePrice().multiply(BigDecimal.valueOf(request.quantity()));
            BigDecimal gst = cost.multiply(medicine.gstPercentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal net = cost.add(gst);

            bill.totalAmount = money(bill.totalAmount.add(cost));
            bill.gstAmount = money(bill.gstAmount.add(gst));
            bill.netAmount = money(bill.netAmount.add(net));
            bill.dueAmount = money(bill.netAmount.subtract(bill.paidAmount));
            distributorBills.save(bill);
        }

        MedicineBatch batch = new MedicineBatch(
                medicine, request.batchNo(), request.expiryDate(), money(request.purchasePrice()),
                money(request.sellingPrice()), request.quantity(), distributor);
        batch.mrp = money(request.mrp());
        batch.distributorBill = bill;
        batches.save(batch);

        recordMovement(batch, MovementType.PURCHASE, request.quantity(), batch.id, "Batch received");
        checkReplenishment(medicine);
        return batchView(batch);
    }

    public BatchView updateBatch(Long id, BatchEditRequest request) {
        MedicineBatch batch = batches.findById(id).orElseThrow(() -> new EntityNotFoundException("Batch not found"));
        int diff = request.availableQuantity() - batch.availableQuantity;
        if (diff != 0) {
            recordMovement(batch, MovementType.ADJUSTMENT, diff, batch.id, "Manual inventory correction by admin");
        }
        batch.batchNo = request.batchNo();
        batch.expiryDate = request.expiryDate();
        batch.purchasePrice = money(request.purchasePrice());
        batch.sellingPrice = money(request.sellingPrice());
        batch.mrp = money(request.mrp());
        batch.availableQuantity = request.availableQuantity();
        if (batch.availableQuantity > batch.quantity) {
            batch.quantity = batch.availableQuantity;
        }
        MedicineBatch savedBatch = batches.save(batch);
        checkReplenishment(savedBatch.medicine);
        return batchView(savedBatch);
    }

    public DistributorView addDistributor(DistributorRequest request) {
        Distributor distributor = new Distributor(request.name(), request.contactPerson(), request.mobile(),
                request.gstNumber(), request.address(), request.upiId());
        distributor.email = request.email();
        distributor.bankName = request.bankName();
        distributor.bankAccountNo = request.bankAccountNo();
        distributor.bankIfscCode = request.bankIfscCode();
        return distributorView(distributors.save(distributor));
    }

    public DistributorView updateDistributor(Long id, DistributorRequest request) {
        Distributor distributor = distributors.findById(id).orElseThrow(() -> new EntityNotFoundException("Distributor not found"));
        distributor.name = request.name();
        distributor.contactPerson = request.contactPerson();
        distributor.mobile = request.mobile();
        distributor.email = request.email();
        distributor.gstNumber = request.gstNumber();
        distributor.address = request.address();
        distributor.upiId = request.upiId();
        distributor.bankName = request.bankName();
        distributor.bankAccountNo = request.bankAccountNo();
        distributor.bankIfscCode = request.bankIfscCode();
        return distributorView(distributor);
    }

    public List<DistributorView> distributors() {
        return distributors.findAll().stream().sorted(Comparator.comparing(d -> d.name)).map(this::distributorView).toList();
    }

    public List<DistributorBillView> distributorBills(Long distributorId) {
        return distributorBills.findByDistributorIdOrderByBillDateDesc(distributorId).stream()
                .map(this::distributorBillView).toList();
    }

    public List<DistributorBillView> allDistributorBills() {
        return distributorBills.findAll().stream()
                .sorted(Comparator.comparing((DistributorBill b) -> b.billDate).reversed())
                .map(this::distributorBillView)
                .toList();
    }

    public List<DistributorPaymentView> distributorPayments(Long distributorId) {
        return distributorPayments.findByDistributorIdOrderByPaymentDateDesc(distributorId).stream()
                .map(this::distributorPaymentView).toList();
    }

    public DistributorBillView distributorBill(Long id) {
        DistributorBill bill = distributorBills.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Distributor bill not found"));
        return distributorBillView(bill);
    }

    public DistributorBillView payDistributorBill(DistributorPaymentRequest request) {
        Distributor distributor = distributors.findById(request.distributorId())
                .orElseThrow(() -> new EntityNotFoundException("Distributor not found"));
        DistributorBill bill = request.billId() == null ? null : distributorBills.findById(request.billId())
                .orElseThrow(() -> new EntityNotFoundException("Distributor bill not found"));

        if (bill != null) {
            if (request.amount().compareTo(bill.dueAmount) > 0) {
                throw new IllegalArgumentException("Payment exceeds bill due amount");
            }
            bill.paidAmount = money(bill.paidAmount.add(request.amount()));
            bill.dueAmount = money(bill.dueAmount.subtract(request.amount()));
            if (bill.dueAmount.signum() == 0) {
                bill.status = "SETTLED";
            }
            distributorBills.save(bill);
        }

        distributorPayments.save(new DistributorPayment(
                distributor, bill, request.amount(), LocalDate.now(), request.paymentMode(), request.referenceNo()
        ));

        return bill != null ? distributorBillView(bill) : null;
    }

    @Transactional
    public void deleteDistributorBill(Long id) {
        DistributorBill bill = distributorBills.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Distributor bill not found"));

        List<DistributorPayment> paymentsList = distributorPayments.findByDistributorBillId(bill.id);
        distributorPayments.deleteAll(paymentsList);

        List<MedicineBatch> linkedBatches = batches.findByDistributorBillId(bill.id);
        for (MedicineBatch batch : linkedBatches) {
            List<StockMovement> batchMovements = movements.findByBatchId(batch.id);
            movements.deleteAll(batchMovements);

            List<SaleItem> batchSaleItems = saleItems.findByBatchId(batch.id);
            saleItems.deleteAll(batchSaleItems);

            batches.delete(batch);
        }

        distributorBills.delete(bill);
        logActivity("DELETE_DISTRIBUTOR_BILL", "Deleted distributor bill " + bill.billNo + " for distributor " + bill.distributor.name, "DISTRIBUTOR_BILL", bill.id);
    }

    public DistributorBillView uploadBulkBill(BulkBillRequest request) {
        Distributor distributor = distributors.findById(request.distributorId())
                .orElseThrow(() -> new EntityNotFoundException("Distributor not found"));

        String bNo = request.billNo() != null ? request.billNo().trim() : "";
        if (bNo.isEmpty()) {
            throw new IllegalArgumentException("Bill number is required for bulk bill upload.");
        }

        if (distributorBills.findByDistributorIdAndBillNo(distributor.id, bNo).isPresent()) {
            throw new IllegalArgumentException("Bill #" + bNo + " for distributor '" + distributor.name + "' has already been uploaded and processed. Duplicate bill uploads are not allowed.");
        }

        LocalDate bDate = request.billDate() != null ? request.billDate() : LocalDate.now();
        LocalDate dDate = request.dueDate() != null ? request.dueDate() : bDate.plusDays(30);
        DistributorBill bill = distributorBills.save(new DistributorBill(distributor, bNo, bDate, dDate));

        for (BulkBatchItemRequest item : request.items()) {
            String mfr = item.manufacturer() != null && !item.manufacturer().trim().isEmpty() ? item.manufacturer().trim() : "Unknown";
            String hsn = item.hsnCode() != null && !item.hsnCode().trim().isEmpty() ? item.hsnCode().trim() : "300490";
            String cat = item.category() != null && !item.category().trim().isEmpty() ? item.category().trim() : "General";
            String gen = item.genericName() != null && !item.genericName().trim().isEmpty() ? item.genericName().trim() : "Generic";

            Medicine medicine = medicines.findByCode(item.medicineCode()).orElseGet(() -> {
                String medName = item.medicineName() != null && !item.medicineName().trim().isEmpty()
                        ? item.medicineName().trim()
                        : "New Med " + item.medicineCode();
                Medicine newMed = new Medicine(
                    item.medicineCode(),
                    medName,
                    gen,
                    mfr,
                    cat,
                    hsn,
                    item.gstPercentage() != null ? item.gstPercentage() : BigDecimal.valueOf(12),
                    item.mrp() != null ? item.mrp() : item.sellingPrice(),
                    item.sellingPrice(),
                    false,
                    10,
                    item.sideEffects()
                );
                return medicines.save(newMed);
            });

            medicine.mrp = money(item.mrp());
            medicine.sellingPrice = money(item.sellingPrice());
            medicine.gstPercentage = money(item.gstPercentage());
            if (item.genericName() != null && !item.genericName().trim().isEmpty()) {
                medicine.genericName = item.genericName().trim();
            }
            if (item.sideEffects() != null && !item.sideEffects().trim().isEmpty()) {
                medicine.sideEffects = item.sideEffects().trim();
            }
            if (item.medicineName() != null && !item.medicineName().trim().isEmpty()) {
                medicine.name = item.medicineName().trim();
            }
            if (item.manufacturer() != null && !item.manufacturer().trim().isEmpty() && (!"Unknown".equalsIgnoreCase(medicine.manufacturer) || !"Unknown".equalsIgnoreCase(item.manufacturer().trim()))) {
                medicine.manufacturer = item.manufacturer().trim();
            }
            if (item.hsnCode() != null && !item.hsnCode().trim().isEmpty() && (!"300490".equals(medicine.hsnCode) || !"300490".equals(item.hsnCode().trim()))) {
                medicine.hsnCode = item.hsnCode().trim();
            }
            if (item.category() != null && !item.category().trim().isEmpty() && (!"General".equalsIgnoreCase(medicine.category) || !"General".equalsIgnoreCase(item.category().trim()))) {
                medicine.category = item.category().trim();
            }
            medicines.save(medicine);

            BigDecimal discountRate = item.discountPercentage() != null ? item.discountPercentage() : BigDecimal.ZERO;
            BigDecimal multiplier = BigDecimal.ONE.subtract(discountRate.divide(HUNDRED, 4, RoundingMode.HALF_UP));
            BigDecimal unitBaseCost = item.purchasePrice().multiply(multiplier);

            BigDecimal cost = unitBaseCost.multiply(BigDecimal.valueOf(item.quantity()));
            BigDecimal gst = cost.multiply(medicine.gstPercentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal net = cost.add(gst);

            bill.totalAmount = money(bill.totalAmount.add(cost));
            bill.gstAmount = money(bill.gstAmount.add(gst));
            bill.netAmount = money(bill.netAmount.add(net));
            bill.dueAmount = money(bill.netAmount.subtract(bill.paidAmount));

            int freeQty = item.free() != null ? item.free() : 0;
            int totalQuantity = item.quantity() + freeQty;

            MedicineBatch batch = new MedicineBatch(
                    medicine, item.batchNo(), item.expiryDate(), money(item.purchasePrice()),
                    money(item.sellingPrice()), totalQuantity, distributor);
            batch.mrp = money(item.mrp());
            batch.free = freeQty;
            batch.discountPercentage = item.discountPercentage();
            batch.distributorBill = bill;
            batches.save(batch);

            recordMovement(batch, MovementType.PURCHASE, totalQuantity, batch.id, "Batch received via bulk bill upload");
            checkReplenishment(medicine);
        }

        distributorBills.save(bill);
        return distributorBillView(bill);
    }

    private DistributorBillView distributorBillView(DistributorBill bill) {
        List<BatchView> items = batches.findAll().stream()
                .filter(b -> b.distributorBill != null && b.distributorBill.id.equals(bill.id))
                .map(this::batchView)
                .toList();
        return new DistributorBillView(bill.id, bill.distributor.name, bill.billNo, bill.billDate, bill.dueDate,
                bill.totalAmount, bill.gstAmount, bill.netAmount, bill.paidAmount, bill.dueAmount,
                bill.status, items);
    }

    private DistributorPaymentView distributorPaymentView(DistributorPayment payment) {
        Instant dateOrCreatedAt = payment.createdAt != null ? payment.createdAt : (payment.paymentDate != null ? payment.paymentDate.atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.now());
        return new DistributorPaymentView(payment.id,
                payment.distributorBill != null ? payment.distributorBill.billNo : "General Payment",
                payment.amount, dateOrCreatedAt, payment.paymentMode, payment.referenceNo);
    }

    public CustomerView addCustomer(CustomerRequest request) {
        String trimmedName = request.name().trim();
        Optional<Customer> existing = customers.findByNameIgnoreCase(trimmedName);
        if (existing.isPresent()) {
            Customer customer = existing.get();
            if (request.mobile() != null && !request.mobile().trim().isEmpty()) {
                customer.mobile = request.mobile().trim();
            }
            if (request.address() != null && !request.address().trim().isEmpty()) {
                customer.address = request.address().trim();
            }
            if (request.creditLimit() != null) {
                customer.creditLimit = money(request.creditLimit());
            }
            return customerView(customers.save(customer));
        }
        return customerView(customers.save(new Customer(
            trimmedName,
            request.mobile() != null ? request.mobile().trim() : null,
            request.address() != null ? request.address().trim() : null,
            money(request.creditLimit())
        )));
    }

    public CustomerView updateCustomer(Long id, CustomerRequest request) {
        Customer customer = customers.findById(id).orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        customer.name = request.name().trim();
        customer.mobile = request.mobile() != null ? request.mobile().trim() : null;
        customer.address = request.address() != null ? request.address().trim() : null;
        customer.creditLimit = money(request.creditLimit());
        return customerView(customers.save(customer));
    }

    public List<CustomerView> customers() {
        return customers.findAll().stream().sorted(Comparator.comparing(c -> c.name)).map(this::customerView).toList();
    }

    public SaleView createSale(SaleRequest request, String username) {
        UserAccount creator = users.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        Customer customer = request.customerId() == null ? null : customers.findById(request.customerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        if (request.paymentMode() == PaymentMode.CREDIT && customer == null) {
            throw new IllegalArgumentException("Credit billing requires a customer");
        }

        Sale sale = new Sale();
        sale.billNo = "RX-" + LocalDate.now().toString().replace("-", "") + "-" + String.format("%04d", sales.count() + 1);
        sale.customer = customer;
        sale.customerAge = request.customerAge();
        sale.doctorName = request.doctorName();
        sale.totalAmount = BigDecimal.ZERO.setScale(2);
        sale.discountAmount = money(request.discountAmount());
        sale.gstAmount = BigDecimal.ZERO.setScale(2);
        sale.roundingAmount = BigDecimal.ZERO.setScale(2);
        sale.netAmount = BigDecimal.ZERO.setScale(2);
        sale.paymentMode = request.paymentMode();
        sale.paymentStatus = request.paymentMode() == PaymentMode.CREDIT ? PaymentStatus.DUE : PaymentStatus.PAID;
        sale.createdBy = creator;
        sales.save(sale);

        List<SaleItem> allocated = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        Set<Medicine> uniqueMedicines = new HashSet<>();
        for (SaleLineRequest line : request.items()) {
            Medicine medicine = medicines.findById(line.medicineId()).orElseThrow(() -> new EntityNotFoundException("Medicine not found"));
            uniqueMedicines.add(medicine);
            // quantity = total individual units sold (e.g. 4 for 4 tablets, 16 for 1 strip + 6 tabs)
            // packSize = units per strip (e.g. 10 for "10 Tabs/Strip")
            int effectivePackSize = line.packSize() > 0 ? line.packSize() : 1;
            int remainingUnits = line.quantity().intValue(); // remaining individual units to allocate

            List<MedicineBatch> candidateBatches = new ArrayList<>();
            if (line.batchId() != null) {
                batches.findById(line.batchId()).ifPresent(candidateBatches::add);
            } else if (line.batchNo() != null && !line.batchNo().trim().isEmpty()) {
                candidateBatches.addAll(batches.findByMedicineIdAndBatchNo(medicine.id, line.batchNo().trim()));
            }
            for (MedicineBatch b : batches.findSellableBatches(medicine.id)) {
                if (candidateBatches.stream().noneMatch(existing -> existing.id.equals(b.id))) {
                    candidateBatches.add(b);
                }
            }

            for (MedicineBatch batch : candidateBatches) {
                if (batch.expiryDate.isBefore(LocalDate.now())) {
                    continue;
                }
                // Total units available = whole strips × packSize + loose units from previously opened strip
                int totalAvailUnits = (batch.availableQuantity * effectivePackSize) + batch.looseUnitsAvailable;
                if (totalAvailUnits <= 0) {
                    continue;
                }
                int soldUnits = Math.min(remainingUnits, totalAvailUnits);
                if (soldUnits <= 0) {
                    continue;
                }

                // Deduct from loose units first, then from whole strips
                int afterLoose = batch.looseUnitsAvailable - soldUnits;
                if (afterLoose >= 0) {
                    // All sold from existing loose units — no strip opened
                    batch.looseUnitsAvailable = afterLoose;
                } else {
                    // Need to open more strips to cover the deficit
                    int unitsNeededFromStrips = -afterLoose; // units still needed after consuming all loose
                    int stripsToOpen = (int) Math.ceil((double) unitsNeededFromStrips / effectivePackSize);
                    int newLoose = (stripsToOpen * effectivePackSize) - unitsNeededFromStrips;
                    batch.availableQuantity = Math.max(0, batch.availableQuantity - stripsToOpen);
                    batch.looseUnitsAvailable = newLoose;
                }

                // soldPacks = fractional packs for billing math (e.g. 0.4 for 4 units of 10-pack)
                BigDecimal soldPacks = BigDecimal.valueOf(soldUnits).divide(BigDecimal.valueOf(effectivePackSize), 4, RoundingMode.HALF_UP);

                SaleItem item = new SaleItem();
                item.sale = sale;
                item.batch = batch;
                item.quantity = soldPacks; // stored as fractional packs for billing math
                item.mrp = batch.mrp != null ? batch.mrp : medicine.mrp;
                item.sellingPrice = batch.sellingPrice;
                item.gstPercentage = medicine.gstPercentage;
                item.totalAmount = money(batch.sellingPrice.multiply(soldPacks));
                allocated.add(item);
                subtotal = subtotal.add(item.totalAmount);
                remainingUnits -= soldUnits;
                recordMovement(batch, MovementType.SALE, -soldUnits, sale.id, "POS sale (" + soldUnits + " units)");
                if (remainingUnits <= 0) {
                    break;
                }
            }
            if (remainingUnits > 0) {
                throw new IllegalArgumentException("Insufficient non-expired stock for " + medicine.name);
            }
        }
        if (sale.discountAmount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed subtotal");
        }

        BigDecimal netBeforeRounding = subtotal.subtract(sale.discountAmount);
        sale.netAmount = netBeforeRounding.setScale(0, RoundingMode.HALF_UP).setScale(2);
        sale.roundingAmount = money(sale.netAmount.subtract(netBeforeRounding));

        BigDecimal totalGst = BigDecimal.ZERO;
        if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = netBeforeRounding.divide(subtotal, 8, RoundingMode.HALF_UP);
            for (SaleItem item : allocated) {
                BigDecimal itemNet = item.totalAmount.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
                BigDecimal divisor = BigDecimal.ONE.add(item.gstPercentage.divide(HUNDRED, 4, RoundingMode.HALF_UP));
                BigDecimal itemTaxable = itemNet.divide(divisor, 2, RoundingMode.HALF_UP);
                BigDecimal itemGst = itemNet.setScale(2, RoundingMode.HALF_UP).subtract(itemTaxable);
                totalGst = totalGst.add(itemGst);
            }
        }
        sale.gstAmount = money(totalGst);
        sale.totalAmount = money(netBeforeRounding.subtract(sale.gstAmount));

        if (request.paymentMode() == PaymentMode.CREDIT) {
            BigDecimal outstanding = credits.outstandingForCustomer(customer.id);
            if (outstanding.add(sale.netAmount).compareTo(customer.creditLimit) > 0) {
                throw new IllegalArgumentException("Customer credit limit would be exceeded");
            }
            CreditTransaction credit = new CreditTransaction();
            credit.customer = customer;
            credit.sale = sale;
            credit.creditAmount = sale.netAmount;
            credit.paidAmount = BigDecimal.ZERO.setScale(2);
            credit.dueAmount = sale.netAmount;
            credit.dueDate = request.creditDueDate() == null ? LocalDate.now().plusDays(30) : request.creditDueDate();
            credit.status = CreditStatus.OPEN;
            credits.save(credit);
        }
        saleItems.saveAll(allocated);
        for (Medicine m : uniqueMedicines) {
            checkReplenishment(m);
        }
        logActivity("SALE_CREATED", sale.billNo + " | Net: " + sale.netAmount + " | " + sale.paymentMode, "Sale", sale.id);
        return saleView(sale, allocated);
    }

    public List<SaleView> sales() {
        List<Sale> allSales = sales.findAll();
        List<SaleItem> allItems = saleItems.findAll();
        return allSales.stream()
                .sorted(Comparator.comparing((Sale s) -> s.createdAt).reversed())
                .map(s -> saleView(s, allItems.stream().filter(item -> item.sale.id.equals(s.id)).toList()))
                .toList();
    }

    public SaleView sale(Long id) {
        Sale sale = sales.findById(id).orElseThrow(() -> new EntityNotFoundException("Sale not found"));
        return saleView(sale, saleItems.findAll().stream().filter(item -> item.sale.id.equals(id)).toList());
    }

    public SaleView saleByBillNo(String billNo) {
        Sale sale = sales.findByBillNo(billNo).orElseThrow(() -> new EntityNotFoundException("Sale not found"));
        return saleView(sale, saleItems.findAll().stream().filter(item -> item.sale.id.equals(sale.id)).toList());
    }

    public List<CreditView> customerCredits(Long customerId) {
        List<Sale> customerSales;
        List<CreditTransaction> creditTxns;
        if (customerId == 0) {
            customerSales = sales.findByCustomerIsNullOrderByCreatedAtDesc();
            creditTxns = List.of();
        } else {
            customerSales = sales.findByCustomerIdOrderByCreatedAtDesc(customerId);
            creditTxns = credits.findByCustomerIdOrderByCreatedAtDesc(customerId);
        }

        List<CreditView> result = new ArrayList<>();
        for (Sale sale : customerSales) {
            Optional<CreditTransaction> creditOpt = creditTxns.stream()
                    .filter(c -> c.sale.id.equals(sale.id))
                    .findFirst();
            if (creditOpt.isPresent()) {
                result.add(creditView(creditOpt.get()));
            } else {
                LocalDate billDate = LocalDate.ofInstant(sale.createdAt, ZoneId.systemDefault());
                List<CustomerPaymentView> payments = List.of(
                    new CustomerPaymentView(
                        -sale.id,
                        sale.netAmount,
                        sale.createdAt != null ? sale.createdAt : Instant.now(),
                        sale.paymentMode.name(),
                        "Upfront payment"
                    )
                );
                result.add(new CreditView(
                    -sale.id,
                    customerId,
                    sale.customer != null ? sale.customer.name : "Walk-in",
                    sale.billNo,
                    billDate,
                    sale.netAmount,
                    sale.netAmount,
                    BigDecimal.ZERO.setScale(2),
                    billDate,
                    CreditStatus.SETTLED,
                    payments
                ));
            }
        }
        return result;
    }

    public List<CreditView> openCredits() {
        return credits.findByStatusOrderByDueDateAsc(CreditStatus.OPEN).stream().map(this::creditView).toList();
    }

    public List<CreditView> settledCredits() {
        return credits.findByStatusOrderByCreatedAtDesc(CreditStatus.SETTLED).stream().map(this::creditView).toList();
    }

    public List<CreditView> allCredits() {
        return credits.findAllByOrderByCreatedAtDesc().stream().map(this::creditView).toList();
    }

    public CreditView payCredit(CreditPaymentRequest request) {
        CreditTransaction credit = credits.findById(request.creditId()).orElseThrow(() -> new EntityNotFoundException("Credit not found"));
        if (credit.status == CreditStatus.SETTLED) {
            throw new IllegalArgumentException("Credit is already settled");
        }
        if (request.amount().compareTo(credit.dueAmount) > 0) {
            throw new IllegalArgumentException("Payment exceeds due amount");
        }
        credit.paidAmount = money(credit.paidAmount.add(request.amount()));
        credit.dueAmount = money(credit.dueAmount.subtract(request.amount()));
        if (credit.dueAmount.signum() == 0) {
            credit.status = CreditStatus.SETTLED;
            credit.sale.paymentStatus = PaymentStatus.PAID;
        }
        credits.save(credit);

        CustomerPayment payment = new CustomerPayment(
                credit.customer,
                credit,
                request.amount(),
                LocalDate.now(ZoneId.systemDefault()),
                request.paymentMode() != null ? request.paymentMode() : "CASH",
                request.referenceNo()
        );
        customerPayments.save(payment);

        logActivity("CREDIT_COLLECTED", "Collected ₹" + request.amount() + " against " + credit.sale.billNo, "Credit", credit.id);
        return creditView(credit);
    }

    public int seedTestCreditCustomers() {
        record TestBillData(String billNo, BigDecimal amount, int daysAgo, int dueDaysInFuture, BigDecimal paidAmount) {}
        record TestCustomerData(String name, String mobile, String address, BigDecimal creditLimit, List<TestBillData> bills) {}

        List<TestCustomerData> testList = List.of(
            new TestCustomerData("Asha Kulkarni", "9988776655", "Baner, Pune", new BigDecimal("5000"),
                List.of(
                    new TestBillData("RX-PREV-001", new BigDecimal("560.00"), 10, 16, BigDecimal.ZERO),
                    new TestBillData("RX-20260817-0063", new BigDecimal("130.00"), 2, 30, BigDecimal.ZERO)
                )),
            new TestCustomerData("Vikram Rathore", "7766554433", "Aundh, Pune", new BigDecimal("8000"),
                List.of(
                    new TestBillData("RX-YEST-001", new BigDecimal("1080.00"), 5, 20, new BigDecimal("400.00"))
                )),
            new TestCustomerData("Rajesh Sharma", "9822114455", "Shivajinagar, Pune", new BigDecimal("6000"),
                List.of(
                    new TestBillData("RX-20260810-0012", new BigDecimal("840.00"), 8, 14, BigDecimal.ZERO),
                    new TestBillData("RX-20260815-0044", new BigDecimal("320.00"), 3, 27, BigDecimal.ZERO)
                )),
            new TestCustomerData("Pooja Deshmukh", "9850123456", "Kothrud, Pune", new BigDecimal("4500"),
                List.of(
                    new TestBillData("RX-20260812-0028", new BigDecimal("950.00"), 6, 24, BigDecimal.ZERO)
                )),
            new TestCustomerData("Amitabh Verma", "9765432190", "Viman Nagar, Pune", new BigDecimal("7000"),
                List.of(
                    new TestBillData("RX-20260805-0008", new BigDecimal("1120.00"), 13, 17, new BigDecimal("200.00")),
                    new TestBillData("RX-20260816-0051", new BigDecimal("480.00"), 2, 28, BigDecimal.ZERO)
                )),
            new TestCustomerData("Sneha Joshi", "9890112233", "Hadapsar, Pune", new BigDecimal("4000"),
                List.of(
                    new TestBillData("RX-20260814-0035", new BigDecimal("675.00"), 4, 26, BigDecimal.ZERO)
                )),
            new TestCustomerData("Ramesh Gupta", "9823456789", "Pimpri, Pune", new BigDecimal("5500"),
                List.of(
                    new TestBillData("RX-20260808-0019", new BigDecimal("760.00"), 10, 20, BigDecimal.ZERO),
                    new TestBillData("RX-20260817-0059", new BigDecimal("540.00"), 1, 29, BigDecimal.ZERO)
                )),
            new TestCustomerData("Ananya Iyer", "9811223344", "Wakad, Pune", new BigDecimal("5000"),
                List.of(
                    new TestBillData("RX-20260811-0022", new BigDecimal("920.00"), 7, 23, BigDecimal.ZERO)
                )),
            new TestCustomerData("Kiran Gaikwad", "9730011223", "Kalyani Nagar, Pune", new BigDecimal("3500"),
                List.of(
                    new TestBillData("RX-20260813-0031", new BigDecimal("380.00"), 5, 25, BigDecimal.ZERO)
                )),
            new TestCustomerData("Sunil Deshpande", "9860778899", "Model Colony, Pune", new BigDecimal("8000"),
                List.of(
                    new TestBillData("RX-20260806-0015", new BigDecimal("1450.00"), 12, 18, new BigDecimal("450.00")),
                    new TestBillData("RX-20260816-0058", new BigDecimal("690.00"), 2, 28, BigDecimal.ZERO)
                ))
        );

        UserAccount adminUser = users.findByUsernameIgnoreCase("admin").orElse(null);
        List<MedicineBatch> availableBatches = batches.findAll();
        MedicineBatch defaultBatch = availableBatches.isEmpty() ? null : availableBatches.get(0);

        int count = 0;
        for (TestCustomerData item : testList) {
            Customer customer = customers.findByMobile(item.mobile()).orElse(null);
            if (customer == null) {
                customer = customers.save(new Customer(item.name(), item.mobile(), item.address(), item.creditLimit()));
            }

            for (TestBillData billData : item.bills()) {
                if (sales.findByBillNo(billData.billNo()).isPresent()) {
                    continue;
                }

                Sale sale = new Sale();
                sale.billNo = billData.billNo();
                sale.customer = customer;
                sale.customerAge = "35";
                sale.doctorName = "Dr. S. Kulkarni";
                sale.totalAmount = money(billData.amount().multiply(new BigDecimal("0.88")));
                sale.discountAmount = BigDecimal.ZERO.setScale(2);
                sale.gstAmount = money(billData.amount().multiply(new BigDecimal("0.12")));
                sale.roundingAmount = BigDecimal.ZERO.setScale(2);
                sale.netAmount = money(billData.amount());
                sale.paymentMode = PaymentMode.CREDIT;
                sale.paymentStatus = PaymentStatus.DUE;
                sale.createdBy = adminUser;
                sale.createdAt = Instant.now().minus(billData.daysAgo(), java.time.temporal.ChronoUnit.DAYS);
                sales.save(sale);

                if (defaultBatch != null) {
                    SaleItem si = new SaleItem();
                    si.sale = sale;
                    si.batch = defaultBatch;
                    si.quantity = BigDecimal.ONE;
                    si.mrp = billData.amount();
                    si.sellingPrice = billData.amount();
                    si.gstPercentage = new BigDecimal("12");
                    si.totalAmount = billData.amount();
                    saleItems.save(si);
                }

                CreditTransaction ct = new CreditTransaction();
                ct.customer = customer;
                ct.sale = sale;
                ct.creditAmount = sale.netAmount;
                ct.paidAmount = money(billData.paidAmount());
                ct.dueAmount = money(sale.netAmount.subtract(ct.paidAmount));
                ct.dueDate = LocalDate.now().plusDays(billData.dueDaysInFuture());
                ct.status = ct.dueAmount.compareTo(BigDecimal.ZERO) <= 0 ? CreditStatus.SETTLED : CreditStatus.OPEN;
                ct.createdAt = sale.createdAt;
                credits.save(ct);

                if (ct.paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                    CustomerPayment cp = new CustomerPayment(
                        customer, ct, ct.paidAmount, LocalDate.now().minusDays(Math.max(1, billData.daysAgo() / 2)), "UPI", "TXN-INIT-" + billData.billNo()
                    );
                    customerPayments.save(cp);
                }

                count++;
            }
        }
        return count;
    }

    public DashboardView dashboard() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1);
        LocalDate monthStartDate = today.withDayOfMonth(1);
        Instant monthStart = monthStartDate.atStartOfDay(zone).toInstant();

        long lowStock = batches.countByAvailableQuantityLessThanEqual(10);
        long expiring = batches.countByExpiryDateBetween(today, today.plusDays(90));
        long todayBills = sales.countByCreatedAtBetween(dayStart, dayEnd);

        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new DashboardView(
                    zero, zero, lowStock, expiring, zero, todayBills,
                    zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero
            );
        }

        BigDecimal salesToday = money(sales.revenueBetween(dayStart, dayEnd));
        BigDecimal salesMonth = money(sales.revenueBetween(monthStart, dayEnd));
        BigDecimal customerDues = money(credits.totalOutstanding());

        BigDecimal customerCreditsMonth = money(credits.creditAmountBetween(monthStart, dayEnd));
        BigDecimal paymentsToDistributorsMonth = money(distributorPayments.paymentsToDistributorsBetween(monthStartDate, today));
        BigDecimal purchasesMonth = money(distributorBills.purchasesBetween(monthStartDate, today));
        BigDecimal distributorDues = money(distributorBills.totalDueForDistributors());
        BigDecimal expiredCost = money(batches.expiredCost(today));

        BigDecimal wages = money(expenses.sumByCategoryAndBetween("WAGES", monthStartDate, today));
        BigDecimal bills = money(expenses.sumByCategoryAndBetween("UTILITIES", monthStartDate, today));
        BigDecimal maintenance = money(expenses.sumByCategoryAndBetween("MAINTENANCE", monthStartDate, today));
        BigDecimal misc = money(expenses.sumByCategoryAndBetween("OTHER", monthStartDate, today));

        BigDecimal totalExpenses = wages.add(bills).add(maintenance).add(misc).add(paymentsToDistributorsMonth);

        return new DashboardView(
                salesToday, salesMonth, lowStock, expiring, customerDues, todayBills,
                customerCreditsMonth, paymentsToDistributorsMonth, customerDues, purchasesMonth, distributorDues,
                expiredCost, totalExpenses, wages, bills, maintenance, misc
        );
    }

    private MedicineView medicineView(Medicine medicine) {
        List<BatchView> medicineBatches = batches.findAll().stream()
                .filter(batch -> batch.medicine.id.equals(medicine.id))
                .sorted(Comparator.comparing(batch -> batch.expiryDate))
                .map(this::batchView)
                .toList();
        return new MedicineView(medicine.id, medicine.code, medicine.name, medicine.genericName, medicine.manufacturer,
                medicine.category, medicine.hsnCode, medicine.gstPercentage, medicine.mrp, medicine.sellingPrice,
                medicine.prescriptionRequired, medicine.stockWatchQty, batches.availableForMedicine(medicine.id), medicineBatches,
                medicine.orderStatus != null ? medicine.orderStatus : "Low Stock", medicine.orderedDate, medicine.orderedDistributorId, medicine.orderedDistributorName,
                medicine.orderedQuantity, medicine.sideEffects);
    }

    private BatchView batchView(MedicineBatch batch) {
        return new BatchView(batch.id, batch.batchNo, batch.expiryDate, batch.purchasePrice, batch.sellingPrice,
                batch.availableQuantity, batch.quantity, batch.medicine != null && batch.medicine.gstPercentage != null ? batch.medicine.gstPercentage : BigDecimal.valueOf(12),
                batch.mrp != null ? batch.mrp : (batch.medicine != null ? batch.medicine.mrp : BigDecimal.ZERO),
                batch.distributor == null ? "Direct purchase" : batch.distributor.name,
                batch.distributorBill == null ? null : batch.distributorBill.billNo,
                batch.distributorBill == null ? null : batch.distributorBill.billDate,
                batch.distributorBill == null ? null : batch.distributorBill.dueDate,
                batch.free, batch.discountPercentage,
                batch.medicine != null ? batch.medicine.id : null,
                batch.medicine != null ? batch.medicine.code : null,
                batch.medicine != null ? batch.medicine.name : null,
                batch.medicine != null ? batch.medicine.manufacturer : "Unknown",
                batch.medicine != null ? batch.medicine.category : "General",
                batch.medicine != null ? batch.medicine.hsnCode : "300490",
                batch.looseUnitsAvailable);
    }

    private DistributorView distributorView(Distributor distributor) {
        return new DistributorView(distributor.id, distributor.name, distributor.contactPerson, distributor.mobile,
                distributor.email, distributor.gstNumber, distributor.address, distributor.upiId,
                distributor.bankName, distributor.bankAccountNo, distributor.bankIfscCode);
    }

    private CustomerView customerView(Customer customer) {
        return new CustomerView(customer.id, customer.name, customer.mobile, customer.address,
                customer.creditLimit, money(credits.outstandingForCustomer(customer.id)));
    }

    private void checkReplenishment(Medicine medicine) {
        long available = batches.availableForMedicine(medicine.id);
        if (available > medicine.stockWatchQty) {
            medicine.orderStatus = "Low Stock";
            medicine.orderedDate = null;
            medicine.orderedDistributorId = null;
            medicine.orderedDistributorName = null;
            medicine.orderedQuantity = null;
            medicines.save(medicine);
        }
    }

    private SaleView saleView(Sale sale, List<SaleItem> items) {
        BigDecimal purchaseBase = BigDecimal.ZERO;
        BigDecimal inputGst = BigDecimal.ZERO;

        for (SaleItem item : items) {
            BigDecimal discountedPrice = item.batch.purchasePrice;
            if (item.batch.discountPercentage != null && item.batch.discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal multiplier = BigDecimal.ONE.subtract(item.batch.discountPercentage.divide(HUNDRED, 4, RoundingMode.HALF_UP));
                discountedPrice = discountedPrice.multiply(multiplier);
            }
            int totalQty = item.batch.quantity;
            int freeQty = item.batch.free != null ? item.batch.free : 0;
            int paidQty = totalQty - freeQty;
            if (totalQty > 0 && paidQty > 0 && freeQty > 0) {
                discountedPrice = discountedPrice.multiply(BigDecimal.valueOf(paidQty))
                        .divide(BigDecimal.valueOf(totalQty), 4, RoundingMode.HALF_UP);
            }
            BigDecimal itemPurchaseBase = discountedPrice.multiply(item.quantity);
            BigDecimal itemInputGst = itemPurchaseBase.multiply(item.batch.medicine.gstPercentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            purchaseBase = purchaseBase.add(itemPurchaseBase);
            inputGst = inputGst.add(itemInputGst);
        }

        BigDecimal profit = sale.totalAmount.subtract(purchaseBase);
        BigDecimal gstPayable = sale.gstAmount.subtract(inputGst);

        BigDecimal subtotal = sale.totalAmount.add(sale.gstAmount).add(sale.discountAmount);
        BigDecimal discount = sale.discountAmount;

        BigDecimal discountRatio = BigDecimal.ZERO;
        if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
            discountRatio = discount.divide(subtotal, 8, RoundingMode.HALF_UP);
        }

        final BigDecimal finalRatio = BigDecimal.ONE.subtract(discountRatio);

        List<SaleLineView> lineViews = items.stream().map(item -> {
            BigDecimal itemNet = item.totalAmount.multiply(finalRatio).setScale(4, RoundingMode.HALF_UP);
            BigDecimal divisor = BigDecimal.ONE.add(item.gstPercentage.divide(HUNDRED, 4, RoundingMode.HALF_UP));
            BigDecimal itemTaxable = itemNet.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal itemGst = itemNet.setScale(2, RoundingMode.HALF_UP).subtract(itemTaxable);

            return new SaleLineView(
                item.batch.medicine.name,
                item.batch.medicine.manufacturer,
                item.batch.batchNo,
                item.batch.expiryDate,
                item.quantity,
                item.sellingPrice,
                item.gstPercentage,
                item.totalAmount,
                itemGst,
                item.mrp != null ? item.mrp : BigDecimal.ZERO,
                item.mrp != null ? item.mrp.subtract(item.sellingPrice) : BigDecimal.ZERO,
                item.batch.medicine.category
            );
        }).toList();

        return new SaleView(
            sale.id,
            sale.billNo,
            sale.customer == null ? "Walk-in" : sale.customer.name,
            sale.customer == null ? null : sale.customer.mobile,
            sale.customer == null ? null : sale.customer.address,
            sale.customerAge,
            sale.doctorName,
            sale.totalAmount,
            sale.discountAmount,
            sale.gstAmount,
            sale.roundingAmount,
            sale.netAmount,
            sale.paymentMode,
            sale.paymentStatus,
            sale.createdAt,
            lineViews,
            money(purchaseBase),
            money(profit),
            money(inputGst),
            money(gstPayable)
        );
    }

    private CreditView creditView(CreditTransaction credit) {
        List<CustomerPaymentView> paymentsList = customerPayments.findByCreditTransactionIdOrderByPaymentDateDesc(credit.id).stream()
                .map(p -> {
                    Instant dateOrCreatedAt = p.createdAt != null ? p.createdAt : (p.paymentDate != null ? p.paymentDate.atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.now());
                    return new CustomerPaymentView(p.id, p.amount, dateOrCreatedAt, p.paymentMode, p.referenceNo);
                })
                .toList();
        LocalDate billDate = LocalDate.ofInstant(credit.sale.createdAt, ZoneId.systemDefault());
        return new CreditView(credit.id, credit.customer.id, credit.customer.name, credit.sale.billNo,
                billDate, credit.creditAmount, credit.paidAmount, credit.dueAmount, credit.dueDate, credit.status, paymentsList);
    }

    private void recordMovement(MedicineBatch batch, MovementType type, int quantity, Long referenceId, String remarks) {
        StockMovement movement = new StockMovement();
        movement.batch = batch;
        movement.movementType = type;
        movement.quantity = quantity;
        movement.referenceId = referenceId;
        movement.remarks = remarks;
        movements.save(movement);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.now();
        }
        String trimmed = dateStr.trim();
        List<java.time.format.DateTimeFormatter> formatters = List.of(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            java.time.format.DateTimeFormatter.ofPattern("d-M-yyyy")
        );
        for (java.time.format.DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (java.time.format.DateTimeParseException e) {
                // Try next
            }
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (java.time.format.DateTimeParseException e) {
            return LocalDate.now();
        }
    }

    public AnalyticsView analytics(String startDateStr, String endDateStr, int days) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate startDate;
        LocalDate endDate = today;

        if (startDateStr != null && !startDateStr.trim().isEmpty() && !startDateStr.equals("all")) {
            try {
                startDate = LocalDate.parse(startDateStr);
            } catch (Exception e) {
                startDate = today.minusDays(days);
            }
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                try {
                    endDate = LocalDate.parse(endDateStr);
                } catch (Exception e) {
                    endDate = today;
                }
            }
        } else if ("all".equals(startDateStr) || (startDateStr != null && startDateStr.trim().isEmpty())) {
            Optional<Sale> firstSale = sales.findFirstByOrderByCreatedAtAsc();
            startDate = firstSale.map(s -> s.createdAt.atZone(zone).toLocalDate()).orElse(today);
        } else {
            startDate = today.minusDays(days);
        }

        if (startDate.isAfter(endDate)) {
            startDate = endDate;
        }

        Instant from = startDate.atStartOfDay(zone).toInstant();
        Instant to = endDate.plusDays(1).atStartOfDay(zone).toInstant();

        List<Sale> salesInRange = sales.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        Map<LocalDate, List<Sale>> grouped = salesInRange.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        s -> s.createdAt.atZone(zone).toLocalDate()));
        List<DailySalesPoint> dailySales = new java.util.ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            List<Sale> daySales = grouped.getOrDefault(d, List.of());
            BigDecimal dayRevenue = daySales.stream()
                    .map(s -> s.netAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dailySales.add(new DailySalesPoint(d, money(dayRevenue), daySales.size()));
        }

        List<Object[]> topRaw = saleItems.topSellingMedicines(from, to);
        List<TopMedicineView> topMedicines = topRaw.stream()
                .limit(10)
                .map(row -> new TopMedicineView(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        money((BigDecimal) row[2])))
                .toList();

        List<Object[]> catRaw = saleItems.categoryWiseRevenue(from, to);
        List<CategoryRevenueView> categoryRevenue = catRaw.stream()
                .map(row -> new CategoryRevenueView(
                        row[0] != null ? (String) row[0] : "Uncategorized",
                        money((BigDecimal) row[1])))
                .toList();

        java.util.Set<String> allMedNames = medicines.findAll().stream()
                .map(m -> m.name)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, TopMedicineView> soldMap = new java.util.HashMap<>();
        for (Object[] row : topRaw) {
            String name = (String) row[0];
            long qty = ((Number) row[1]).longValue();
            BigDecimal rev = money((BigDecimal) row[2]);
            soldMap.put(name, new TopMedicineView(name, qty, rev));
        }
        for (String name : allMedNames) {
            soldMap.putIfAbsent(name, new TopMedicineView(name, 0L, BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP)));
        }
        List<TopMedicineView> slowMedicines = soldMap.values().stream()
                .sorted(java.util.Comparator.comparingLong((TopMedicineView m) -> m.totalQuantity())
                        .thenComparing(TopMedicineView::name))
                .limit(10)
                .toList();

        java.util.Map<String, BigDecimal> paymentMap = new java.util.HashMap<>();
        for (PaymentMode pm : PaymentMode.values()) {
            paymentMap.put(pm.name(), BigDecimal.ZERO);
        }
        for (Sale s : salesInRange) {
            if (s.paymentMode != null) {
                paymentMap.put(s.paymentMode.name(), paymentMap.get(s.paymentMode.name()).add(s.netAmount));
            }
        }
        List<PaymentModeShare> paymentModeShare = paymentMap.entrySet().stream()
                .map(e -> new PaymentModeShare(e.getKey(), money(e.getValue())))
                .sorted(java.util.Comparator.comparing(PaymentModeShare::mode))
                .toList();

        return new AnalyticsView(dailySales, topMedicines, categoryRevenue, slowMedicines, paymentModeShare);
    }

    public List<DistributorPriceComparison> distributorComparison() {
        List<Medicine> allMeds = medicines.findAll();
        List<DistributorPriceComparison> result = new java.util.ArrayList<>();
        for (Medicine med : allMeds) {
            List<MedicineBatch> medBatches = batches.findAll().stream()
                    .filter(b -> b.medicine.id.equals(med.id) && b.distributor != null)
                    .toList();
            if (medBatches.isEmpty()) continue;
            List<DistributorPriceEntry> entries = medBatches.stream()
                    .map(b -> {
                        String bNo = b.distributorBill != null ? b.distributorBill.billNo : null;
                        LocalDate bDate = b.distributorBill != null ? b.distributorBill.billDate : (b.createdAt != null ? LocalDate.ofInstant(b.createdAt, ZoneId.systemDefault()) : null);
                        return new DistributorPriceEntry(
                            b.distributor.name,
                            b.purchasePrice,
                            b.sellingPrice,
                            b.mrp != null ? b.mrp : med.mrp,
                            b.batchNo,
                            b.expiryDate,
                            b.availableQuantity,
                            bNo,
                            bDate);
                    })
                    .toList();
            result.add(new DistributorPriceComparison(med.id, med.name, med.genericName, entries));
        }
        return result;
    }

    public List<ActivityLogView> getActivityLogs() {
        return activityLogs.findTop200ByOrderByCreatedAtDesc().stream()
                .map(log -> new ActivityLogView(log.id, log.action, log.performedBy,
                        log.details, log.entityType, log.entityId, log.createdAt))
                .toList();
    }

    private void logActivity(String action, String details, String entityType, Long entityId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        activityLogs.save(new ActivityLog(action, username, details, entityType, entityId));
    }

    public StoreConfig getStoreConfigEntity() {
        return configRepository.findById(1L).orElseGet(() -> {
            StoreConfig newConfig = new StoreConfig(
                "Sri Lakshmi Medical And Fancy Stores",
                "D. No. 1-176, Beside Gandhi statue,",
                "Main Road Makkapeta, Makkapeta-521190",
                "9989207847",
                "TS/RR/2022-71557",
                "TS/RR/2023-71557",
                "36AGYPV269P1ZU",
                true,
                3,
                "http://localhost:8099/send-message",
                "default-token",
                "9989207847",
                "09:00"
            );
            return configRepository.save(newConfig);
        });
    }

    public StoreConfigDto getStoreConfig() {
        StoreConfig config = getStoreConfigEntity();
        return new StoreConfigDto(
            config.name, config.addressLine1, config.addressLine2, config.phone,
            config.drugLicense22, config.drugLicense21, config.gstNumber,
            config.enableAutoReminders, config.reminderDays,
            config.whatsappGatewayUrl, config.whatsappToken, config.whatsappSender,
            config.dailyReminderTime
        );
    }

    public StoreConfigDto saveStoreConfig(StoreConfigDto dto) {
        StoreConfig config = configRepository.findById(1L).orElseGet(() -> {
            StoreConfig sc = new StoreConfig();
            sc.id = 1L;
            return sc;
        });
        config.name = dto.name();
        config.addressLine1 = dto.addressLine1();
        config.addressLine2 = dto.addressLine2();
        config.phone = dto.phone();
        config.drugLicense22 = dto.drugLicense22();
        config.drugLicense21 = dto.drugLicense21();
        config.gstNumber = dto.gstNumber();
        config.enableAutoReminders = dto.enableAutoReminders();
        config.reminderDays = dto.reminderDays();
        config.whatsappGatewayUrl = dto.whatsappGatewayUrl();
        config.whatsappToken = dto.whatsappToken();
        config.whatsappSender = dto.whatsappSender();
        config.dailyReminderTime = dto.dailyReminderTime() != null ? dto.dailyReminderTime() : "09:00";
        configRepository.save(config);
        return dto;
    }

    public void sendWhatsAppInvoice(Long saleId) {
        sendWhatsAppInvoice(saleId, null, null);
    }

    public void sendWhatsAppInvoice(Long saleId, String pdfBase64, String filename) {
        Sale sale = sales.findById(saleId).orElseThrow(() -> new EntityNotFoundException("Sale not found"));
        if (sale.customer != null && sale.customer.mobile != null && !sale.customer.mobile.trim().isEmpty()) {
            String phone = sale.customer.mobile.replaceAll("[^0-9]", "");
            if (phone.length() == 10) {
                phone = "91" + phone;
            }
            
            StoreConfig config = getStoreConfigEntity();
            String storeName = (config != null && config.name != null && !config.name.trim().isEmpty()) ? config.name.trim() : "Sri Lakshmi medical and fancy store";
            
            String messageText = "Invoice From " + storeName + ".\n\n"
                    + "మా వద్ద మందులు కొనుగోలు చేసినందుకు ధన్యవాదాలు! త్వరగా కోలుకోవాలని కోరుకుంటున్నాం. Have a healthy day! 🙏.";
            
            whatsAppService.sendWhatsAppDocument(phone, messageText, pdfBase64, filename != null ? filename : ("Invoice_" + sale.billNo + ".pdf"));
        }
    }

    public void sendCreditReminder(Long creditId) {
        CreditTransaction credit = credits.findById(creditId).orElseThrow(() -> new EntityNotFoundException("Credit not found"));
        if (credit.customer != null && credit.customer.mobile != null && !credit.customer.mobile.trim().isEmpty()) {
            String phone = credit.customer.mobile.replaceAll("[^0-9]", "");
            if (phone.length() == 10) {
                phone = "91" + phone;
            }
            StoreConfig config = getStoreConfigEntity();
            String storeName = config.name;
            String message = "Hello " + credit.customer.name + ",\n\n"
                    + "This is a friendly reminder from *" + storeName + "* regarding your outstanding due of *₹"
                    + credit.dueAmount.setScale(2) + "* for Bill No. *" + credit.sale.billNo + "*, which was due on *"
                    + credit.dueDate + "*.\n\n"
                    + "Kindly clear the dues at your earliest convenience. Thank you!";
            whatsAppService.sendWhatsAppMessage(phone, message);
        }
    }

    public void triggerDailyReminders() {
        StoreConfig config = getStoreConfigEntity();
        if (!config.enableAutoReminders) {
            return;
        }

        LocalDate today = LocalDate.now();
        List<CreditTransaction> openCredits = credits.findByStatusOrderByDueDateAsc(CreditStatus.OPEN);
        for (CreditTransaction credit : openCredits) {
            if (credit.customer != null && credit.customer.mobile != null && !credit.customer.mobile.trim().isEmpty()) {
                LocalDate thresholdDate = today.plusDays(config.reminderDays);
                if (!credit.dueDate.isAfter(thresholdDate)) {
                    String phone = credit.customer.mobile.replaceAll("[^0-9]", "");
                    if (phone.length() == 10) {
                        phone = "91" + phone;
                    }
                    String storeName = config.name;
                    String message = "Hello " + credit.customer.name + ",\n\n"
                            + "This is a friendly reminder from *" + storeName + "* regarding your outstanding due of *₹"
                            + credit.dueAmount.setScale(2) + "* for Bill No. *" + credit.sale.billNo + "*, which is due on *"
                            + credit.dueDate + ".\n\n"
                            + "Kindly clear the dues at your earliest convenience. Thank you!";
                    whatsAppService.sendWhatsAppMessage(phone, message);
                }
            }
        }
    }

    public List<UserView> users() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(u -> u.username.toLowerCase()))
                .map(this::userView)
                .toList();
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            return "-";
        }
        String clean = mobile.trim();
        if (clean.length() <= 2) {
            return clean;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length() - 2; i++) {
            sb.append("*");
        }
        sb.append(clean.substring(clean.length() - 2));
        return sb.toString();
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter.");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one numeric digit.");
        }
        if (!password.matches(".*[^a-zA-Z0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character.");
        }
    }

    private void validatePasswordHistory(UserAccount user, String rawNewPassword) {
        if (passwordEncoder.matches(rawNewPassword, user.password)) {
            throw new IllegalArgumentException("New password cannot be the same as your current password.");
        }
        if (user.passwordHistory != null && !user.passwordHistory.trim().isEmpty()) {
            String[] historicalHashes = user.passwordHistory.split(",");
            for (String hash : historicalHashes) {
                if (hash != null && !hash.trim().isEmpty()) {
                    if (passwordEncoder.matches(rawNewPassword, hash.trim())) {
                        throw new IllegalArgumentException("New password cannot be one of your recently used passwords.");
                    }
                }
            }
        }
    }

    private void addToPasswordHistory(UserAccount user, String oldPasswordHash) {
        if (oldPasswordHash == null || oldPasswordHash.trim().isEmpty()) {
            return;
        }
        List<String> history = new java.util.ArrayList<>();
        if (user.passwordHistory != null && !user.passwordHistory.trim().isEmpty()) {
            String[] parts = user.passwordHistory.split(",");
            for (String p : parts) {
                if (p != null && !p.trim().isEmpty()) {
                    history.add(p.trim());
                }
            }
        }
        history.add(oldPasswordHash.trim());
        if (history.size() > 3) {
            history = history.subList(history.size() - 3, history.size());
        }
        user.passwordHistory = String.join(",", history);
    }

    private String generateTemporaryPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specials = "@$!%*?&";
        java.util.Random rand = new java.util.Random();
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(rand.nextInt(upper.length())));
        sb.append(lower.charAt(rand.nextInt(lower.length())));
        sb.append(digits.charAt(rand.nextInt(digits.length())));
        sb.append(specials.charAt(rand.nextInt(specials.length())));
        
        String all = upper + lower + digits + specials;
        for (int i = 4; i < 10; i++) {
            sb.append(all.charAt(rand.nextInt(all.length())));
        }
        List<Character> chars = new java.util.ArrayList<>();
        for (char c : sb.toString().toCharArray()) {
            chars.add(c);
        }
        java.util.Collections.shuffle(chars);
        StringBuilder result = new StringBuilder();
        for (char c : chars) {
            result.append(c);
        }
        return result.toString();
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        if (request.username() == null || request.username().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (request.mobile() == null || request.mobile().trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile number is required.");
        }
        
        UserAccount user = users.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new IllegalArgumentException("Username or Mobile number does not match."));
                
        if (user.mobile == null || !user.mobile.trim().equals(request.mobile().trim())) {
            throw new IllegalArgumentException("Username or Mobile number does not match.");
        }
        
        if (!user.active) {
            throw new IllegalArgumentException("Account is locked. Please contact your administrator.");
        }

        String tempPwd = generateTemporaryPassword();
        user.password = passwordEncoder.encode(tempPwd);
        user.passwordResetRequired = true;
        user.temporaryPasswordExpiry = Instant.now().plus(Duration.ofMinutes(15));
        users.save(user);

        String cleanPhone = user.mobile.replaceAll("[^0-9]", "");
        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone;
        }
        String storeName = getStoreConfigEntity().name;
        String message = "*PASSWORD RESET REQUEST* from *" + storeName + "*\n\n"
                + "A password reset request was initiated for your account: *" + user.username + "*\n\n"
                + "Your temporary password is: *" + tempPwd + "*\n"
                + "This temporary password is valid for 15 minutes. You will be forced to change it immediately after login.";
        whatsAppService.sendWhatsAppMessage(cleanPhone, message);

        logActivity("USER_PASSWORD_RESET_REQUESTED", "User account:" + user.username + " requested self password reset", "User", user.id);
    }

    public void forceChangePassword(String username, String tempPassword, String newPassword) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (tempPassword == null || tempPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Temporary password is required.");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("New password is required.");
        }

        UserAccount user = users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!user.active) {
            throw new IllegalArgumentException("Account is locked. Please contact your administrator.");
        }

        if (!user.passwordResetRequired) {
            throw new IllegalArgumentException("Password reset is not required for this user.");
        }

        if (!passwordEncoder.matches(tempPassword, user.password)) {
            throw new IllegalArgumentException("Invalid temporary password.");
        }

        if (user.temporaryPasswordExpiry != null && user.temporaryPasswordExpiry.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Temporary password has expired. Please request a new password reset.");
        }

        validatePasswordStrength(newPassword);
        validatePasswordHistory(user, newPassword);
        addToPasswordHistory(user, user.password);

        user.password = passwordEncoder.encode(newPassword);
        user.passwordResetRequired = false;
        user.temporaryPasswordExpiry = null;
        UserAccount saved = users.save(user);

        logActivity("USER_PASSWORD_FORCE_CHANGED", "User account:" + saved.username + " successfully reset temporary password", "User", saved.id);
    }

    public UserView createUser(UserRequest request) {
        if (request.username() == null || request.username().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        validateMobileFormat(request.mobile());
        validateEmailFormat(request.email());

        if (users.findByUsernameIgnoreCase(request.username().trim()).isPresent()) {
            throw new IllegalArgumentException("Username already exists.");
        }
        if (users.findByMobile(request.mobile().trim()).isPresent()) {
            throw new IllegalArgumentException("The provided mobile number is already registered.");
        }
        if (users.findByEmailIgnoreCase(request.email().trim()).isPresent()) {
            throw new IllegalArgumentException("The provided email address is already registered.");
        }

        verifyOtp(request.mobile().trim(), request.otp());

        String rawPassword = generateTemporaryPassword();
        String encodedPassword = passwordEncoder.encode(rawPassword);

        UserAccount user = new UserAccount(request.username().trim(), encodedPassword, request.fullName().trim(), request.role());
        user.mobile = request.mobile().trim();
        user.email = request.email().trim();
        user.active = request.active();
        user.passwordResetRequired = true;
        user.temporaryPasswordExpiry = Instant.now().plus(Duration.ofMinutes(15));
        
        UserAccount saved = users.save(user);

        if (saved.mobile != null && !saved.mobile.trim().isEmpty()) {
            String cleanPhone = saved.mobile.replaceAll("[^0-9]", "");
            if (cleanPhone.length() == 10) {
                cleanPhone = "91" + cleanPhone;
            }
            String storeName = getStoreConfigEntity().name;
            String message = "*WELCOME TO " + storeName.toUpperCase() + "*\n\n"
                    + "Your account has been created by the Admin.\n"
                    + "Your username: *" + saved.username + "*\n"
                    + "Your temporary password is: *" + rawPassword + "*\n"
                    + "This password is valid for 15 minutes. You will be prompted to change it on your first login.";
            whatsAppService.sendWhatsAppMessage(cleanPhone, message);
        }

        logActivity("USER_CREATED", "Created user account: " + saved.username + " (Role: " + saved.role + ", Mobile: " + maskMobile(saved.mobile) + ", Email: " + (saved.email != null ? saved.email : "-") + ")", "User", saved.id);
        return userView(saved);
    }

    private String formatRole(Role role) {
        if (role == null) return "null";
        switch (role) {
            case ADMIN: return "Admin";
            case STAFF: return "Staff";
            case PHARMACIST: return "Pharmacist";
            default: return role.name();
        }
    }

    public UserView updateUser(Long id, UserRequest request) {
        if (request.mobile() == null || request.mobile().trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile number is required.");
        }
        if (request.email() == null || request.email().trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }

        UserAccount user = users.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        Optional<UserAccount> duplicateUser = users.findByUsernameIgnoreCase(request.username().trim());
        if (duplicateUser.isPresent() && !duplicateUser.get().id.equals(id)) {
            throw new IllegalArgumentException("Username already exists.");
        }

        Optional<UserAccount> duplicateMobile = users.findByMobile(request.mobile().trim());
        if (duplicateMobile.isPresent() && !duplicateMobile.get().id.equals(id)) {
            throw new IllegalArgumentException("The provided mobile number is already registered.");
        }

        validateEmailFormat(request.email());
        Optional<UserAccount> duplicateEmail = users.findByEmailIgnoreCase(request.email().trim());
        if (duplicateEmail.isPresent() && !duplicateEmail.get().id.equals(id)) {
            throw new IllegalArgumentException("The provided email address is already registered.");
        }

        String newMobile = request.mobile() != null ? request.mobile().trim() : "";
        String oldMobile = user.mobile != null ? user.mobile.trim() : "";
        if (!newMobile.equals(oldMobile)) {
            verifyOtp(newMobile, request.otp());
        }

        Role oldRole = user.role;
        String oldFullName = user.fullName != null ? user.fullName.trim() : "";
        String oldMobileVal = user.mobile != null ? user.mobile.trim() : "";
        String oldEmailVal = user.email != null ? user.email.trim() : "";
        boolean oldActive = user.active;
        String oldUsername = user.username != null ? user.username.trim() : "";

        user.username = request.username().trim();
        user.fullName = request.fullName().trim();
        user.mobile = request.mobile().trim();
        user.email = request.email().trim();
        user.role = request.role();
        
        if (!request.active()) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName().equals(user.username)) {
                throw new IllegalArgumentException("Cannot lock your own account");
            }
        }
        user.active = request.active();
 
        if (request.password() != null && !request.password().trim().isEmpty()) {
            validatePasswordStrength(request.password());
            validatePasswordHistory(user, request.password());
            addToPasswordHistory(user, user.password);
            user.password = passwordEncoder.encode(request.password());
        }
 
        UserAccount saved = users.save(user);

        boolean changed = false;
        if (!oldRole.equals(saved.role)) {
            logActivity("USER_ROLE_UPDATED", "User account:" + saved.username + " updated role to " + formatRole(saved.role) + " from " + formatRole(oldRole), "User", saved.id);
            changed = true;
        }
        String newFullName = saved.fullName != null ? saved.fullName.trim() : "";
        if (!oldFullName.equals(newFullName)) {
            logActivity("USER_NAME_UPDATED", "User account:" + saved.username + " updated Full Name from " + oldFullName + " to " + newFullName, "User", saved.id);
            changed = true;
        }
        String newUsername = saved.username != null ? saved.username.trim() : "";
        if (!oldUsername.equals(newUsername)) {
            logActivity("USER_USERNAME_UPDATED", "User account:" + oldUsername + " updated username to " + newUsername + " from " + oldUsername, "User", saved.id);
            changed = true;
        }
        String newMobileVal = saved.mobile != null ? saved.mobile.trim() : "";
        if (!oldMobileVal.equals(newMobileVal)) {
            logActivity("USER_MOBILE_UPDATED", "User account:" + saved.username + " updated mobile to " + (newMobileVal.isEmpty() ? "empty" : maskMobile(newMobileVal)) + " from " + (oldMobileVal.isEmpty() ? "empty" : maskMobile(oldMobileVal)), "User", saved.id);
            changed = true;
        }
        String newEmailVal = saved.email != null ? saved.email.trim() : "";
        if (!oldEmailVal.equals(newEmailVal)) {
            logActivity("USER_EMAIL_UPDATED", "User account:" + saved.username + " updated email to " + (newEmailVal.isEmpty() ? "empty" : newEmailVal) + " from " + (oldEmailVal.isEmpty() ? "empty" : oldEmailVal), "User", saved.id);
            changed = true;
        }
        if (oldActive != saved.active) {
            if (!saved.active) {
                logActivity("USER_LOCKED", "User account:" + saved.username + " locked", "User", saved.id);
                logActivity("USER_CREDENTIALS_LOCKED", "User account:" + saved.username + " credentials locked.", "User", saved.id);
            } else {
                logActivity("USER_UNLOCKED", "User account:" + saved.username + " unlocked", "User", saved.id);
            }
            changed = true;
        }
        if (request.password() != null && !request.password().trim().isEmpty()) {
            logActivity("USER_PASSWORD_RESET", "User account:" + saved.username + " credentials reset by admin", "User", saved.id);
            changed = true;
        }

        if (!changed) {
            logActivity("USER_UPDATED", "Updated user account: " + saved.username + " (" + saved.role + ")", "User", saved.id);
        }

        return userView(saved);
    }

    public void sendDeleteUserOtp(Long id, String adminUsername) {
        UserAccount targetUser = users.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (targetUser.username.equalsIgnoreCase(adminUsername)) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }

        UserAccount adminUser = users.findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new EntityNotFoundException("Admin account not found"));

        if (adminUser.mobile == null || adminUser.mobile.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin mobile number is not registered. Cannot perform deletion without verified admin mobile.");
        }

        String adminMobile = adminUser.mobile.trim();
        OtpData existingOtp = otpMap.get(adminMobile);
        if (existingOtp != null && existingOtp.blockedUntil != null && existingOtp.blockedUntil.isAfter(Instant.now())) {
            long remainingSecs = Duration.between(Instant.now(), existingOtp.blockedUntil).toSeconds();
            throw new IllegalArgumentException("Too many failed attempts. Mobile validation is temporarily blocked. Please retry in " + (remainingSecs / 60 + 1) + " minutes.");
        }

        String otpCode = String.format("%06d", new java.util.Random().nextInt(1000000));
        otpMap.put(adminMobile, new OtpData(otpCode, Instant.now().plus(Duration.ofMinutes(5))));

        System.out.println("[Delete User OTP Debug] Admin Mobile: " + adminMobile + ", OTP: " + otpCode);

        String cleanPhone = adminMobile.replaceAll("[^0-9]", "");
        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone;
        }

        String storeName = getStoreConfigEntity().name;
        String messageText = "*DELETE USER VERIFICATION* from *" + storeName + "*\n\n"
                + "Your OTP to authorize the deletion of user account *" + targetUser.username + "* is: *" + otpCode + "*\n"
                + "This OTP is valid for 5 minutes. Please do not share it with anyone.";

        whatsAppService.sendWhatsAppMessage(cleanPhone, messageText);
    }

    public void deleteUser(Long id, String otp, String adminUsername) {
        UserAccount targetUser = users.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (targetUser.username.equalsIgnoreCase(adminUsername)) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }

        UserAccount adminUser = users.findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new EntityNotFoundException("Admin account not found"));

        if (adminUser.mobile == null || adminUser.mobile.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin mobile number is not registered.");
        }

        verifyOtp(adminUser.mobile.trim(), otp);

        users.delete(targetUser);

        logActivity("USER_DELETED", "Deleted user account: " + targetUser.username + " (Role: " + targetUser.role + ", Mobile: " + maskMobile(targetUser.mobile) + ", Email: " + (targetUser.email != null ? targetUser.email : "-") + ") by admin " + adminUser.username, "User", id);
    }

    public void sendUpdateMobileOtp(Long userId, String mobile) {
        validateMobileFormat(mobile);

        Optional<UserAccount> duplicateMobile = users.findAll().stream()
                .filter(u -> u.mobile != null && u.mobile.trim().equalsIgnoreCase(mobile.trim()) && !u.id.equals(userId))
                .findFirst();
        if (duplicateMobile.isPresent()) {
            throw new IllegalArgumentException("The provided mobile number is already registered.");
        }

        OtpData existingOtp = otpMap.get(mobile.trim());
        if (existingOtp != null && existingOtp.blockedUntil != null && existingOtp.blockedUntil.isAfter(Instant.now())) {
            long remainingSecs = Duration.between(Instant.now(), existingOtp.blockedUntil).toSeconds();
            throw new IllegalArgumentException("Too many failed attempts. Mobile validation is temporarily blocked. Please request OTP in " + (remainingSecs / 60 + 1) + " minutes.");
        }

        String otpCode = String.format("%06d", new java.util.Random().nextInt(1000000));
        otpMap.put(mobile.trim(), new OtpData(otpCode, Instant.now().plus(Duration.ofMinutes(5))));

        System.out.println("[Update Mobile OTP Debug] User ID: " + userId + ", Mobile: " + mobile.trim() + ", OTP: " + otpCode);

        String cleanPhone = mobile.replaceAll("[^0-9]", "");
        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone;
        }

        String storeName = getStoreConfigEntity().name;
        String messageText = "*MOBILE UPDATE VERIFICATION* from *" + storeName + "*\n\n"
                + "Your OTP to verify mobile number update is: *" + otpCode + "*\n"
                + "This OTP is valid for 5 minutes.";

        whatsAppService.sendWhatsAppMessage(cleanPhone, messageText);
    }

    public UserView resetUserPassword(Long id, PasswordResetRequest request) {
        UserAccount user = users.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found"));
        String tempPwd = generateTemporaryPassword();
        user.password = passwordEncoder.encode(tempPwd);
        user.passwordResetRequired = true;
        user.temporaryPasswordExpiry = Instant.now().plus(Duration.ofMinutes(15));
        UserAccount saved = users.save(user);

        if (saved.mobile != null && !saved.mobile.trim().isEmpty()) {
            String cleanPhone = saved.mobile.replaceAll("[^0-9]", "");
            if (cleanPhone.length() == 10) {
                cleanPhone = "91" + cleanPhone;
            }
            String storeName = getStoreConfigEntity().name;
            String message = "*PASSWORD RESET* from *" + storeName + "*\n\n"
                    + "Your temporary password is: *" + tempPwd + "*\n"
                    + "This temporary password is valid for 15 minutes. You will be prompted to change it immediately after login.";
            whatsAppService.sendWhatsAppMessage(cleanPhone, message);
        }

        logActivity("USER_PASSWORD_RESET", "User account:" + saved.username + " credentials reset by admin", "User", saved.id);
        return userView(saved);
    }

    public UserView toggleUserActiveStatus(Long id, boolean active) {
        UserAccount user = users.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (!active) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName().equals(user.username)) {
                throw new IllegalArgumentException("Cannot lock your own account");
            }
        }
        boolean oldActive = user.active;
        user.active = active;
        UserAccount saved = users.save(user);
        if (oldActive != active) {
            if (active) {
                logActivity("USER_UNLOCKED", "User account:" + saved.username + " unlocked", "User", saved.id);
            } else {
                logActivity("USER_LOCKED", "User account:" + saved.username + " locked", "User", saved.id);
                logActivity("USER_CREDENTIALS_LOCKED", "User account:" + saved.username + " credentials locked.", "User", saved.id);
            }
        }
        return userView(saved);
    }

    private final Map<String, OtpData> otpMap = new java.util.concurrent.ConcurrentHashMap<>();

    private static class OtpData {
        final String code;
        final Instant expiry;
        int failedAttempts = 0;
        Instant blockedUntil;
        OtpData(String code, Instant expiry) {
            this.code = code;
            this.expiry = expiry;
        }
    }

    private void validateMobileFormat(String mobile) {
        if (mobile == null || mobile.trim().isEmpty()) {
            throw new IllegalArgumentException("Mobile number is required.");
        }
        String clean = mobile.trim();
        if (!clean.matches("^\\d{10}$")) {
            throw new IllegalArgumentException("Mobile number must be exactly 10 digits containing only numbers.");
        }
    }

    private void validateEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        String clean = email.trim();
        if (!clean.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")) {
            throw new IllegalArgumentException("Invalid email format.");
        }
    }

    private void verifyOtp(String mobile, String submittedOtp) {
        validateMobileFormat(mobile);
        String cleanMobile = mobile.trim();
        if (submittedOtp == null || submittedOtp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP code is required.");
        }
        String cleanOtp = submittedOtp.trim();

        OtpData otpData = otpMap.get(cleanMobile);
        if (otpData == null) {
            throw new IllegalArgumentException("No OTP requested or OTP has expired.");
        }

        if (otpData.blockedUntil != null && otpData.blockedUntil.isAfter(Instant.now())) {
            long remainingSecs = Duration.between(Instant.now(), otpData.blockedUntil).toSeconds();
            throw new IllegalArgumentException("Too many failed attempts. Mobile validation is temporarily blocked. Please retry in " + (remainingSecs / 60 + 1) + " minutes.");
        }

        if (otpData.expiry.isBefore(Instant.now())) {
            otpMap.remove(cleanMobile);
            throw new IllegalArgumentException("Expired OTP.");
        }

        if (!otpData.code.equals(cleanOtp)) {
            otpData.failedAttempts++;
            if (otpData.failedAttempts >= 3) {
                otpData.blockedUntil = Instant.now().plus(Duration.ofMinutes(10));
                throw new IllegalArgumentException("Invalid OTP. Too many failed attempts. Mobile validation is temporarily blocked for 10 minutes.");
            }
            throw new IllegalArgumentException("Invalid OTP. Attempts remaining: " + (3 - otpData.failedAttempts));
        }

        otpMap.remove(cleanMobile);
    }

    private void validateRegistrationFields(String username, String mobile, String email) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        validateMobileFormat(mobile);
        validateEmailFormat(email);

        if (users.findByUsernameIgnoreCase(username.trim()).isPresent()) {
            throw new IllegalArgumentException("Username already exists.");
        }
        if (users.findByMobile(mobile.trim()).isPresent()) {
            throw new IllegalArgumentException("The provided mobile number is already registered.");
        }
        if (users.findByEmailIgnoreCase(email.trim()).isPresent()) {
            throw new IllegalArgumentException("The provided email address is already registered.");
        }
    }

    public void sendRegistrationOtp(RegisterOtpRequest request) {
        validateRegistrationFields(request.username(), request.mobile(), request.email());

        OtpData existingOtp = otpMap.get(request.mobile().trim());
        if (existingOtp != null && existingOtp.blockedUntil != null && existingOtp.blockedUntil.isAfter(Instant.now())) {
            long remainingSecs = Duration.between(Instant.now(), existingOtp.blockedUntil).toSeconds();
            throw new IllegalArgumentException("Too many failed attempts. Mobile validation is temporarily blocked. Please request OTP in " + (remainingSecs / 60 + 1) + " minutes.");
        }

        String otpCode = String.format("%06d", new java.util.Random().nextInt(1000000));
        otpMap.put(request.mobile().trim(), new OtpData(otpCode, Instant.now().plus(Duration.ofMinutes(5))));

        System.out.println("[Registration OTP Debug] Mobile: " + request.mobile().trim() + ", OTP: " + otpCode);

        String cleanPhone = request.mobile().replaceAll("[^0-9]", "");
        if (cleanPhone.length() == 10) {
            cleanPhone = "91" + cleanPhone;
        }

        String storeName = getStoreConfigEntity().name;
        String messageText = "*VERIFICATION CODE* from *" + storeName + "*\n\n"
                + "Your OTP for self-registration is: *" + otpCode + "*\n"
                + "This OTP is valid for 5 minutes. Please do not share it with anyone.";

        whatsAppService.sendWhatsAppMessage(cleanPhone, messageText);
    }

    public UserView registerUser(RegisterRequest request) {
        validateRegistrationFields(request.username(), request.mobile(), request.email());

        if (request.password() == null || request.password().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }

        verifyOtp(request.mobile(), request.otp());

        String encodedPassword = passwordEncoder.encode(request.password());
        UserAccount user = new UserAccount(
            request.username().trim(),
            encodedPassword,
            request.fullName().trim(),
            Role.STAFF
        );
        user.mobile = request.mobile().trim();
        user.email = request.email().trim();
        user.active = true;

        UserAccount saved = users.save(user);
        
        activityLogs.save(new ActivityLog("USER_REGISTERED", saved.username, "Self-registered staff account via OTP", "User", saved.id));

        return userView(saved);
    }

    private UserView userView(UserAccount user) {
        return new UserView(user.id, user.username, user.fullName, user.mobile, user.email, user.role, user.active, user.createdAt);
    }
}

@Component
class DemoData implements CommandLineRunner {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MedicineRepository medicines;
    private final MedicineBatchRepository batches;
    private final DistributorRepository distributors;
    private final CustomerRepository customers;
    private final DistributorBillRepository distributorBills;
    private final DistributorPaymentRepository distributorPayments;
    private final CustomerPaymentRepository customerPayments;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final CreditTransactionRepository credits;
    private final ExpenseRepository expenses;
    private final StoreConfigRepository configRepository;

    DemoData(UserRepository users, PasswordEncoder encoder, MedicineRepository medicines,
             MedicineBatchRepository batches, DistributorRepository distributors, CustomerRepository customers,
             DistributorBillRepository distributorBills, DistributorPaymentRepository distributorPayments,
             CustomerPaymentRepository customerPayments, SaleRepository sales, SaleItemRepository saleItems,
             CreditTransactionRepository credits, ExpenseRepository expenses, StoreConfigRepository configRepository) {
        this.users = users;
        this.encoder = encoder;
        this.medicines = medicines;
        this.batches = batches;
        this.distributors = distributors;
        this.customers = customers;
        this.distributorBills = distributorBills;
        this.distributorPayments = distributorPayments;
        this.customerPayments = customerPayments;
        this.sales = sales;
        this.saleItems = saleItems;
        this.credits = credits;
        this.expenses = expenses;
        this.configRepository = configRepository;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public void run(String... args) {
        if (configRepository.count() == 0) {
            configRepository.save(new StoreConfig(
                "Sri Lakshmi Medical And Fancy Stores",
                "D. No. 1-176, Beside Gandhi statue,",
                "Main Road Makkapeta, Makkapeta-521190",
                "9989207847",
                "TS/RR/2022-71557",
                "TS/RR/2023-71557",
                "36AGYPV269P1ZU",
                true,
                3,
                "http://localhost:8099/send-message",
                "default-token",
                "9989207847",
                "09:00"
            ));
        }
        if (users.count() > 0) {
            return;
        }
        UserAccount admin = users.save(new UserAccount("admin", encoder.encode("admin123"), "Store Admin", Role.ADMIN));
        users.saveAll(List.of(
                new UserAccount("staff", encoder.encode("staff123"), "Billing Staff", Role.STAFF),
                new UserAccount("pharmacist", encoder.encode("pharmacist123"), "Duty Pharmacist", Role.PHARMACIST)));

        Distributor dist1 = distributors.save(new Distributor(
                "MediLink Distributors", "Ravi Shah", "9876543210", "27ABCDE1234F1Z5", "Pune", "medilink@upi"));
        dist1.bankName = "State Bank of India";
        dist1.bankAccountNo = "30860782555";
        dist1.bankIfscCode = "SBIN222ED1";
        distributors.save(dist1);

        Distributor dist2 = distributors.save(new Distributor(
                "Apex Pharma Solutions", "Anil Mehta", "9898989898", "27GHIJK5678L2Z9", "Mumbai", "apexpharma@upi"));
        dist2.bankName = "HDFC Bank";
        dist2.bankAccountNo = "501004825612";
        dist2.bankIfscCode = "HDFC0001234";
        distributors.save(dist2);

        Distributor dist3 = distributors.save(new Distributor(
                "Wellness Wholesale", "Sanjay Dutt", "9797979797", "27MNOPQ9012R3Z1", "Delhi", "wellness@upi"));
        dist3.bankName = "ICICI Bank";
        dist3.bankAccountNo = "000401568241";
        dist3.bankIfscCode = "ICIC0000004";
        distributors.save(dist3);

        Medicine paracetamol = medicines.save(new Medicine(
                "MED-001", "Paracetamol 500", "Paracetamol 500mg", "HealWell", "15 Tabs/Strip",
                "300490", new BigDecimal("12"), new BigDecimal("32"), new BigDecimal("28"), false));
        Medicine antibiotic = medicines.save(new Medicine(
                "MED-002", "Azithromycin 250", "Azithromycin 250mg", "NovaCare", "6 Tabs/Strip",
                "300420", new BigDecimal("12"), new BigDecimal("120"), new BigDecimal("108"), true));
        Medicine glucose = medicines.save(new Medicine(
                "MED-003", "ORS Sachet", "Oral Rehydration Salts (ORS)", "HydraMed", "21.8g Sachet",
                "300450", new BigDecimal("5"), new BigDecimal("24"), new BigDecimal("22"), false));
        Medicine metformin = medicines.save(new Medicine(
                "MED-004", "Metformin 500", "Metformin 500mg", "CarePharma", "10 Tabs/Strip",
                "300430", new BigDecimal("5"), new BigDecimal("45"), new BigDecimal("40"), true));
        Medicine atorvastatin = medicines.save(new Medicine(
                "MED-005", "Atorvastatin 10", "Atorvastatin 10mg", "HeartMed", "10 Tabs/Strip",
                "300410", new BigDecimal("12"), new BigDecimal("90"), new BigDecimal("81"), true));
        Medicine coughSyrup = medicines.save(new Medicine(
                "MED-006", "Cough Syrup", "Dextromethorphan 10mg/5ml", "NovaCare", "100 ml Bottle",
                "300460", new BigDecimal("18"), new BigDecimal("75"), new BigDecimal("68"), false));

        Medicine calpol = medicines.save(new Medicine(
                "MED-007", "Calpol 500", "Paracetamol 500mg", "GlaxoSmithKline", "15 Tabs/Strip",
                "300490", new BigDecimal("12"), new BigDecimal("30"), new BigDecimal("26"), false));
        Medicine glycomet = medicines.save(new Medicine(
                "MED-008", "Glycomet 500", "Metformin 500mg", "USV Private Limited", "10 Tabs/Strip",
                "300430", new BigDecimal("5"), new BigDecimal("42"), new BigDecimal("38"), true));
        Medicine lipitor = medicines.save(new Medicine(
                "MED-009", "Lipitor 10", "Atorvastatin 10mg", "Pfizer", "10 Tabs/Strip",
                "300410", new BigDecimal("12"), new BigDecimal("95"), new BigDecimal("85"), true));
        Medicine inhaler = medicines.save(new Medicine(
                "MED-010", "Asthma Inhaler", "Albuterol 100mcg", "AeroMed", "200 Doses/Inhaler",
                "300490", new BigDecimal("18"), new BigDecimal("150"), new BigDecimal("130"), true));

        Medicine amlodipine = medicines.save(new Medicine(
                "MED-011", "Amlodipine 5", "Amlodipine 5mg", "HeartMed", "10 Tabs/Strip",
                "300410", new BigDecimal("12"), new BigDecimal("30"), new BigDecimal("25"), true));
        Medicine ibuprofen = medicines.save(new Medicine(
                "MED-012", "Ibuprofen 400", "Ibuprofen 400mg", "PainRelief", "10 Tabs/Strip",
                "300490", new BigDecimal("12"), new BigDecimal("40"), new BigDecimal("35"), false));
        Medicine amoxicillin = medicines.save(new Medicine(
                "MED-013", "Amoxicillin 500", "Amoxicillin 500mg", "NovaCare", "10 Tabs/Strip",
                "300420", new BigDecimal("12"), new BigDecimal("110"), new BigDecimal("100"), true));
        Medicine cetirizine = medicines.save(new Medicine(
                "MED-014", "Cetirizine 10", "Cetirizine 10mg", "AllergyPharma", "10 Tabs/Strip",
                "300450", new BigDecimal("12"), new BigDecimal("25"), new BigDecimal("20"), false));
        Medicine montelukast = medicines.save(new Medicine(
                "MED-015", "Montelukast 10", "Montelukast 10mg + Levocetirizine 5mg", "BreatheEasy", "10 Tabs/Strip",
                "300490", new BigDecimal("12"), new BigDecimal("120"), new BigDecimal("110"), true));
        Medicine omeprazole = medicines.save(new Medicine(
                "MED-016", "Omeprazole 20", "Omeprazole 20mg", "GastroCare", "15 Caps/Strip",
                "300430", new BigDecimal("12"), new BigDecimal("50"), new BigDecimal("45"), true));
        Medicine pantoprazole = medicines.save(new Medicine(
                "MED-017", "Pantoprazole 40", "Pantoprazole 40mg", "GastroCare", "15 Caps/Strip",
                "300430", new BigDecimal("12"), new BigDecimal("60"), new BigDecimal("54"), true));
        Medicine ranitidine = medicines.save(new Medicine(
                "MED-018", "Ranitidine 150", "Ranitidine 150mg", "GastroCare", "10 Tabs/Strip",
                "300430", new BigDecimal("12"), new BigDecimal("20"), new BigDecimal("18"), false));
        Medicine loratadine = medicines.save(new Medicine(
                "MED-019", "Loratadine 10", "Loratadine 10mg", "AllergyPharma", "10 Tabs/Strip",
                "300450", new BigDecimal("12"), new BigDecimal("30"), new BigDecimal("25"), false));
        Medicine atorvastatin20 = medicines.save(new Medicine(
                "MED-020", "Atorvastatin 20", "Atorvastatin 20mg", "HeartMed", "10 Tabs/Strip",
                "300410", new BigDecimal("12"), new BigDecimal("130"), new BigDecimal("115"), true));
        Medicine metformin1000 = medicines.save(new Medicine(
                "MED-021", "Metformin 1000", "Metformin 1000mg", "CarePharma", "10 Tabs/Strip",
                "300430", new BigDecimal("5"), new BigDecimal("70"), new BigDecimal("60"), true));
        Medicine glimepiride = medicines.save(new Medicine(
                "MED-022", "Glimepiride 2", "Glimepiride 2mg", "CarePharma", "10 Tabs/Strip",
                "300430", new BigDecimal("5"), new BigDecimal("45"), new BigDecimal("40"), true));
        Medicine glimpM1 = medicines.save(new Medicine(
                "MED-GLIMPM1TAB", "GLIMP-M1 TAB", "Glimepiride 1mg + Metformin 500mg", "BIOCHE", "10 Tabs/Strip",
                "30049083", new BigDecimal("5"), new BigDecimal("55"), new BigDecimal("48"), true, 10, "Hypoglycemia, Nausea"));
        Medicine multivitamin = medicines.save(new Medicine(
                "MED-023", "Multivitamin", "Multivitamins + Essential Minerals", "NutraLife", "30 Caps/Bottle",
                "300450", new BigDecimal("18"), new BigDecimal("150"), new BigDecimal("135"), false));
        Medicine vitc = medicines.save(new Medicine(
                "MED-024", "Vitamin C 500", "Vitamin C 500mg", "NutraLife", "15 Tabs/Strip",
                "300450", new BigDecimal("18"), new BigDecimal("80"), new BigDecimal("70"), false));
        Medicine calcium = medicines.save(new Medicine(
                "MED-025", "Calcium D3", "Calcium 500mg + Vitamin D3 250IU", "NutraLife", "15 Tabs/Strip",
                "300450", new BigDecimal("18"), new BigDecimal("120"), new BigDecimal("105"), false));

        DistributorBill db1 = distributorBills.save(new DistributorBill(dist1, "BILL-9872", LocalDate.now().minusDays(15), LocalDate.now().plusDays(15)));
        DistributorBill db2 = distributorBills.save(new DistributorBill(dist1, "BILL-4311", LocalDate.now().minusDays(5), LocalDate.now().plusDays(25)));
        DistributorBill db3 = distributorBills.save(new DistributorBill(dist2, "BILL-7721", LocalDate.now().minusDays(10), LocalDate.now().plusDays(20)));
        DistributorBill db4 = distributorBills.save(new DistributorBill(dist3, "BILL-1209", LocalDate.now().minusDays(20), LocalDate.now().plusDays(10)));
        DistributorBill db5 = distributorBills.save(new DistributorBill(dist1, "BILL-5522", LocalDate.now().minusDays(3), LocalDate.now().plusDays(27)));

        MedicineBatch batch1 = new MedicineBatch(paracetamol, "PCM-2601", LocalDate.now().plusMonths(18), new BigDecimal("18"), new BigDecimal("28"), 120, dist1);
        batch1.distributorBill = db1;
        batches.save(batch1);

        MedicineBatch batch2 = new MedicineBatch(antibiotic, "AZ-2604", LocalDate.now().plusMonths(10), new BigDecimal("72"), new BigDecimal("108"), 35, dist1);
        batch2.distributorBill = db1;
        batches.save(batch2);

        MedicineBatch batch3 = new MedicineBatch(glucose, "ORS-2509", LocalDate.now().plusDays(60), new BigDecimal("12"), new BigDecimal("22"), 9, dist1);
        batch3.distributorBill = db2;
        batches.save(batch3);

        MedicineBatch batch4 = new MedicineBatch(metformin, "MET-2711", LocalDate.now().plusMonths(24), new BigDecimal("25"), new BigDecimal("40"), 150, dist2);
        batch4.distributorBill = db3;
        batches.save(batch4);

        MedicineBatch batch5 = new MedicineBatch(atorvastatin, "ATO-2605", LocalDate.now().plusMonths(15), new BigDecimal("55"), new BigDecimal("81"), 100, dist2);
        batch5.distributorBill = db3;
        batches.save(batch5);

        MedicineBatch batch6 = new MedicineBatch(coughSyrup, "CS-2602", LocalDate.now().minusDays(5), new BigDecimal("40"), new BigDecimal("68"), 15, dist3);
        batch6.distributorBill = db4;
        batches.save(batch6);

        MedicineBatch batch7 = new MedicineBatch(calpol, "CAL-1002", LocalDate.now().plusMonths(12), new BigDecimal("15"), new BigDecimal("26"), 80, dist1);
        batch7.distributorBill = db1;
        batches.save(batch7);

        MedicineBatch batch8 = new MedicineBatch(glycomet, "GLY-2612", LocalDate.now().plusMonths(20), new BigDecimal("20"), new BigDecimal("38"), 60, dist2);
        batch8.distributorBill = db3;
        batches.save(batch8);

        MedicineBatch batch9 = new MedicineBatch(lipitor, "LIP-2508", LocalDate.now().plusMonths(14), new BigDecimal("50"), new BigDecimal("85"), 75, dist2);
        batch9.distributorBill = db3;
        batches.save(batch9);

        batches.save(new MedicineBatch(amlodipine, "AML-101", LocalDate.now().plusMonths(18), new BigDecimal("15"), new BigDecimal("25"), 150, dist1));
        batches.save(new MedicineBatch(ibuprofen, "IBU-102", LocalDate.now().plusMonths(12), new BigDecimal("20"), new BigDecimal("35"), 120, dist1));
        batches.save(new MedicineBatch(amoxicillin, "AMX-103", LocalDate.now().plusMonths(24), new BigDecimal("60"), new BigDecimal("100"), 80, dist2));
        batches.save(new MedicineBatch(cetirizine, "CET-104", LocalDate.now().plusMonths(15), new BigDecimal("10"), new BigDecimal("20"), 200, dist2));
        batches.save(new MedicineBatch(montelukast, "MON-105", LocalDate.now().plusMonths(20), new BigDecimal("70"), new BigDecimal("110"), 100, dist3));
        batches.save(new MedicineBatch(omeprazole, "OMP-106", LocalDate.now().plusMonths(12), new BigDecimal("25"), new BigDecimal("45"), 110, dist3));
        batches.save(new MedicineBatch(pantoprazole, "PAN-107", LocalDate.now().plusMonths(18), new BigDecimal("30"), new BigDecimal("54"), 90, dist3));
        batches.save(new MedicineBatch(ranitidine, "RAN-108", LocalDate.now().plusMonths(10), new BigDecimal("10"), new BigDecimal("18"), 250, dist1));
        batches.save(new MedicineBatch(loratadine, "LOR-109", LocalDate.now().plusMonths(22), new BigDecimal("15"), new BigDecimal("25"), 140, dist2));
        batches.save(new MedicineBatch(atorvastatin20, "ATO-110", LocalDate.now().plusMonths(24), new BigDecimal("75"), new BigDecimal("115"), 70, dist2));
        batches.save(new MedicineBatch(metformin1000, "MET-111", LocalDate.now().plusMonths(14), new BigDecimal("40"), new BigDecimal("60"), 180, dist1));
        batches.save(new MedicineBatch(glimepiride, "GLI-112", LocalDate.now().plusMonths(16), new BigDecimal("25"), new BigDecimal("40"), 160, dist1));
        batches.save(new MedicineBatch(multivitamin, "MUL-113", LocalDate.now().plusMonths(12), new BigDecimal("90"), new BigDecimal("135"), 50, dist3));
        batches.save(new MedicineBatch(vitc, "VIT-114", LocalDate.now().plusMonths(18), new BigDecimal("45"), new BigDecimal("70"), 300, dist3));
        batches.save(new MedicineBatch(calcium, "CAL-115", LocalDate.now().plusMonths(20), new BigDecimal("65"), new BigDecimal("105"), 220, dist3));

        MedicineBatch b5_1 = new MedicineBatch(glucose, "ORS-5522", LocalDate.now().plusMonths(12), new BigDecimal("10"), new BigDecimal("22"), 50, dist1);
        b5_1.distributorBill = db5;
        batches.save(b5_1);

        MedicineBatch b5_2 = new MedicineBatch(metformin, "MET-5522", LocalDate.now().plusMonths(18), new BigDecimal("20"), new BigDecimal("40"), 100, dist1);
        b5_2.distributorBill = db5;
        batches.save(b5_2);

        MedicineBatch b5_3 = new MedicineBatch(paracetamol, "PCM-5522", LocalDate.now().plusMonths(24), new BigDecimal("15"), new BigDecimal("28"), 200, dist1);
        b5_3.distributorBill = db5;
        batches.save(b5_3);

        MedicineBatch b5_4 = new MedicineBatch(atorvastatin, "ATO-5522", LocalDate.now().plusMonths(15), new BigDecimal("50"), new BigDecimal("81"), 80, dist1);
        b5_4.distributorBill = db5;
        batches.save(b5_4);

        MedicineBatch b5_5 = new MedicineBatch(coughSyrup, "CS-5522", LocalDate.now().plusMonths(10), new BigDecimal("30"), new BigDecimal("68"), 60, dist1);
        b5_5.distributorBill = db5;
        batches.save(b5_5);

        MedicineBatch b5_6 = new MedicineBatch(inhaler, "INH-5522", LocalDate.now().plusMonths(14), new BigDecimal("100"), new BigDecimal("130"), 40, dist1);
        b5_6.distributorBill = db5;
        batches.save(b5_6);

        Map<Long, DistributorBill> billsMap = new HashMap<>();
        billsMap.put(db1.id, db1);
        billsMap.put(db2.id, db2);
        billsMap.put(db3.id, db3);
        billsMap.put(db4.id, db4);
        billsMap.put(db5.id, db5);

        for (DistributorBill bill : billsMap.values()) {
            bill.totalAmount = BigDecimal.ZERO.setScale(2);
            bill.gstAmount = BigDecimal.ZERO.setScale(2);
            bill.netAmount = BigDecimal.ZERO.setScale(2);
        }

        for (MedicineBatch b : batches.findAll()) {
            if (b.distributorBill != null) {
                DistributorBill bill = billsMap.get(b.distributorBill.id);
                if (bill != null) {
                    BigDecimal discount = b.discountPercentage != null ? b.discountPercentage : BigDecimal.ZERO;
                    BigDecimal multiplier = BigDecimal.ONE.subtract(discount.divide(HUNDRED, 4, RoundingMode.HALF_UP));
                    BigDecimal unitCost = b.purchasePrice.multiply(multiplier);
                    int billQty = b.quantity - (b.free != null ? b.free : 0);
                    BigDecimal cost = unitCost.multiply(BigDecimal.valueOf(billQty));
                    BigDecimal gst = cost.multiply(b.medicine.gstPercentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    
                    bill.totalAmount = money(bill.totalAmount.add(cost));
                    bill.gstAmount = money(bill.gstAmount.add(gst));
                    bill.netAmount = money(bill.netAmount.add(cost.add(gst)));
                }
            }
        }

        db1.paidAmount = BigDecimal.ZERO.setScale(2);
        db2.paidAmount = db2.netAmount;
        db3.paidAmount = new BigDecimal("10000.00");
        db4.paidAmount = db4.netAmount;
        db5.paidAmount = BigDecimal.ZERO.setScale(2);

        for (DistributorBill bill : List.of(db1, db2, db3, db4, db5)) {
            bill.dueAmount = money(bill.netAmount.subtract(bill.paidAmount));
            if (bill.dueAmount.compareTo(BigDecimal.ZERO) <= 0) {
                bill.status = "SETTLED";
            } else {
                bill.status = "OPEN";
            }
            distributorBills.save(bill);
        }

        distributorPayments.save(new DistributorPayment(
                dist1, db2, db2.netAmount, LocalDate.now().minusDays(5), "UPI", "TXN-9821820"
        ));
        distributorPayments.save(new DistributorPayment(
                dist2, db3, new BigDecimal("10000.00"), LocalDate.now().minusDays(2), "BANK_TRANSFER", "TXN-5512398"
        ));
        distributorPayments.save(new DistributorPayment(
                dist3, db4, db4.netAmount, LocalDate.now().minusDays(19), "CASH", "TXN-00021"
        ));

        Customer c1 = customers.save(new Customer("Asha Kulkarni", "9988776655", "Baner", new BigDecimal("2500")));
        Customer c2 = customers.save(new Customer("Omkar Patil", "8877665544", "Kothrud", new BigDecimal("1500")));
        Customer c3 = customers.save(new Customer("Vikram Rathore", "7766554433", "Aundh", new BigDecimal("5000")));

        createSaleRecord(sales, saleItems, credits, null, "RX-TODAY-001", PaymentMode.CASH, PaymentStatus.PAID,
                new BigDecimal("84.00"), new BigDecimal("10.08"), BigDecimal.ZERO, admin,
                List.of(batch1), List.of(3), null);

        createSaleRecord(sales, saleItems, credits, null, "RX-TODAY-002", PaymentMode.UPI, PaymentStatus.PAID,
                new BigDecimal("198.00"), new BigDecimal("9.90"), BigDecimal.ZERO, admin,
                List.of(batch3), List.of(9), null);

        Sale s3 = createSaleRecord(sales, saleItems, credits, c3, "RX-YEST-001", PaymentMode.CREDIT, PaymentStatus.DUE,
                new BigDecimal("1080.00"), new BigDecimal("129.60"), BigDecimal.ZERO, admin,
                List.of(batch2), List.of(10), LocalDate.now().plusDays(29));

        createSaleRecord(sales, saleItems, credits, c1, "RX-PREV-001", PaymentMode.CREDIT, PaymentStatus.DUE,
                new BigDecimal("560.00"), new BigDecimal("67.20"), BigDecimal.ZERO, admin,
                List.of(batch1), List.of(20), LocalDate.now().plusDays(25));

        CreditTransaction creditS3 = credits.findByCustomerIdOrderByCreatedAtDesc(c3.id).get(0);
        creditS3.paidAmount = new BigDecimal("400.00");
        creditS3.dueAmount = creditS3.creditAmount.subtract(creditS3.paidAmount);
        credits.save(creditS3);

        customerPayments.save(new CustomerPayment(
                c3, creditS3, new BigDecimal("400.00"), LocalDate.now().minusDays(1), "UPI", "TXN-982170"
        ));

        expenses.save(new Expense("WAGES", new BigDecimal("15000.00"), LocalDate.now().minusDays(2), "Staff monthly wages"));
        expenses.save(new Expense("UTILITIES", new BigDecimal("3500.00"), LocalDate.now().minusDays(4), "Electricity & Internet bills"));
        expenses.save(new Expense("MAINTENANCE", new BigDecimal("2200.00"), LocalDate.now().minusDays(1), "AC maintenance and generic store repairs"));
        expenses.save(new Expense("OTHER", new BigDecimal("1000.00"), LocalDate.now().minusDays(3), "Stationery and sanitizers"));
    }

    private Sale createSaleRecord(SaleRepository sales, SaleItemRepository saleItems, CreditTransactionRepository credits,
                                  Customer customer, String billNo, PaymentMode mode, PaymentStatus status,
                                  BigDecimal subtotal, BigDecimal gst, BigDecimal discount, UserAccount creator,
                                  List<MedicineBatch> batchList, List<Integer> qtyList, LocalDate creditDueDate) {
        Sale sale = new Sale();
        sale.billNo = billNo;
        sale.customer = customer;
        sale.discountAmount = discount.setScale(2, RoundingMode.HALF_UP);
        sale.roundingAmount = BigDecimal.ZERO.setScale(2);
        sale.totalAmount = BigDecimal.ZERO.setScale(2);
        sale.gstAmount = BigDecimal.ZERO.setScale(2);
        sale.netAmount = BigDecimal.ZERO.setScale(2);

        sale.paymentMode = mode;
        sale.paymentStatus = status;
        sale.createdBy = creator;
        sales.save(sale);

        List<SaleItem> allocated = new ArrayList<>();
        BigDecimal computedSubtotal = BigDecimal.ZERO;

        for (int i = 0; i < batchList.size(); i++) {
            MedicineBatch batch = batchList.get(i);
            int qty = qtyList.get(i);
            SaleItem item = new SaleItem();
            item.sale = sale;
            item.batch = batch;
            item.quantity = BigDecimal.valueOf(qty);
            item.mrp = batch.mrp != null ? batch.mrp : batch.medicine.mrp;
            item.sellingPrice = batch.sellingPrice;
            item.gstPercentage = batch.medicine.gstPercentage;
            item.totalAmount = batch.sellingPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            saleItems.save(item);
            allocated.add(item);
            computedSubtotal = computedSubtotal.add(item.totalAmount);

            batch.availableQuantity -= qty;
            batches.save(batch);
        }

        BigDecimal netBeforeRounding = computedSubtotal.subtract(sale.discountAmount);
        sale.netAmount = netBeforeRounding.setScale(0, RoundingMode.HALF_UP).setScale(2);
        sale.roundingAmount = sale.netAmount.subtract(netBeforeRounding).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalGst = BigDecimal.ZERO;
        if (computedSubtotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = netBeforeRounding.divide(computedSubtotal, 8, RoundingMode.HALF_UP);
            for (SaleItem item : allocated) {
                BigDecimal itemNet = item.totalAmount.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
                BigDecimal divisor = BigDecimal.ONE.add(item.gstPercentage.divide(HUNDRED, 4, RoundingMode.HALF_UP));
                BigDecimal itemTaxable = itemNet.divide(divisor, 2, RoundingMode.HALF_UP);
                BigDecimal itemGst = itemNet.setScale(2, RoundingMode.HALF_UP).subtract(itemTaxable);
                totalGst = totalGst.add(itemGst);
            }
        }
        sale.gstAmount = totalGst.setScale(2, RoundingMode.HALF_UP);
        sale.totalAmount = netBeforeRounding.subtract(sale.gstAmount).setScale(2, RoundingMode.HALF_UP);
        sales.save(sale);

        if (mode == PaymentMode.CREDIT && customer != null) {
            CreditTransaction credit = new CreditTransaction();
            credit.customer = customer;
            credit.sale = sale;
            credit.creditAmount = sale.netAmount;
            credit.paidAmount = BigDecimal.ZERO.setScale(2);
            credit.dueAmount = sale.netAmount;
            credit.dueDate = creditDueDate != null ? creditDueDate : LocalDate.now().plusDays(30);
            credit.status = CreditStatus.OPEN;
            credits.save(credit);
        }
        return sale;
    }
}
