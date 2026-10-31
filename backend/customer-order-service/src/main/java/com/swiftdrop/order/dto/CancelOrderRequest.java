package com.swiftdrop.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
    private String reason;
}
