package com.opinio.passform.domain.analysis;

// SPEC 5절 314행: matched[].kind — option(선택지 일치) / answered·unanswered(그 문항에 답했는지)
public enum MatchKind {
	OPTION, ANSWERED, UNANSWERED
}
