package com.opinio.passform.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

// 입력은 2026-11-04에 gpt-6-luna가 parse_query.md v2로 실제 낸 출력이다(docs/demo/NOTES.md).
class ParsedContextRepairTest {

	private static final Set<String> OPTIONS = Set.of("소프트웨어학과", "경영학과", "4학년", "주말");

	private static RequestContext context(List<String> conditions, String match, String operation) {
		return new RequestContext(conditions, match, operation, null, null, null);
	}

	private static RequestContext repair(String query, RequestContext parsed) {
		return ParsedContextRepair.repair(query, parsed, OPTIONS, expression -> expression.contains("지원 동기"));
	}

	@Test
	void 파서가_버린_조건을_되살린다() {
		RequestContext repaired = repair("수의학과 학생 수 알려줘", context(List.of(), "all", "count"));
		assertThat(repaired.conditions()).containsExactly("수의학과");
		assertThat(repaired.match()).isNull();
		assertThat(repaired.operation()).isEqualTo("count");
	}

	@Test
	void 진짜_전체_요청은_조건을_만들지_않는다() {
		assertThat(repair("전체 응답 수 알려줘", context(List.of(), "all", "count")).conditions()).isEmpty();
		assertThat(repair("응답 결과를 알려줘", context(List.of(), "all", null)).operation()).isNull();
	}

	@Test
	void 조건에_붙은_일반_명사를_뗀다() {
		assertThat(repair("경영학과 학생 수 알려줘", context(List.of("경영학과 학생"), "all", "count")).conditions())
			.containsExactly("경영학과");
	}

	@Test
	void 아닌을_not으로_푼다() {
		RequestContext repaired = repair("소프트웨어학과가 아닌 학생 수 알려줘",
				context(List.of("소프트웨어학과가 아닌 학생"), "all", "count"));
		assertThat(repaired.conditions()).containsExactly("소프트웨어학과");
		assertThat(repaired.match()).isEqualTo("not");
	}

	@Test
	void 긍정과_부정이_섞이면_뜻을_바꾸지_않고_match를_비운다() {
		RequestContext repaired = repair("소프트웨어학과가 아닌 4학년 수", context(List.of("소프트웨어학과가 아닌", "4학년"), "all", null));
		assertThat(repaired.conditions()).containsExactly("소프트웨어학과가 아닌", "4학년");
		assertThat(repaired.match()).isNull();
		assertThat(repaired.operation()).isEqualTo("count");
	}

	@Test
	void 답하지_않은_표현을_미응답_조건으로_바꾼다() {
		RequestContext repaired = repair("3번 문항에 답하지 않은 학생 수 알려줘", context(List.of("3번 문항에 답하지 않은"), "all", "count"));
		assertThat(repaired.conditions()).containsExactly("3번 문항 - 미응답");
		assertThat(repaired.match()).isNull();
	}

	@Test
	void 낱말이_전부_선택지면_조건을_나눈다() {
		RequestContext repaired = repair("소프트웨어학과 4학년 몇 명이야?", context(List.of("소프트웨어학과 4학년"), null, "count"));
		assertThat(repaired.conditions()).containsExactly("소프트웨어학과", "4학년");
	}

	@Test
	void target과_topic에_같은_표현이_복사되면_문항인지로_가른다() {
		RequestContext question = repair("지원 동기 요약해줘",
				new RequestContext(List.of(), "all", "summarize", null, "지원 동기", "지원 동기"));
		assertThat(question.target()).isEqualTo("지원 동기");
		assertThat(question.topic()).isNull();

		RequestContext opinion = repair("비용이 부담된다는 의견 요약해줘", new RequestContext(List.of(), "all", "summarize", null,
				"비용이 부담된다는 의견", "비용이 부담된다는 의견"));
		assertThat(opinion.target()).isNull();
		assertThat(opinion.topic()).isEqualTo("비용이 부담된다");
	}

	@Test
	void 일반어뿐인_target과_topic은_버려서_전체_요약이_된다() {
		RequestContext repaired = repair("설문조사 결과 요약해줘",
				new RequestContext(List.of(), "all", "summarize", null, "설문조사 결과", "설문조사 결과"));
		assertThat(repaired.target()).isNull();
		assertThat(repaired.topic()).isNull();
		assertThat(repaired.conditions()).isEmpty();
	}

	@Test
	void 조건으로_새어_나온_target과_의미_없는_topic을_정리한다() {
		RequestContext repaired = repair("소프트웨어학과 지원자들의 지원 동기에서 가장 많이 나온 의견 정리해줘", new RequestContext(
				List.of("소프트웨어학과 지원자", "지원 동기"), "all", "summarize", null, "지원 동기", "가장 많이 나온 의견"));
		assertThat(repaired.conditions()).containsExactly("소프트웨어학과");
		assertThat(repaired.target()).isEqualTo("지원 동기");
		assertThat(repaired.topic()).isNull();
	}

	@Test
	void 주제가_있는데_문항이_아닌_target은_버린다() {
		RequestContext repaired = repair("우주여행에 대한 의견 요약해줘",
				new RequestContext(List.of(), "all", "summarize", null, "우주여행에 대한 의견", "우주여행"));
		assertThat(repaired.target()).isNull();
		assertThat(repaired.topic()).isEqualTo("우주여행");
	}

}
