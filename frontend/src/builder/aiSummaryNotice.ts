import type { AiSummaryNotice } from '../types/api';

/**
 * 미리보기에만 쓰는 AI 요약 외부 전송 고지. 정본은 서버 ConsentNotices, SPEC 211행이다.
 * 배포된 폼은 서버가 첫 배포 때 기록한 값을 공개 조회로 받으므로 이 상수를 쓰지 않는다.
 */
export const AI_SUMMARY_NOTICE: AiSummaryNotice = {
  provider: 'OpenAI',
  processing_country: '미국(프로젝트 데이터 저장 위치 설정 확인 후 확정)',
  items: ['AI 요약 대상 주관식 응답 내용(개인정보 문항 제외, 연락처 마스킹)'],
  purpose: '제작자의 응답 요약·주제 분류',
  retention:
    '제공사 정책상 남용 감시 로그를 최대 30일 보관(법적 요구가 있으면 더 길어질 수 있음). 기본 설정에서 학습에 사용하지 않음',
  refusal_effect: '제출할 수 있으며, 내 응답은 AI 요약에서만 제외된다',
  checked_on: '2026-10-28',
};
