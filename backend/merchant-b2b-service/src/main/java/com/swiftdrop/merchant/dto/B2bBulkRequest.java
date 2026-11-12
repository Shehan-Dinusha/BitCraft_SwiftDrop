package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

@Schema(description = "Up to 500 orders; each item is validated independently")
public record B2bBulkRequest(
        @NotEmpty @Size(max = 500) @Schema(description = "Orders in input order") List<B2bOrderRequest> orders
) {}
