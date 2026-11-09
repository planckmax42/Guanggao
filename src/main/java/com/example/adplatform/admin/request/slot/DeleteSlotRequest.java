package com.example.adplatform.admin.request.slot;

import jakarta.validation.constraints.NotBlank;

public record DeleteSlotRequest(
        @NotBlank String publicId,
        @NotBlank String slotCode){}
