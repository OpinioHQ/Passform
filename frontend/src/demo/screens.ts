import { demoForm } from './data';

/**
 * 화면에 표시하는 가상 값. 실제 수치나 실제 사용자가 아니다.
 * 화면을 서버 데이터로 바꾼 담당자가 여기서 해당 값을 지운다.
 */
export const demoUser = {
  name: '김지윤',
  initial: '지',
  email: 'jiyun@atelier.team',
  workspace: '데모 워크스페이스',
  profileLabel: '업무용 프로필',
};

export const demoInbox = {
  owners: ['아틀리에 · 피플팀', '아틀리에 · 피플팀', '아틀리에 · 운영팀', '아틀리에 · 컬처팀'],
  fallbackOwner: 'Passform 데모팀',
  closingSoonCount: '2',
  submittedCount: '8',
  draftDeadlineBadge: '마감 임박 · D-2',
  estimatedEffort: '약 3분 · 6문항',
  draftProgress: { label: '3 / 5문항 작성', percent: '60%', savedAt: '오늘 10:32 저장됨' },
};

export const demoResponseScreen = {
  longAnswerPlaceholder: '작은 제안도 좋아요. 다음 달에 함께 바꿔갈 점을 알려주세요.',
  autofillAnswers: { q1: demoUser.name, q2: demoUser.email },
  autofillRows: [
    { label: '이름', value: demoUser.name },
    { label: '이메일', value: demoUser.email },
  ],
};

export const demoHistory = {
  latestTitle: demoForm.title,
  otherTitle: '팀 경험 설문 · 데모 응답',
};

export const demoCreated = {
  title: demoForm.title,
  description: '함께 일하는 경험에 대한 설문',
  statusBadge: '배포 중 · 폼 버전 1',
  responseCount: '6',
  statusLabel: '열림',
};

export const demoBuilder = {
  statusBadge: demoCreated.statusBadge,
  responsesButton: `응답 ${demoCreated.responseCount}`,
  sectionTitle: '섹션 01 · 함께 일하는 경험',
};

export const demoExplore = {
  description: `${demoForm.title} · 가상 데이터 결과 탐색`,
  statusBadge: demoCreated.statusLabel,
  versionCount: '1',
  versionNote: '데모 폼 버전 1',
  columns: ['이름', '소속 팀', '협업 경험', '제출일시', '바라는 점'],
  questionIds: { name: 'q1', team: 'q3', experience: 'q4', wish: 'q5' },
  satisfiedAnswer: '만족해요',
  team: '프로덕트디자인',
  queryPattern: /만족|프로덕트디자인/,
  filterChip: '팀: 프로덕트디자인 · 협업: 만족해요 ×',
  exampleQuestion: '프로덕트디자인팀 중 ‘만족해요’ 응답만 보여줘.',
  matchedMessage:
    '데모 응답에서 ‘프로덕트디자인’·‘만족해요’를 선택한 응답만 보여 줍니다. 실제 자연어 해석은 서버 분석 API를 연결해야 합니다.',
  unknownMessage:
    '이 요청은 데모 모드에서 판단할 수 없습니다. 실제 서비스에서는 서버 분석 API의 결과나 되묻기로 답합니다.',
  csvFilename: 'passform_demo.csv',
};
