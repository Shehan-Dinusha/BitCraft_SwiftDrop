package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.dto.BulkOrderIngestItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BulkCsvOrderProcessorTest {

    private BulkCsvOrderProcessor csvProcessor;

    @BeforeEach
    void setUp() {
        csvProcessor = new BulkCsvOrderProcessor();
    }

    @Test
    @DisplayName("parseCsv should parse CSV rows and extract valid BulkOrderIngestItems")
    void testParseCsvSuccess() {
        String csvData = """
                recipient_name,recipient_phone,delivery_district,delivery_address,package_weight_kg,delivery_speed
                Kasun Perera,+94 77 123 4567,COLOMBO,"123 Main St, Colombo 03",2.5,EXPRESS
                Nimal Silva,+94 71 987 6543,GAMPAHA,45 Kandy Rd Kadawatha,1.0,STANDARD
                """;

        List<BulkOrderIngestItem> items = csvProcessor.parseCsv(
                new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, items.size());

        BulkOrderIngestItem first = items.get(0);
        assertEquals("Kasun Perera", first.recipientName());
        assertEquals("+94 77 123 4567", first.recipientPhone());
        assertEquals(SriLankanDistrict.COLOMBO, first.deliveryDistrict());
        assertEquals("123 Main St, Colombo 03", first.deliveryAddress());
        assertEquals(new BigDecimal("2.5"), first.packageWeightKg());
        assertEquals("EXPRESS", first.deliverySpeed());

        BulkOrderIngestItem second = items.get(1);
        assertEquals("Nimal Silva", second.recipientName());
        assertEquals(SriLankanDistrict.GAMPAHA, second.deliveryDistrict());
        assertEquals("STANDARD", second.deliverySpeed());
    }

    @Test
    @DisplayName("parseCsv should handle missing header or empty rows gracefully")
    void testParseCsvEmptyOrInvalidRows() {
        String csvData = """
                Sunil Mendis,+94 77 000 1111,KANDY,Peradeniya Road
                
                """;

        List<BulkOrderIngestItem> items = csvProcessor.parseCsv(
                new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, items.size());
        assertEquals("Sunil Mendis", items.get(0).recipientName());
        assertEquals(SriLankanDistrict.KANDY, items.get(0).deliveryDistrict());
    }
}
