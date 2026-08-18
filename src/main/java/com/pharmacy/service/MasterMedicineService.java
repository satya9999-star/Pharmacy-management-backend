package com.pharmacy.service;

import com.pharmacy.dto.InventoryDtos.MasterMedicineView;
import com.pharmacy.model.MasterMedicine;
import com.pharmacy.repository.MasterMedicineRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class MasterMedicineService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterMedicineService.class);
    private final MasterMedicineRepository repository;
    private final List<MasterMedicineView> searchIndex = new CopyOnWriteArrayList<>();

    @PersistenceContext
    private EntityManager entityManager;

    public MasterMedicineService(MasterMedicineRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        try {
            long currentCount = repository.count();
            long nullDescCount = repository.countByMedicineDescIsNull();
            if (currentCount == 0 || nullDescCount > 0) {
                if (nullDescCount > 0) {
                    log.info("Found {} master medicine records with null descriptions in database. Wiping and re-seeding full catalog with descriptions & side effects...", nullDescCount);
                    repository.deleteAllInBatch();
                } else {
                    log.info("Starting Master Medicine CSV Data Seeding from updated_indian_medicine_data.csv...");
                }
                loadCsvData();
            } else {
                log.info("Master Medicine Catalog initialized with {} records.", currentCount);
            }
            buildInMemorySearchIndex();
        } catch (Exception e) {
            log.error("Failed to initialize Master Medicine Catalog: {}", e.getMessage(), e);
        }
    }

    public synchronized void reloadCsvData() {
        log.info("Manual reload triggered: Purging old master_medicines table and re-seeding CSV data...");
        repository.deleteAllInBatch();
        loadCsvData();
        buildInMemorySearchIndex();
    }

    private void loadCsvData() {
        ClassPathResource resource = new ClassPathResource("updated_indian_medicine_data.csv");
        if (!resource.exists()) {
            log.warn("updated_indian_medicine_data.csv not found in classpath resources.");
            return;
        }

        List<MasterMedicine> batch = new ArrayList<>(1000);
        int totalLoaded = 0;
        StringBuilder recordBuffer = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String rawLine;
            boolean firstLine = true;

            while ((rawLine = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header line
                }

                if (rawLine.trim().isEmpty() && recordBuffer.length() == 0) continue;

                if (recordBuffer.length() > 0) {
                    recordBuffer.append("\n");
                }
                recordBuffer.append(rawLine);

                if (!isQuotesBalanced(recordBuffer.toString())) {
                    continue; // Multi-line quoted field (e.g. description containing newlines), keep reading
                }

                String fullRecord = recordBuffer.toString();
                recordBuffer.setLength(0);

                List<String> parts = parseCsvLine(fullRecord);
                if (parts.size() < 2) continue;

                String name = cleanField(parts.size() > 1 ? parts.get(1) : "", 500);
                if (name.isEmpty()) continue;

                String priceStr = cleanField(parts.size() > 2 ? parts.get(2) : "0", 50);
                BigDecimal price = parseBigDecimal(priceStr);

                boolean discontinued = "TRUE".equalsIgnoreCase(cleanField(parts.size() > 3 ? parts.get(3) : "FALSE", 10));
                String manufacturer = cleanField(parts.size() > 4 ? parts.get(4) : "", 500);
                String type = cleanField(parts.size() > 5 ? parts.get(5) : "Allopathy", 255);
                String packSize = cleanField(parts.size() > 6 ? parts.get(6) : "", 255);
                String shortComp1 = cleanField(parts.size() > 7 ? parts.get(7) : "", 255);
                String shortComp2 = cleanField(parts.size() > 8 ? parts.get(8) : "", 255);
                String saltComposition = cleanField(parts.size() > 9 ? parts.get(9) : "", 500);

                if (saltComposition.isEmpty()) {
                    if (!shortComp1.isEmpty() && !shortComp2.isEmpty()) {
                        saltComposition = shortComp1 + " + " + shortComp2;
                    } else if (!shortComp1.isEmpty()) {
                        saltComposition = shortComp1;
                    }
                }
                if (saltComposition.length() > 500) {
                    saltComposition = saltComposition.substring(0, 500);
                }

                String medicineDesc = cleanField(parts.size() > 10 ? parts.get(10) : "", 65000);
                String sideEffects = cleanField(parts.size() > 11 ? parts.get(11) : "", 65000);
                String drugInteractions = cleanField(parts.size() > 12 ? parts.get(12) : "", 65000);

                MasterMedicine entity = new MasterMedicine(
                        name,
                        saltComposition,
                        medicineDesc,
                        sideEffects,
                        drugInteractions,
                        manufacturer,
                        type,
                        price,
                        packSize,
                        discontinued
                );

                batch.add(entity);
                totalLoaded++;

                if (batch.size() >= 1000) {
                    saveBatch(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                saveBatch(batch);
                batch.clear();
            }

            log.info("Successfully loaded {} master medicines with full descriptions into database.", totalLoaded);

        } catch (Exception e) {
            log.error("Error reading updated_indian_medicine_data.csv: {}", e.getMessage(), e);
        }
    }

    private boolean isQuotesBalanced(String text) {
        int quoteCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '"') {
                quoteCount++;
            }
        }
        return quoteCount % 2 == 0;
    }

    private void saveBatch(List<MasterMedicine> batch) {
        if (batch.isEmpty()) return;
        try {
            repository.saveAllAndFlush(batch);
            if (entityManager != null) {
                entityManager.clear();
            }
        } catch (Exception e) {
            log.warn("Batch save failed ({}), executing row-by-row fallback...", e.getMessage());
            for (MasterMedicine item : batch) {
                try {
                    repository.saveAndFlush(item);
                } catch (Exception ex) {
                    log.warn("Skipping bad row [{}] due to error: {}", item.name, ex.getMessage());
                }
            }
            if (entityManager != null) {
                entityManager.clear();
            }
        }
    }

    private void buildInMemorySearchIndex() {
        log.info("Building search index for Master Medicine Catalog...");
        try {
            long totalCount = repository.count();
            if (totalCount > 50000) {
                log.info("Master catalog has {} records. Direct database queries will be used for fast search.", totalCount);
                searchIndex.clear();
                return;
            }
            List<MasterMedicine> all = repository.findAll();
            searchIndex.clear();
            searchIndex.addAll(all.stream()
                    .map(this::toView)
                    .collect(Collectors.toList()));
            log.info("In-memory Master Medicine Index ready with {} searchable items.", searchIndex.size());
        } catch (Exception e) {
            log.warn("Could not build in-memory index, search will fallback to database queries: {}", e.getMessage());
        }
    }

    public List<MasterMedicineView> search(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] rawTerms = query.toLowerCase().split("[,\\s]+");
        List<String> terms = Arrays.stream(rawTerms)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());

        if (terms.isEmpty()) {
            return Collections.emptyList();
        }

        int maxResults = limit > 0 ? Math.min(limit, 50) : 20;

        if (!searchIndex.isEmpty()) {
            String qLower = query.toLowerCase().trim();
            return searchIndex.stream()
                    .filter(m -> {
                        String searchableText = (
                            (m.name() != null ? m.name() : "") + " " +
                            (m.saltComposition() != null ? m.saltComposition() : "") + " " +
                            (m.manufacturerName() != null ? m.manufacturerName() : "") + " " +
                            (m.category() != null ? m.category() : "") + " " +
                            (m.medicineDesc() != null ? m.medicineDesc() : "") + " " +
                            (m.sideEffects() != null ? m.sideEffects() : "") + " " +
                            (m.packSizeLabel() != null ? m.packSizeLabel() : "")
                        ).toLowerCase();

                        for (String term : terms) {
                            if (!searchableText.contains(term)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted((m1, m2) -> Integer.compare(
                        calculateRelevanceScore(m2, qLower, terms),
                        calculateRelevanceScore(m1, qLower, terms)
                    ))
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        String firstTerm = terms.get(0);
        String secondTerm = terms.size() > 1 ? terms.get(1) : null;
        return repository.searchByTerms(firstTerm, secondTerm, PageRequest.of(0, maxResults))
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    private MasterMedicineView toView(MasterMedicine m) {
        return new MasterMedicineView(
                m.id,
                m.name,
                m.saltComposition,
                m.medicineDesc,
                m.sideEffects,
                m.drugInteractions,
                m.manufacturerName,
                m.category,
                m.price,
                m.packSizeLabel,
                m.discontinued
        );
    }

    private int calculateRelevanceScore(MasterMedicineView m, String queryLower, List<String> terms) {
        String name = m.name() != null ? m.name().toLowerCase() : "";
        String gen = m.saltComposition() != null ? m.saltComposition().toLowerCase() : "";
        String firstTerm = terms.isEmpty() ? "" : terms.get(0);

        if (name.equals(queryLower)) return 10000;
        if (name.startsWith(queryLower)) return 9000;
        if (!firstTerm.isEmpty() && name.startsWith(firstTerm)) return 8000;

        String[] words = name.split("[\\s\\-_]+");
        if (!firstTerm.isEmpty() && words.length > 0 && words[0].startsWith(firstTerm)) return 7000;
        for (String w : words) {
            if (!firstTerm.isEmpty() && w.startsWith(firstTerm)) return 6000;
        }

        if (gen.startsWith(queryLower)) return 4500;
        if (!firstTerm.isEmpty() && gen.startsWith(firstTerm)) return 4000;
        if (name.contains(queryLower)) return 3000;
        if (gen.contains(queryLower)) return 1000;

        return 100;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }

    private String cleanField(String raw, int maxLength) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (maxLength > 0 && trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    private BigDecimal parseBigDecimal(String val) {
        try {
            return new BigDecimal(val.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
