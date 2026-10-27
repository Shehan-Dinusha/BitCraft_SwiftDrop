package com.swiftdrop.merchant.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.merchant.dto.BulkOrderIngestItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BulkCsvOrderProcessor {

    /**
     * Parses a CSV file with expected headers:
     * recipient_name,recipient_phone,delivery_district,delivery_address,package_weight_kg,delivery_speed
     */
    public List<BulkOrderIngestItem> parseCsv(InputStream inputStream) {
        List<BulkOrderIngestItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (isHeader) {
                    isHeader = false;
                    // Skip header row if it contains recipient or district
                    if (line.toLowerCase().contains("recipient") || line.toLowerCase().contains("district")) {
                        continue;
                    }
                }

                String[] tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (tokens.length < 4) {
                    log.warn("Skipping invalid CSV line (insufficient columns): {}", line);
                    continue;
                }

                String recipientName = stripQuotes(tokens[0]);
                String recipientPhone = stripQuotes(tokens[1]);
                String districtStr = stripQuotes(tokens[2]).toUpperCase();
                String deliveryAddress = stripQuotes(tokens[3]);

                BigDecimal weight = BigDecimal.valueOf(1.0);
                if (tokens.length > 4 && !tokens[4].trim().isEmpty()) {
                    try {
                        weight = new BigDecimal(stripQuotes(tokens[4]));
                    } catch (NumberFormatException e) {
                        weight = BigDecimal.valueOf(1.0);
                    }
                }

                String speed = "STANDARD";
                if (tokens.length > 5 && !tokens[5].trim().isEmpty()) {
                    speed = stripQuotes(tokens[5]).toUpperCase();
                }

                SriLankanDistrict district;
                try {
                    district = SriLankanDistrict.valueOf(districtStr);
                } catch (IllegalArgumentException e) {
                    district = SriLankanDistrict.COLOMBO;
                }

                items.add(new BulkOrderIngestItem(
                        recipientName,
                        recipientPhone,
                        district,
                        deliveryAddress,
                        weight,
                        speed
                ));
            }
        } catch (Exception e) {
            log.error("Failed to parse merchant CSV order batch", e);
            throw new IllegalArgumentException("Failed to parse CSV file: " + e.getMessage(), e);
        }
        return items;
    }

    private String stripQuotes(String val) {
        if (val == null) return "";
        val = val.trim();
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            return val.substring(1, val.length() - 1).trim();
        }
        return val;
    }
}
