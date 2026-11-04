import type { AnalysisResponse, Matched } from '../api/types';

// 제작자 화면의 문구. "판정 불가", q1 같은 내부 용어를 쓰지 않는다. API의 coverage·matched는 그대로 둔다.

function nameOf(entry: Matched): string {
  return entry.question_note ?? '해당 문항';
}

export function describeMatch(entry: Matched): string {
  if (entry.kind === 'answered') {
    return `${nameOf(entry)}에 답함`;
  }
  if (entry.kind === 'unanswered') {
    return `${nameOf(entry)}에 답하지 않음`;
  }
  return `${nameOf(entry)}: ${entry.option ?? ''}`;
}

// 숫자 위에 보여 줄 해석. 원문 표현 대신 실제로 연결된 문항과 선택지를 보여 준다.
export function interpretedConditions(conditions: string[], matched: Matched[]): string[] {
  const described = conditions.flatMap((condition) => {
    const entries = matched.filter((entry) => entry.condition === condition);
    return entries.length > 0 ? entries.map(describeMatch) : [condition];
  });
  return [...new Set(described)];
}

// 조건별 근거: 어떤 표현이 어느 문항·버전에 연결됐는가
export function evidenceLines(matched: Matched[]): string[] {
  const groups = new Map<string, { condition: string; description: string; versions: number[] }>();
  for (const entry of matched) {
    const description = describeMatch(entry);
    const key = `${entry.condition}\u0000${description}`;
    const group = groups.get(key) ?? { condition: entry.condition, description, versions: [] };
    group.versions.push(entry.form_version);
    groups.set(key, group);
  }
  return [...groups.values()].map(
    (group) => `"${group.condition}" → ${group.description} (폼 버전 ${group.versions.join('·')})`,
  );
}

function notesOf(conditions: string[], matched: Matched[]): string {
  const notes = conditions.map((condition) => {
    const entry = matched.find((candidate) => candidate.condition === condition);
    return entry?.question_note ?? condition;
  });
  return [...new Set(notes)].map((note) => `"${note}"`).join(', ');
}

// 물음표를 눌러야 보이는 설명. 숫자에 넣지 않은 응답과 그 이유를 문장으로 쓴다.
export function resultNotes(response: AnalysisResponse): string[] {
  const { coverage, result } = response;
  const matched = response.matched ?? [];
  if (!coverage) {
    return [];
  }
  const notes: string[] = [];
  for (const version of coverage.not_evaluable_by_version) {
    const reached = version.unavailable_conditions.filter((condition) =>
      matched.some((entry) => entry.condition === condition && entry.form_version === version.form_version),
    );
    const missing = version.unavailable_conditions.filter((condition) => !reached.includes(condition));
    const label = `폼 버전 ${version.form_version}의 응답 ${version.response_count}건`;
    if (reached.length === 0) {
      notes.push(`${label}은 ${notesOf(missing, matched)} 문항이 없던 때 받은 것이라 세지 않았어요.`);
    } else if (missing.length === 0) {
      notes.push(`${label}은 앞선 답에 따라 ${notesOf(reached, matched)} 문항을 건너뛰어 세지 않았어요.`);
    } else {
      notes.push(`${label}은 ${notesOf(version.unavailable_conditions, matched)} 문항이 없거나 건너뛰어 세지 않았어요.`);
    }
  }
  for (const version of coverage.sort_unavailable_by_version ?? []) {
    notes.push(`폼 버전 ${version}에는 정렬 기준 문항이 없어 맨 뒤에 두었어요.`);
  }
  for (const version of coverage.summary_unavailable_by_version ?? []) {
    notes.push(`폼 버전 ${version.form_version}의 응답 ${version.response_count}건은 이 문항이 없어 요약에서 빠졌어요.`);
  }
  const excluded = result?.summary?.excluded_no_consent_count ?? 0;
  if (excluded > 0) {
    notes.push(`AI 요약에 동의하지 않은 ${excluded}건은 요약에서 빠졌어요. 건수에는 포함돼요.`);
  }
  return notes;
}

// 일부만 확인했다는 사실은 설명을 열지 않아도 알 수 있어야 한다(AC78, 절대 규칙 3).
export function countCaption(response: AnalysisResponse): string {
  const { coverage, context } = response;
  if (!coverage) {
    return '';
  }
  if (coverage.not_evaluable_count > 0) {
    const checked = coverage.total_submitted_count - coverage.not_evaluable_count;
    return `전체 ${coverage.total_submitted_count}건 중 확인할 수 있는 ${checked}건에서 찾았어요`;
  }
  return context && context.conditions.length > 0
    ? `전체 ${coverage.total_submitted_count}건 중 조건에 맞는 응답이에요`
    : '받은 응답 전체예요';
}
