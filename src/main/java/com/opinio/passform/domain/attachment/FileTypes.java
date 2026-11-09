package com.opinio.passform.domain.attachment;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.opinio.passform.domain.error.DomainException;
import com.opinio.passform.domain.error.ErrorCode;

// 파일 형식 판정(SPEC 5절 219행). 확장자와 실제 내용의 서명을 모두 본다. 클라이언트의 Content-Type은 쓰지 않는다.
// 사람 결정(#63): 내용 판별은 서명까지만 한다. ZIP 기반(docx·pptx·hwpx)과 OLE 기반(doc·ppt·hwp)은 내부를 열지 않는다.
public final class FileTypes {

	public static final int HEAD_BYTES = 12;

	private static final int MAX_FILENAME_LENGTH = 255;

	public enum Audience {
		CREATOR, RESPONDENT
	}

	private enum Signature {

		JPEG {
			@Override
			boolean matches(byte[] head) {
				return startsWith(head, 0xFF, 0xD8, 0xFF);
			}
		},
		PNG {
			@Override
			boolean matches(byte[] head) {
				return startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
			}
		},
		GIF {
			@Override
			boolean matches(byte[] head) {
				return ascii(head, 0, "GIF87a") || ascii(head, 0, "GIF89a");
			}
		},
		WEBP {
			@Override
			boolean matches(byte[] head) {
				return ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP");
			}
		},
		PDF {
			@Override
			boolean matches(byte[] head) {
				return ascii(head, 0, "%PDF-");
			}
		},
		MP4 {
			@Override
			boolean matches(byte[] head) {
				return ascii(head, 4, "ftyp");
			}
		},
		ZIP {
			@Override
			boolean matches(byte[] head) {
				return startsWith(head, 0x50, 0x4B, 0x03, 0x04) || startsWith(head, 0x50, 0x4B, 0x05, 0x06)
						|| startsWith(head, 0x50, 0x4B, 0x07, 0x08);
			}
		},
		OLE {
			@Override
			boolean matches(byte[] head) {
				return startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
			}
		};

		abstract boolean matches(byte[] head);

		private static boolean startsWith(byte[] head, int... expected) {
			if (head.length < expected.length) {
				return false;
			}
			for (int i = 0; i < expected.length; i++) {
				if ((head[i] & 0xFF) != expected[i]) {
					return false;
				}
			}
			return true;
		}

		private static boolean ascii(byte[] head, int offset, String expected) {
			byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
			return head.length >= offset + bytes.length
					&& Arrays.equals(head, offset, offset + bytes.length, bytes, 0, bytes.length);
		}

	}

	private record Rule(String contentType, Signature signature, Set<Audience> audiences) {
	}

	private static final Set<Audience> BOTH = Set.of(Audience.CREATOR, Audience.RESPONDENT);

	private static final Set<Audience> CREATOR_ONLY = Set.of(Audience.CREATOR);

	private static final Set<Audience> RESPONDENT_ONLY = Set.of(Audience.RESPONDENT);

	private static final Map<String, Rule> RULES = Map.ofEntries(
			Map.entry("jpg", new Rule("image/jpeg", Signature.JPEG, BOTH)),
			Map.entry("jpeg", new Rule("image/jpeg", Signature.JPEG, BOTH)),
			Map.entry("png", new Rule("image/png", Signature.PNG, BOTH)),
			Map.entry("gif", new Rule("image/gif", Signature.GIF, BOTH)),
			Map.entry("webp", new Rule("image/webp", Signature.WEBP, BOTH)),
			Map.entry("pdf", new Rule("application/pdf", Signature.PDF, BOTH)),
			Map.entry("mp4", new Rule("video/mp4", Signature.MP4, CREATOR_ONLY)),
			Map.entry("zip", new Rule("application/zip", Signature.ZIP, RESPONDENT_ONLY)),
			Map.entry("ppt", new Rule("application/vnd.ms-powerpoint", Signature.OLE, RESPONDENT_ONLY)),
			Map.entry("pptx",
					new Rule("application/vnd.openxmlformats-officedocument.presentationml.presentation", Signature.ZIP,
							RESPONDENT_ONLY)),
			Map.entry("doc", new Rule("application/msword", Signature.OLE, RESPONDENT_ONLY)),
			Map.entry("docx",
					new Rule("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Signature.ZIP,
							RESPONDENT_ONLY)),
			Map.entry("hwp", new Rule("application/x-hwp", Signature.OLE, RESPONDENT_ONLY)),
			Map.entry("hwpx", new Rule("application/hwp+zip", Signature.ZIP, RESPONDENT_ONLY)));

	private FileTypes() {
	}

	// 저장할 content_type을 돌려준다. 목록 밖 확장자이거나 내용이 확장자와 맞지 않으면 400 invalid_file_type이다.
	public static String detect(String filename, byte[] head, Audience audience) {
		Rule rule = RULES.get(extensionOf(filename));
		if (rule == null || !rule.audiences().contains(audience) || !rule.signature().matches(head)) {
			throw new DomainException(ErrorCode.INVALID_FILE_TYPE);
		}
		return rule.contentType();
	}

	// 이미지만 화면에 바로 보이고 나머지는 내려받는다(SPEC 5절 220행).
	public static boolean opensInline(String contentType) {
		return contentType.startsWith("image/");
	}

	// 브라우저가 보낸 경로를 떼고 제어 문자를 지운다. 이름은 표시에만 쓰고 S3 키에는 넣지 않는다.
	public static String cleanFilename(String original) {
		String name = original == null ? "" : original;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("\\p{Cntrl}", "").trim();
		if (name.length() > MAX_FILENAME_LENGTH) {
			int dot = name.lastIndexOf('.');
			String extension = dot >= 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
			name = name.substring(0, MAX_FILENAME_LENGTH - extension.length()) + extension;
		}
		return name;
	}

	private static String extensionOf(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

}
