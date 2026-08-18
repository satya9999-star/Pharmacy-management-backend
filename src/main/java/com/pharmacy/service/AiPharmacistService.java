package com.pharmacy.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AiPharmacistService {

    @Autowired(required = false)
    @Qualifier("googleGenAiChatModel")
    private ChatModel chatModel;

    @Autowired(required = false)
    private org.springframework.ai.ollama.OllamaChatModel ollamaChatModel;

    @Autowired
    private OpenRouterService openRouterService;

    private String cleanJsonWrapper(String outputText) {
        if (outputText == null) return "";
        outputText = outputText.trim();
        if (outputText.contains("```json")) {
            outputText = outputText.substring(outputText.indexOf("```json") + 7);
            if (outputText.contains("```")) {
                outputText = outputText.substring(0, outputText.indexOf("```"));
            }
        } else if (outputText.contains("```")) {
            outputText = outputText.substring(outputText.indexOf("```") + 3);
            if (outputText.contains("```")) {
                outputText = outputText.substring(0, outputText.indexOf("```"));
            }
        }
        return outputText.trim();
    }

    public String searchMedicineDetails(String medicineName) {
        if (medicineName == null || medicineName.trim().isEmpty()) {
            return getFallbackResponse("Unknown Medicine");
        }

        medicineName = medicineName.trim();

        String prompt = "Provide detailed information for the medicine: " + medicineName + ".\n"
                + "You MUST output a strict JSON object with EXACTLY the following keys, and NO other text or markdown wrapper (no ```json code blocks, just raw JSON string):\n"
                + "{\n"
                + "  \"name\": \"Medicine Name\",\n"
                + "  \"composition\": \"Chemical composition/generic name\",\n"
                + "  \"uses\": \"Common clinical uses\",\n"
                + "  \"dosage\": \"Standard dosage guidance for adults and pediatric users\",\n"
                + "  \"dosAndDonts\": \"Key Do's and Don'ts when taking this medicine\",\n"
                + "  \"interactions\": \"Common drug-to-drug and drug-to-food interactions\",\n"
                + "  \"sideEffects\": \"Common and severe side effects\",\n"
                + "  \"precautions\": \"Key precautions and contraindications\",\n"
                + "  \"storage\": \"Recommended storage instructions\",\n"
                + "  \"whoCanTake\": \"Clear safety eligibility. Must explicitly detail for: 1) KIDS: can children take it and at what age/form, 2) PREGNANT: is it safe for pregnant or breastfeeding women, 3) CHRONIC: is it safe for high blood pressure, kidney, or liver patients.\"\n"
                + "}\n"
                + "Provide accurate, clear, and comprehensive medical details.";

        String systemPrompt = "You are a professional clinical pharmacist.";
        
        // Try Local Ollama first (Gemma 3)
        if (ollamaChatModel != null) {
            try {
                ChatResponse response = ollamaChatModel.call(new org.springframework.ai.chat.prompt.Prompt(systemPrompt + "\n" + prompt));
                if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                    return cleanJsonWrapper(response.getResult().getOutput().getText());
                }
            } catch (Exception e) {
                System.err.println("Local Ollama chat failed: " + e.getMessage() + ". Falling back to external API keys.");
            }
        }

        // Fallback 1: Try OpenRouter
        String openRouterResponse = openRouterService.getChatCompletion(systemPrompt, prompt);
        if (openRouterResponse != null && !openRouterResponse.isEmpty()) {
            return cleanJsonWrapper(openRouterResponse);
        }

        // Fallback 2: Try ChatModel (Gemini)
        if (chatModel != null) {
            try {
                ChatResponse response = chatModel.call(new org.springframework.ai.chat.prompt.Prompt(systemPrompt + "\n" + prompt));
                if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                    return cleanJsonWrapper(response.getResult().getOutput().getText());
                }
            } catch (Exception e) {
                System.err.println("Spring AI ChatModel call failed: " + e.getMessage() + ". Falling back to structured default data.");
            }
        }

        return getFallbackResponse(medicineName);
    }

    private String getFallbackResponse(String medicineName) {
        String query = medicineName.toLowerCase();

        if (query.contains("paracetamol") || query.contains("acetaminophen") || query.contains("dolo")) {
            return "{\n"
                    + "  \"name\": \"Paracetamol (Dolo 650 / Calpol)\",\n"
                    + "  \"composition\": \"Acetaminophen (650mg / 500mg)\",\n"
                    + "  \"uses\": \"Fever reduction (antipyretic) and relief of mild to moderate pain (analgesic) such as headache, muscle ache, backache, and toothache.\",\n"
                    + "  \"dosage\": \"Adults: 500mg to 1000mg every 4 to 6 hours as needed. Maximum 4000mg (4g) per 24 hours. Children: Dosage based on weight (typically 10-15mg/kg per dose).\",\n"
                    + "  \"dosAndDonts\": \"DO: Take with a full glass of water. Keep track of daily limit.\\nDONT: Do not consume alcohol while taking it. Do not double doses or combine with other acetaminophen products (risks liver toxicity).\",\n"
                    + "  \"interactions\": \"Warfarin (increased bleeding risk with long-term use), Alcohol (increased hepatotoxicity risk), Cholestyramine (reduced absorption).\",\n"
                    + "  \"sideEffects\": \"Nausea, stomach pain, loss of appetite, dark urine, or yellowing of skin/eyes (rare signs of liver issues). Allergic reactions like skin rash/swelling are extremely rare.\",\n"
                    + "  \"precautions\": \"Avoid if you have severe liver disease or history of chronic alcohol abuse. Consult a doctor before use if you have kidney impairment.\",\n"
                    + "  \"storage\": \"Store at room temperature (below 30°C) in a dry place. Keep away from moisture and direct heat. Keep out of reach of children.\",\n"
                    + "  \"whoCanTake\": \"KIDS: Safe when given in pediatric weight-based doses (avoid adult tablets).\\nPREGNANT: Generally considered the safest pain/fever reliever during pregnancy; use lowest effective dose.\\nCHRONIC: Safe for high blood pressure. Use with caution in active liver disease or kidney impairment (strict limit).\"\n"
                    + "}";
        } else if (query.contains("amoxicillin") || query.contains("mox")) {
            return "{\n"
                    + "  \"name\": \"Amoxicillin\",\n"
                    + "  \"composition\": \"Amoxicillin Trihydrate\",\n"
                    + "  \"uses\": \"Treatment of bacterial infections including middle ear infections, strep throat, pneumonia, skin infections, and urinary tract infections.\",\n"
                    + "  \"dosage\": \"Adults: 250mg to 500mg every 8 hours, or 500mg to 875mg every 12 hours. Children: 20 to 45 mg/kg/day in divided doses depending on the severity of the infection.\",\n"
                    + "  \"dosAndDonts\": \"DO: Complete the entire prescribed course even if symptoms disappear early to prevent antibiotic resistance. Take with or without food.\\nDONT: Do not stop taking abruptly. Do not share antibiotics with others or save for future infections.\",\n"
                    + "  \"interactions\": \"Oral contraceptives (reduced efficacy), Probenecid (increased amoxicillin blood levels), Methotrexate (increased toxicity risk), Allopurinol (increased risk of rash).\",\n"
                    + "  \"sideEffects\": \"Diarrhea, nausea, vomiting, skin rash, or oral thrush (long-term use). Severe watery or bloody diarrhea (colitis) requires immediate medical attention.\",\n"
                    + "  \"precautions\": \"Contraindicated in patients with a history of penicillin allergy. Inform doctor of asthma or kidney disease before taking.\",\n"
                    + "  \"storage\": \"Store capsules and dry powder at room temperature. Once reconstituted by a pharmacist, liquid suspensions should ideally be stored in a refrigerator (do not freeze) and discarded after 14 days.\",\n"
                    + "  \"whoCanTake\": \"KIDS: Commonly prescribed and safe for kids in weight-based suspension formats.\\nPREGNANT: Generally safe to use (FDA Category B). Excreted in breast milk in small amounts.\\nCHRONIC: Safe for high blood pressure. Dosage adjustment required for patients with severe kidney disease.\"\n"
                    + "}";
        } else if (query.contains("ibuprofen") || query.contains("brufen") || query.contains("combiflam")) {
            return "{\n"
                    + "  \"name\": \"Ibuprofen\",\n"
                    + "  \"composition\": \"Ibuprofen (200mg / 400mg / 600mg)\",\n"
                    + "  \"uses\": \"Reducing hormones that cause pain and inflammation in the body. Used for arthritis, menstrual cramps, dental pain, fever, and sports injuries.\",\n"
                    + "  \"dosage\": \"Adults: 200mg to 400mg every 4 to 6 hours as needed. Maximum daily dose is 1200mg (over-the-counter) or 3200mg under strict medical supervision.\",\n"
                    + "  \"dosAndDonts\": \"DO: Always take with food or milk to minimize gastrointestinal upset. Drink plenty of water.\\nDONT: Do not take on an empty stomach. Do not combine with aspirin or other NSAIDs. Do not exceed recommended limits.\",\n"
                    + "  \"interactions\": \"Aspirin & other NSAIDs (increased GI ulcer risk), Antihypertensives (reduced blood pressure control), Anticoagulants like Warfarin (severe bleeding risks), Lithium (toxicity risk).\",\n"
                    + "  \"sideEffects\": \"Indigestion, heartburn, stomach pain, nausea, dizziness, or headache. Serious side effects include gastrointestinal bleeding, stomach ulcers, or kidney problems.\",\n"
                    + "  \"precautions\": \"High risk for patients with active stomach ulcers, cardiovascular disease, hypertension, or renal impairment. Avoid in the third trimester of pregnancy.\",\n"
                    + "  \"storage\": \"Store below 25°C in a dry place. Protect from direct light and damp environments. Keep containers tightly closed.\",\n"
                    + "  \"whoCanTake\": \"KIDS: Safe for infants over 6 months in weight-appropriate formulations. Never give adult tablets.\\nPREGNANT: Avoid, especially during the third trimester (risk of fetal heart/kidney complications).\\nCHRONIC: Avoid in severe high blood pressure, heart failure, active stomach ulcers, or kidney failure as it can elevate BP and stress kidneys.\"\n"
                    + "}";
        } else if (query.contains("metformin") || query.contains("glycomet")) {
            return "{\n"
                    + "  \"name\": \"Metformin\",\n"
                    + "  \"composition\": \"Metformin Hydrochloride (500mg / 850mg / 1000mg)\",\n"
                    + "  \"uses\": \"Oral anti-diabetic medication used to control blood sugar levels in type 2 diabetes mellitus by improving insulin sensitivity and reducing glucose production.\",\n"
                    + "  \"dosage\": \"Initial: 500mg twice daily or 850mg once daily with meals. Maintenance: Titrated up to 2000mg-2550mg per day in divided doses depending on HbA1c response.\",\n"
                    + "  \"dosAndDonts\": \"DO: Take with meals to reduce stomach upset. Stay well-hydrated. Check kidney functions regularly.\\nDONT: Avoid excessive alcohol intake (escalates lactic acidosis risk). Do not skip meals or skip blood sugar tracking.\",\n"
                    + "  \"interactions\": \"Contrast dye (temporary suspension required before CT scans to prevent renal failure), Cimetidine (increased metformin levels), Diuretics (increased risk of hypoglycemia).\",\n"
                    + "  \"sideEffects\": \"Nausea, vomiting, abdominal bloating, gas, diarrhea, and metallic taste. Long-term use may cause Vitamin B12 deficiency. Lactic acidosis is a rare but life-threatening side effect.\",\n"
                    + "  \"precautions\": \"Contraindicated in severe renal impairment (eGFR < 30 mL/min), hepatic failure, acute heart failure, or diabetic ketoacidosis. Discontinue if dehydration occurs.\",\n"
                    + "  \"storage\": \"Store between 15°C and 30°C in a tightly closed container. Keep in dry areas and protect from high humidity.\",\n"
                    + "  \"whoCanTake\": \"KIDS: Safe for children 10 years and older under strict specialist prescription.\\nPREGNANT: Consult doctor. Insulin is preferred, though metformin is sometimes continued under supervision.\\nCHRONIC: Contraindicated in severe kidney disease (eGFR < 30) or liver failure due to lactic acidosis risk. Safe for controlled BP.\"\n"
                    + "}";
        } else {
            String formattedName = medicineName.substring(0, 1).toUpperCase() + medicineName.substring(1).toLowerCase();
            return "{\n"
                    + "  \"name\": \"" + formattedName + "\",\n"
                    + "  \"composition\": \"Active pharmaceutical generic composition of " + formattedName + "\",\n"
                    + "  \"uses\": \"Commonly prescribed for indications related to this therapeutic category. Please consult a registered medical practitioner for exact diagnosis.\",\n"
                    + "  \"dosage\": \"Please refer to the manufacturer's package insert or consult a physician. Dosage varies widely by age, weight, and general health condition.\",\n"
                    + "  \"dosAndDonts\": \"DO: Follow the instructions given by your pharmacist or doctor. Read the product leaflet.\\nDONT: Do not self-medicate, alter doses without consulting a physician, or take expired stock.\",\n"
                    + "  \"interactions\": \"May interact with other prescription drugs or over-the-counter medications. Provide your physician with a complete list of drugs you currently take.\",\n"
                    + "  \"sideEffects\": \"Common side effects may include mild gastrointestinal discomfort, dizziness, or headache. Allergic reactions require urgent emergency medical attention.\",\n"
                    + "  \"precautions\": \"Consult a doctor if you are pregnant, planning to become pregnant, lactating, or have pre-existing liver/kidney impairments.\",\n"
                    + "  \"storage\": \"Store in a cool, dry place away from direct sunlight and moisture. Store below 25°C. Keep out of reach of children.\",\n"
                    + "  \"whoCanTake\": \"KIDS: Consult a pediatrician. Do not self-administer adult doses to children.\\nPREGNANT: Consult an obstetrician before use. Many drugs cross the placenta or enter breast milk.\\nCHRONIC: Avoid or adjust dose if you have high blood pressure, kidney failure, or liver dysfunction. Always check with your doctor.\"\n"
                    + "}";
        }
    }

    public String translateDetails(String detailsJson, String targetLanguage) {
        if (detailsJson == null || detailsJson.trim().isEmpty() || targetLanguage == null || targetLanguage.trim().isEmpty()) {
            return detailsJson;
        }

        String prompt = "Translate the following medicine details JSON object into the native Indian language: " + targetLanguage.trim() + ".\n"
                + "You MUST output a strict JSON object with EXACTLY the same keys as the input, and NO other text or markdown wrapper (no ```json code blocks, just raw JSON string).\n"
                + "Translate only the JSON values (e.g. Composition, Uses, Dosage, Do's/Don'ts, Side Effects, Safety eligibility, storage). Keep the scientific chemical names (like 'Acetaminophen', 'Amoxicillin') recognizable or transliterated if appropriate for local patients to read, but translate the clinical context.\n"
                + "Input JSON:\n"
                + detailsJson.trim();

        String systemPrompt = "You are a professional medical translator.";
        
        // Try Local Ollama first (Gemma 3)
        if (ollamaChatModel != null) {
            try {
                ChatResponse response = ollamaChatModel.call(new org.springframework.ai.chat.prompt.Prompt(systemPrompt + "\n" + prompt));
                if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                    return cleanJsonWrapper(response.getResult().getOutput().getText());
                }
            } catch (Exception e) {
                System.err.println("Local Ollama translation failed: " + e.getMessage() + ". Falling back to external API keys.");
            }
        }

        // Fallback 1: Try OpenRouter
        String openRouterResponse = openRouterService.getChatCompletion(systemPrompt, prompt);
        if (openRouterResponse != null && !openRouterResponse.isEmpty()) {
            return cleanJsonWrapper(openRouterResponse);
        }

        // Fallback 2: Try ChatModel (Gemini)
        if (chatModel != null) {
            try {
                ChatResponse response = chatModel.call(new org.springframework.ai.chat.prompt.Prompt(systemPrompt + "\n" + prompt));
                if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                    return cleanJsonWrapper(response.getResult().getOutput().getText());
                }
            } catch (Exception e) {
                System.err.println("Spring AI translation failed: " + e.getMessage() + ". Returning original details.");
            }
        }

        return detailsJson;
    }
}
