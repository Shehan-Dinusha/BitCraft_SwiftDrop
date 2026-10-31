package com.swiftdrop.contracts.enums;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Types of cash movement recorded by the cash-on-delivery ledger.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Cash-on-delivery transaction type.", example = "COD_COLLECTION")
public enum CashTransactionType {
    COD_COLLECTION,
    MERCHANT_REMITTANCE,
    REFUND
}
