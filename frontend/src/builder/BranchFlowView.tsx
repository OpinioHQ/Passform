import React from 'react';
import { Button, Panel } from '../components/UI';
import type { BranchGraph, BranchGraphEdge, FormDefinition } from '../types/api';
import { placeQuestions, questionLabel, sectionLabel } from './branchRules';

/** 서버가 준 분기 그래프와, 같은 때 저장된 폼 정의. 문항은 ID가 아니라 문구로 보인다(AGENTS 5절). */
export interface BranchFlowData {
  graph: BranchGraph;
  definition: FormDefinition;
}

type Load = { state: 'loading' } | { state: 'ready'; data: BranchFlowData } | { state: 'failed' };

/** 노드 id를 사람이 읽는 이름으로 바꾼다. 저장된 정의에 없는 대상(미완성 초안)은 그렇게 알린다. */
function nodeNames(definition: FormDefinition): (id: string) => string {
  const names = new Map<string, string>();
  definition.sections.forEach((section, index) =>
    names.set(section.section_id, `${sectionLabel(definition, index)} (섹션 처음)`),
  );
  for (const placed of placeQuestions(definition)) {
    names.set(placed.question.question_id, questionLabel(placed));
  }
  return (id) => (id === 'submit' ? '제출' : (names.get(id) ?? '찾을 수 없는 대상'));
}

function conditionText(definition: FormDefinition, questionId: string, index: number): string {
  const owner = placeQuestions(definition).find((p) => p.question.question_id === questionId);
  const rule = owner?.question.branch_rules[index];
  if (!rule) return '';
  const notes = new Map(
    placeQuestions(definition).map((p) => [
      p.question.question_id,
      p.question.question_note.trim(),
    ]),
  );
  const parts = rule.when.conditions.map(
    (c) => `"${notes.get(c.question_id) || '문구 없는 문항'}"에서 "${c.option}" 선택`,
  );
  return parts.join(rule.when.match === 'all' ? ' 그리고 ' : ' 또는 ');
}

/** AC45: 모든 규칙 이동과 기본 이동을 문항마다 보여 준다. 그래프 라이브러리 없이 목록과 화살표 글자로 그린다(사람 결정). */
export function BranchFlowView({
  load,
  onClose,
}: {
  load: () => Promise<BranchFlowData>;
  onClose: () => void;
}) {
  const [result, setResult] = React.useState<Load>({ state: 'loading' });
  React.useEffect(() => {
    let active = true;
    load()
      .then((data) => {
        if (active) setResult({ state: 'ready', data });
      })
      .catch(() => {
        if (active) setResult({ state: 'failed' });
      });
    return () => {
      active = false;
    };
  }, [load]);

  return (
    <Panel className="builder-section branch-flow">
      <section aria-label="분기 흐름">
        <div className="section-title">
          <h2>분기 흐름</h2>
          <Button variant="secondary" onClick={onClose}>
            닫기
          </Button>
        </div>
        <p className="small muted">저장된 내용 기준이에요. 저장하지 않은 편집은 반영되지 않아요.</p>
        {result.state === 'loading' && <p className="muted">분기 흐름을 불러오는 중이에요.</p>}
        {result.state === 'failed' && (
          <p className="error" role="alert">
            분기 흐름을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.
          </p>
        )}
        {result.state === 'ready' && <FlowList data={result.data} />}
      </section>
    </Panel>
  );
}

function FlowList({ data: { graph, definition } }: { data: BranchFlowData }) {
  const name = nodeNames(definition);
  const edgesFrom = new Map<string, BranchGraphEdge[]>();
  for (const edge of graph.edges) {
    edgesFrom.set(edge.from, [...(edgesFrom.get(edge.from) ?? []), edge]);
  }
  // 섹션 노드 아래에 그 섹션의 문항 노드를 모은다. 노드는 서버가 섹션 → 그 문항 순서로 준다.
  const groups: { sectionId: string; questionIds: string[] }[] = [];
  for (const node of graph.nodes) {
    if (node.type === 'section') groups.push({ sectionId: node.id, questionIds: [] });
    else if (node.type === 'question') groups[groups.length - 1]?.questionIds.push(node.id);
  }
  const sectionTitle = (id: string) => {
    const index = definition.sections.findIndex((s) => s.section_id === id);
    return index < 0 ? '섹션' : sectionLabel(definition, index);
  };

  return (
    <ol className="branch-flow-list">
      {groups.map((group) => (
        <li key={group.sectionId}>
          <strong>{sectionTitle(group.sectionId)}</strong>
          {group.questionIds.length === 0 && <p className="small muted">문항이 없는 섹션이에요.</p>}
          <ul>
            {group.questionIds.map((questionId) => (
              <li key={questionId}>
                <span>{name(questionId)}</span>
                <ul className="branch-flow-edges">
                  {(edgesFrom.get(questionId) ?? []).map((edge) =>
                    edge.kind === 'rule' ? (
                      <li key={`rule-${edge.rule_index ?? 0}`} className="branch-flow-rule">
                        ↳ 분기 {(edge.rule_index ?? 0) + 1}:{' '}
                        {conditionText(definition, questionId, edge.rule_index ?? 0)} →{' '}
                        {name(edge.to)}
                      </li>
                    ) : (
                      <li key="default" className="branch-flow-default">
                        ↓ 기본 다음 → {name(edge.to)}
                      </li>
                    ),
                  )}
                </ul>
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ol>
  );
}
