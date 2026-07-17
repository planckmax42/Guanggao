package com.example.adplatform.admin.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 64) String industry,
        @NotBlank @Size(max = 64) String contactName,
        @NotBlank @Email @Size(max = 128) String contactEmail) {
}
