package com.opinio.passform.domain.result;

import java.util.List;
import java.util.UUID;

// GET /forms/{form_id}/responses의 출력(AC19, AC62). 결과 대시보드의 응답별 보기가 쓴다.
// 대상은 결과 대시보드와 같고(모든 버전의 남은 submitted 응답), 순서는 submitted_at 오름차순 → response_id 오름차순이다.
public record ResponseList(List<UUID> responseIds) {

	public ResponseList {
		responseIds = List.copyOf(responseIds);
	}

}
