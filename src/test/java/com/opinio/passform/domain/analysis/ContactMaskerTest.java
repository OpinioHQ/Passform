package com.opinio.passform.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// AC92: 외부로 보내는 답 속 전화번호·이메일 가리기.
class ContactMaskerTest {

	@ParameterizedTest
	@ValueSource(strings = { "01012345678", "010-1234-5678", "010 1234 5678", "010.1234.5678", "0111234567",
			"+82-10-1234-5678", "821012345678", "02-123-4567", "031-123-4567", "0312345678", "+82-2-1234-5678",
			"+82-31-123-4567", "010 - 1234 - 5678" })
	void 전화번호는_구분자와_관계없이_가린다(String phone) {
		assertThat(ContactMasker.mask("번호는 " + phone + "입니다")).isEqualTo("번호는 [전화번호]입니다");
	}

	@ParameterizedTest
	@ValueSource(strings = { "a@example.com", "first.last+tag@sub.example.co.kr" })
	void 이메일을_가린다(String email) {
		assertThat(ContactMasker.mask("메일은 " + email + "으로")).isEqualTo("메일은 [이메일]으로");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-10에 뵙겠습니다", "학번 20261234", "참석자 300명", "@멘션" })
	void 연락처가_아닌_값은_두다(String text) {
		assertThat(ContactMasker.mask(text)).isEqualTo(text);
	}

}
