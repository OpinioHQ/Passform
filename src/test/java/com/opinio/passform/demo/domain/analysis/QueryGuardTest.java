package com.opinio.passform.demo.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueryGuardTest {

	private static final List<String> FORM_TEXTS = List.of("소속 학과", "통계학과", "앱 버전", "분포도 경험", "버전 2 사용 경험");

	@ParameterizedTest
	@ValueSource(strings = { "버전 1만 보여줘", "버전별 응답 수 알려줘", "v2만 알려줘", "버전마다 비교해줘", "2차 버전 응답", "최신 버전 응답만" })
	void 명시적인_범위_표현을_잡는다(String query) {
		assertThat(QueryGuard.mentionsUnsupportedScope(query, List.of())).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "소프트웨어학과 몇 명이야", "앱 버전 쓰는 사람 수", "버전 2 사용 경험 있는 사람" })
	void 일반_조건과_폼_문구는_범위로_보지_않는다(String query) {
		assertThat(QueryGuard.mentionsUnsupportedScope(query, FORM_TEXTS)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "평균 나이 알려줘", "학과별 통계 보여줘", "학년별로 인원 알려줘", "4학년 비율", "문항별 응답 수" })
	void 지원하지_않는_통계를_잡는다(String query) {
		assertThat(QueryGuard.mentionsUnsupportedStatistic(query, FORM_TEXTS)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "통계학과 학생 수", "전체 응답 수 알려줘", "특별 전형 지원자", "별로라는 의견 요약해줘", "분포도 경험 있는 사람" })
	void 폼_문구와_일반_표현은_통계로_보지_않는다(String query) {
		assertThat(QueryGuard.mentionsUnsupportedStatistic(query, FORM_TEXTS)).isFalse();
	}

}
