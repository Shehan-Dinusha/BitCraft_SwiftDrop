package com.swiftdrop.order.service;

import java.io.IOException;
import com.google.zxing.WriterException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.pdf.Barcode;
import com.lowagie.text.pdf.Barcode128;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/** Machine-readable codes printed on the shipping label (Code 128 barcode, QR code). */
final class PdfCodes {

    /** Code 128 module width and bar height on the label, in points. */
    static final float BAR_X = 1.2f;
    static final float BAR_HEIGHT = 51f;

    private PdfCodes() {
    }

    /** The label's Code 128 of the tracking number, as drawn (bar width and height included). */
    static Barcode128 code128(String value) {
        Barcode128 code = new Barcode128();
        code.setCodeType(Barcode.CODE128);
        code.setCode(value);
        code.setX(BAR_X);
        code.setBarHeight(BAR_HEIGHT);
        code.setFont(null);
        return code;
    }

    /** Vector QR code (error correction Q, 4-module quiet zone included in {@code size}). */
    static BitMatrix labelQrMatrix(String text) throws IOException, WriterException {
        return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                Map.of(EncodeHintType.MARGIN, 4, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q));
    }

    static byte[] png(BitMatrix matrix) throws IOException, WriterException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        }
    }
}
