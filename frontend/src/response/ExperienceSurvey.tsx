import React from 'react';
import { Button } from '../components/UI';
import type { ExperienceEase, ExperienceRequest } from '../types/api';

/** 작성 용이성 4단계(SPEC 4절 60행). */
const easeOptions: { value: ExperienceEase; label: string }[] = [
  { value: 'very_hard', label: '매우 어려움' },
  { value: 'hard', label: '어려움' },
  { value: 'easy', label: '쉬움' },
  { value: 'very_easy', label: '매우 쉬움' },
];

const IMPROVEMENT_LIMIT = 1000;

type Step = 'open' | 'sending' | 'sent' | 'failed' | 'skipped';

/**
 * 제출 완료 화면의 테스트 기간 한정 사용 경험 평가(SPEC 4절 59~61행, AC95).
 * 두 문항 모두 선택이고, 둘 다 비면 보내지 않는다. 건너뛰면 사라지고 다시 띄우지 않는다.
 * 보내기에 실패해도 제출 완료 표시는 바깥에 그대로 남는다.
 */
export function ExperienceSurvey({
  onSend,
}: {
  onSend: (body: Omit<ExperienceRequest, 'token'>) => Promise<void>;
}) {
  const [step, setStep] = React.useState<Step>('open');
  const [ease, setEase] = React.useState<ExperienceEase | null>(null);
  const [improvement, setImprovement] = React.useState('');
  const text = improvement.trim();
  const empty = ease === null && text === '';

  async function send() {
    if (empty) return;
    setStep('sending');
    try {
      await onSend({ ...(ease ? { ease } : {}), ...(text ? { improvement: text } : {}) });
      setStep('sent');
    } catch {
      setStep('failed');
    }
  }

  if (step === 'skipped') return null;
  if (step === 'sent') {
    return (
      <p className="experience-survey" role="status">
        평가를 보냈어요.
      </p>
    );
  }
  if (step === 'failed') {
    return (
      <div className="experience-survey">
        <p role="alert">평가를 보내지 못했어요.</p>
        <div className="button-row">
          <Button variant="secondary" onClick={() => void send()}>
            다시 보내기
          </Button>
        </div>
      </div>
    );
  }
  const sending = step === 'sending';
  return (
    <div className="experience-survey">
      <div className="question">
        <div className="question-heading">
          <span className="question-label" id="experience-ease">
            이번 폼을 작성하는 과정은 얼마나 쉬웠나요? (선택)
          </span>
        </div>
        <div className="question-input">
          <div className="option-stack" role="radiogroup" aria-labelledby="experience-ease">
            {easeOptions.map((option) => (
              <label
                className={`option ${ease === option.value ? 'checked' : ''}`}
                key={option.value}
              >
                <input
                  type="radio"
                  name="experience-ease"
                  checked={ease === option.value}
                  disabled={sending}
                  onChange={() => setEase(option.value)}
                />
                {option.label}
              </label>
            ))}
          </div>
        </div>
      </div>
      <div className="question">
        <div className="question-heading">
          <label className="question-label" htmlFor="experience-improvement">
            어떤 점을 개선하면 좋을까요? (선택)
          </label>
        </div>
        <div className="question-input">
          <textarea
            id="experience-improvement"
            value={improvement}
            rows={3}
            maxLength={IMPROVEMENT_LIMIT}
            disabled={sending}
            onChange={(e) => setImprovement(e.target.value)}
          />
        </div>
      </div>
      <div className="button-row">
        <Button onClick={() => void send()} disabled={empty || sending}>
          평가 보내기
        </Button>
        <Button variant="ghost" onClick={() => setStep('skipped')} disabled={sending}>
          건너뛰기
        </Button>
      </div>
    </div>
  );
}
