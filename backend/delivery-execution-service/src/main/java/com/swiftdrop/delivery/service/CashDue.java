package com.swiftdrop.delivery.service;

import com.swiftdrop.delivery.domain.enums.CashKind;

import java.math.BigDecimal;

/** Cash the courier has just confirmed collecting for a step. */
public record CashDue(CashKind kind, BigDecimal amount, String currency) {
}
