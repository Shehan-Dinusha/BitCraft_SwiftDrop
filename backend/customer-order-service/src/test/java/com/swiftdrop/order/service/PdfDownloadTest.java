package com.swiftdrop.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDownloadTest {

    @Test
    @DisplayName("File name is prefix plus tracking number")
    void buildsFileName() {
        var pdf = new CustomerOrderService.PdfDownload("SD-7K4M-9QX2", new byte[0]);

        assertThat(pdf.fileName("receipt")).isEqualTo("receipt-SD-7K4M-9QX2.pdf");
        assertThat(pdf.fileName("delivery-receipt")).isEqualTo("delivery-receipt-SD-7K4M-9QX2.pdf");
    }

    @Test
    @DisplayName("Only letters, digits and hyphens survive in the file name")
    void stripsUnsafeCharacters() {
        var pdf = new CustomerOrderService.PdfDownload("SD-1\"; x=../\r\nA B", new byte[0]);

        assertThat(pdf.fileName("receipt")).isEqualTo("receipt-SD-1xAB.pdf");
    }
}
