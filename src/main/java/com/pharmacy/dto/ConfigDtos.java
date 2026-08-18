package com.pharmacy.dto;

import jakarta.validation.constraints.NotBlank;

public final class ConfigDtos {
    private ConfigDtos() {}

    public record StoreConfigDto(
        @NotBlank(message = "Pharmacy Name is mandatory") String name,
        @NotBlank(message = "Address Line 1 is mandatory") String addressLine1,
        @NotBlank(message = "Address Line 2 is mandatory") String addressLine2,
        @NotBlank(message = "Phone Number is mandatory") String phone,
        @NotBlank(message = "Drug License No. (Form 22) is mandatory") String drugLicense22,
        @NotBlank(message = "Drug License No. (Form 21) is mandatory") String drugLicense21,
        @NotBlank(message = "GSTIN / Tax Number is mandatory") String gstNumber,
        boolean enableAutoReminders,
        int reminderDays,
        String whatsappGatewayUrl,
        String whatsappToken,
        String whatsappSender,
        String dailyReminderTime
    ) {}

    public record TranslateRequest(
        @NotBlank String detailsJson,
        @NotBlank String targetLanguage
    ) {}
}
