package com.swiftdrop.delivery.service;

import java.math.BigDecimal;

/** The delivery fee the courier has just confirmed collecting. */
public record CashDue(BigDecimal amount, String currency) {
}
