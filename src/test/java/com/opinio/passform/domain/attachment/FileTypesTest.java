package com.opinio.passform.domain.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.opinio.passform.domain.attachment.FileTypes.Audience;
import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

// SPEC 5절 219행, AC63. 확장자와 내용을 모두 보고, 제작자와 응답자의 허용 목록이 다르다.
class FileTypesTest {

	private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0);

	private static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

	private static final byte[] GIF = ascii("GIF89a");

	private static final byte[] WEBP = ascii("RIFF\0\0\0\0WEBP");

	private static final byte[] PDF = ascii("%PDF-1.7");

	private static final byte[] MP4 = ascii("\0\0\0\u0018ftypmp42");

	private static final byte[] ZIP = bytes(0x50, 0x4B, 0x03, 0x04);

	private static final byte[] OLE = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);

	private static final byte[] EXE = ascii("MZ\u0090\0\u0003");

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	private static byte[] ascii(String text) {
		return text.getBytes(StandardCharsets.ISO_8859_1);
	}

	static Stream<Arguments> creatorAllowed() {
		return Stream.of(Arguments.of("a.jpg", JPEG, "image/jpeg"), Arguments.of("a.jpeg", JPEG, "image/jpeg"),
				Arguments.of("a.png", PNG, "image/png"), Arguments.of("a.gif", GIF, "image/gif"),
				Arguments.of("a.webp", WEBP, "image/webp"), Arguments.of("a.pdf", PDF, "application/pdf"),
				Arguments.of("a.mp4", MP4, "video/mp4"), Arguments.of("A.PNG", PNG, "image/png"));
	}

	static Stream<Arguments> respondentAllowed() {
		return Stream.of(Arguments.of("a.jpg", JPEG, "image/jpeg"), Arguments.of("a.png", PNG, "image/png"),
				Arguments.of("a.gif", GIF, "image/gif"), Arguments.of("a.webp", WEBP, "image/webp"),
				Arguments.of("a.pdf", PDF, "application/pdf"), Arguments.of("a.zip", ZIP, "application/zip"),
				Arguments.of("a.ppt", OLE, "application/vnd.ms-powerpoint"),
				Arguments.of("a.pptx", ZIP, "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
				Arguments.of("a.doc", OLE, "application/msword"),
				Arguments.of("a.docx", ZIP, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
				Arguments.of("a.hwp", OLE, "application/x-hwp"), Arguments.of("a.hwpx", ZIP, "application/hwp+zip"));
	}

	@ParameterizedTest
	@MethodSource("creatorAllowed")
	void 제작자_허용_형식은_판별한_content_type을_돌려준다(String name, byte[] head, String expected) {
		assertThat(FileTypes.detect(name, head, Audience.CREATOR)).isEqualTo(expected);
	}

	@ParameterizedTest
	@MethodSource("respondentAllowed")
	void 응답자_허용_형식은_판별한_content_type을_돌려준다(String name, byte[] head, String expected) {
		assertThat(FileTypes.detect(name, head, Audience.RESPONDENT)).isEqualTo(expected);
	}

	static Stream<Arguments> disguised() {
		return Stream.of(Arguments.of("a.pdf", EXE), Arguments.of("a.png", EXE), Arguments.of("a.jpg", PDF),
				Arguments.of("a.mp4", PNG), Arguments.of("a.pdf", new byte[0]));
	}

	@ParameterizedTest
	@MethodSource("disguised")
	void 확장자만_바꾼_파일은_거부한다(String name, byte[] head) {
		assertRejected(name, head, Audience.CREATOR);
	}

	@ParameterizedTest
	@CsvSource({ "a.exe", "a.js", "a.sh", "a.svg", "noextension", "a.pdf.exe" })
	void 목록_밖_확장자는_내용이_맞아도_거부한다(String name) {
		assertRejected(name, PDF, Audience.CREATOR);
		assertRejected(name, PDF, Audience.RESPONDENT);
	}

	@Test
	void 제작자는_문서와_ZIP을_올릴_수_없다() {
		assertRejected("a.zip", ZIP, Audience.CREATOR);
		assertRejected("a.docx", ZIP, Audience.CREATOR);
		assertRejected("a.hwp", OLE, Audience.CREATOR);
	}

	@Test
	void 응답자는_MP4를_올릴_수_없다() {
		assertRejected("a.mp4", MP4, Audience.RESPONDENT);
	}

	@Test
	void 이미지만_화면에_바로_열린다() {
		assertThat(FileTypes.opensInline("image/png")).isTrue();
		assertThat(FileTypes.opensInline("application/pdf")).isFalse();
		assertThat(FileTypes.opensInline("video/mp4")).isFalse();
	}

	@Test
	void 파일_이름에서_경로와_제어_문자를_뗀다() {
		assertThat(FileTypes.cleanFilename("..\\..\\C:\\a/b/안내\n문.pdf")).isEqualTo("안내문.pdf");
		assertThat(FileTypes.cleanFilename(null)).isEmpty();
		String longName = "가".repeat(400) + ".pdf";
		assertThat(FileTypes.cleanFilename(longName)).hasSize(255).endsWith(".pdf");
	}

	private static void assertRejected(String name, byte[] head, Audience audience) {
		assertThatThrownBy(() -> FileTypes.detect(name, head, audience)).isInstanceOfSatisfying(DomainException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_TYPE));
	}

}
