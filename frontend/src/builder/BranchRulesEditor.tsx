import type { BranchCondition, BranchTarget, FormDefinition } from '../types/api';
import {
  addCondition,
  addRule,
  canAddRule,
  canHoldRules,
  conditionCandidates,
  moveRule,
  placeQuestions,
  questionLabel,
  removeCondition,
  removeRule,
  ruleProblems,
  setCondition,
  setRuleMatch,
  setRuleTarget,
  targetCandidates,
  updateRule,
} from './branchRules';
import { RowButton } from './QuestionEditor';

/** select의 값. 화면에는 보이지 않고 고른 항목을 되찾는 데만 쓴다. */
const targetKey = (goTo: BranchTarget) =>
  'submit' in goTo
    ? 'submit'
    : 'section_id' in goTo
      ? `section:${goTo.section_id}`
      : `question:${goTo.question_id}`;

const MISSING = '__missing__';

/**
 * 한 문항의 분기 규칙 목록. 조건 문항·선택지와 이동할 곳은 고를 수 있는 것만 목록에 둔다(SPEC 178행).
 * 편집으로 깨진 규칙은 지우지 않고 문제를 보여 준다. 배포 전 검사가 막는다(사람 결정).
 */
export function BranchRulesEditor({
  definition: def,
  questionId,
  name,
  onChange,
}: {
  definition: FormDefinition;
  questionId: string;
  /** 버튼 이름에 쓰는 문항 이름("질문 3"). */
  name: string;
  onChange: (change: (def: FormDefinition) => FormDefinition) => void;
}) {
  const placed = placeQuestions(def).find((p) => p.question.question_id === questionId);
  if (!placed) return null;
  const rules = placed.question.branch_rules;
  const holdable = canHoldRules(def, placed);
  const candidates = conditionCandidates(def, questionId);
  const targets = targetCandidates(def, questionId);
  const byId = new Map(placeQuestions(def).map((p) => [p.question.question_id, p]));

  return (
    <div className="builder-field">
      <span>분기 규칙 (위에서부터 검사해 처음 맞는 규칙으로 이동해요)</span>
      {rules.map((rule, ruleIndex) => {
        const ruleName = `${name} 분기 ${ruleIndex + 1}`;
        const problems = ruleProblems(def, questionId, rule);
        const edit = (change: Parameters<typeof updateRule>[3]) =>
          onChange((d) => updateRule(d, questionId, ruleIndex, change));
        const currentTarget = targets.find((t) => targetKey(t.go_to) === targetKey(rule.go_to));
        return (
          <div className="builder-question" key={ruleIndex}>
            <div className="button-row">
              <strong>{ruleIndex + 1}번째 규칙</strong>
              <RowButton
                label={`${ruleName} 위로`}
                text="위로"
                disabled={ruleIndex === 0}
                onClick={() => onChange((d) => moveRule(d, questionId, ruleIndex, -1))}
              />
              <RowButton
                label={`${ruleName} 아래로`}
                text="아래로"
                disabled={ruleIndex === rules.length - 1}
                onClick={() => onChange((d) => moveRule(d, questionId, ruleIndex, 1))}
              />
              <RowButton
                label={`${ruleName} 삭제`}
                text="삭제"
                onClick={() => onChange((d) => removeRule(d, questionId, ruleIndex))}
              />
            </div>
            <label className="builder-field">
              <span>조건 결합</span>
              <select
                aria-label={`${ruleName} 조건 결합`}
                value={rule.when.match}
                onChange={(e) => edit((r) => setRuleMatch(r, e.target.value as 'all' | 'any'))}
              >
                <option value="all">모두 만족</option>
                <option value="any">하나라도 만족</option>
              </select>
            </label>
            {rule.when.conditions.map((condition, conditionIndex) => {
              const conditionName = `${ruleName} 조건 ${conditionIndex + 1}`;
              const source = byId.get(condition.question_id);
              const sourceListed = candidates.some(
                (c) => c.question.question_id === condition.question_id,
              );
              const options = source?.question.options ?? [];
              const set = (next: BranchCondition) =>
                edit((r) => setCondition(r, conditionIndex, next));
              return (
                <div className="builder-option" key={conditionIndex}>
                  <select
                    aria-label={`${conditionName} 문항`}
                    value={sourceListed ? condition.question_id : MISSING}
                    onChange={(e) => {
                      const picked = byId.get(e.target.value);
                      if (picked) {
                        set({
                          question_id: picked.question.question_id,
                          option: picked.question.options[0] ?? '',
                        });
                      }
                    }}
                  >
                    {!sourceListed && (
                      <option value={MISSING} disabled>
                        {source ? `${questionLabel(source)} (고를 수 없음)` : '없는 문항'}
                      </option>
                    )}
                    {candidates.map((c) => (
                      <option key={c.question.question_id} value={c.question.question_id}>
                        {questionLabel(c)}
                      </option>
                    ))}
                  </select>
                  <span>답이</span>
                  <select
                    aria-label={`${conditionName} 선택지`}
                    value={options.includes(condition.option) ? condition.option : MISSING}
                    onChange={(e) => set({ ...condition, option: e.target.value })}
                  >
                    {!options.includes(condition.option) && (
                      <option value={MISSING} disabled>
                        {condition.option ? `${condition.option} (없는 선택지)` : '없는 선택지'}
                      </option>
                    )}
                    {[...new Set(options)].map((option) => (
                      <option key={option} value={option}>
                        {option || '(빈 선택지)'}
                      </option>
                    ))}
                  </select>
                  {source?.question.question_type === 'checkbox' && <span>포함</span>}
                  <RowButton
                    label={`${conditionName} 삭제`}
                    text="삭제"
                    disabled={rule.when.conditions.length <= 1}
                    onClick={() => edit((r) => removeCondition(r, conditionIndex))}
                  />
                </div>
              );
            })}
            <div className="button-row">
              <RowButton
                label={`${ruleName} 조건 추가`}
                text="조건 추가"
                disabled={candidates.length === 0}
                onClick={() => onChange((d) => addCondition(d, questionId, ruleIndex))}
              />
            </div>
            <label className="builder-field">
              <span>이동할 곳</span>
              <select
                aria-label={`${ruleName} 이동할 곳`}
                value={currentTarget ? targetKey(currentTarget.go_to) : MISSING}
                onChange={(e) => {
                  const picked = targets.find((t) => targetKey(t.go_to) === e.target.value);
                  if (picked) edit((r) => setRuleTarget(r, picked.go_to));
                }}
              >
                {!currentTarget && (
                  <option value={MISSING} disabled>
                    고를 수 없는 곳
                  </option>
                )}
                {targets.map((t) => (
                  <option key={targetKey(t.go_to)} value={targetKey(t.go_to)}>
                    {t.label}
                  </option>
                ))}
              </select>
            </label>
            {problems.length > 0 && (
              <div className="error">
                {problems.map((problem) => (
                  <p key={problem}>{problem}</p>
                ))}
              </div>
            )}
          </div>
        );
      })}
      {holdable ? (
        <div className="button-row">
          <RowButton
            label={`${name} 분기 규칙 추가`}
            text="분기 규칙 추가"
            disabled={!canAddRule(def, questionId)}
            onClick={() => onChange((d) => addRule(d, questionId))}
          />
          {!canAddRule(def, questionId) && (
            <small className="muted">
              분기 조건은 선택형 문항으로 만들어요. 이 문항이나 앞쪽에 선택형 문항을 먼저 두세요.
            </small>
          )}
        </div>
      ) : (
        <small className="muted">
          섹션별 페이지에서는 섹션의 마지막 문항에만 분기를 둘 수 있어요.
        </small>
      )}
    </div>
  );
}
