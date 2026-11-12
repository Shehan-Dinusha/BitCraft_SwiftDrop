package com.swiftdrop.merchant;

import com.swiftdrop.merchant.dto.*;
import java.math.BigDecimal;

public final class B2bTestData {
    private B2bTestData() {}
    public static B2bOrderRequest order() {
        return new B2bOrderRequest("SHOP-001",
                new B2bAddress("10 Galle Road", "Colombo", "colombo", "00300", "Test Shop", "077 123 4567"),
                new B2bAddress("25 Kandy Road", "Gampaha", "gampaha", "11000", "Test Recipient", "+94 71 123 4567"),
                new B2bParcel(null, new BigDecimal("1500"), true, "Books", null, null), "express");
    }
}
