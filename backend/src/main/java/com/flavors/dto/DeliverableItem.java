package com.flavors.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DeliverableItem(
        @NotBlank
        @Size(max = 30)
        String platform,

        @NotBlank
        @Size(max = 30)
        String type,

        @NotNull
        @Positive
        Integer count) {
}
