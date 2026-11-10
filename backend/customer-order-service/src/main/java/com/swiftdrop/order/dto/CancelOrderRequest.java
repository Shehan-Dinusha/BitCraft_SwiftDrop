package com.swiftdrop.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer order cancellation request payload (FR-C3)")
public class CancelOrderRequest {

    @NotBlank(message = "Cancellation reason is required")
    @Schema(description = "Reason for cancelling the order", example = "Sender no longer needs to ship this parcel")
    @Size(max = 512, message = "Must be at most 512 characters")
    @Pattern(regexp = ValidationPatterns.FREE_TEXT, message = ValidationPatterns.FREE_TEXT_MESSAGE)
    private String reason;
}
