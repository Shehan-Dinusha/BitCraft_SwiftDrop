package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

/** Gateway identity headers for MockMvc requests, as the API gateway sets them after verifying a JWT. */
final class TestCallers {

    private TestCallers() {
    }

    /** A user of the given merchant company. */
    static RequestPostProcessor merchant(UUID merchantId) {
        return request -> {
            request.addHeader("X-User-Id", UUID.randomUUID().toString());
            request.addHeader("X-User-Role", "MERCHANT");
            request.addHeader("X-Merchant-Id", merchantId.toString());
            return request;
        };
    }

    static RequestPostProcessor admin() {
        return request -> {
            request.addHeader("X-User-Id", UUID.randomUUID().toString());
            request.addHeader("X-User-Role", "SYSTEM_ADMIN");
            return request;
        };
    }

    static RequestPostProcessor dispatcher(SriLankanDistrict district) {
        return request -> {
            request.addHeader("X-User-Id", UUID.randomUUID().toString());
            request.addHeader("X-User-Role", "DISPATCHER");
            request.addHeader("X-District-Id", district.name());
            return request;
        };
    }

    static RequestPostProcessor customer() {
        return request -> {
            request.addHeader("X-User-Id", UUID.randomUUID().toString());
            request.addHeader("X-User-Role", "CUSTOMER");
            return request;
        };
    }
}
