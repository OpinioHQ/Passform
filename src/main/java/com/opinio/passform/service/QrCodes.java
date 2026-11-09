package com.opinio.passform.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

// 공유 링크를 담은 QR PNG를 만든다(SPEC 5절 126행·204행, AC10). 외부 호출이 없고 같은 입력이면 같은 바이트다.
@Component
public class QrCodes {

	// 휴대폰 카메라로 화면이나 인쇄물을 찍기에 충분한 크기다. 여백(quiet zone)은 ZXing 기본 4칸이다.
	static final int SIZE_PX = 480;

	public byte[] png(String content) {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX,
					Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(), EncodeHintType.ERROR_CORRECTION,
							ErrorCorrectionLevel.M));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			MatrixToImageWriter.writeToStream(matrix, "PNG", out);
			return out.toByteArray();
		}
		catch (WriterException e) {
			// 공유 링크 길이로는 일어나지 않는다. 일어나면 서버 오류다.
			throw new IllegalStateException("QR을 만들지 못했다", e);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
