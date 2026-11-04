// 스키마 초안을 검증할 케이스와 예시 파일을 만든다. 저장소 루트에서 실행한다: node _local/MVP_사전정의_2026-11-04/검증/build_cases.mjs
// 양성은 data/seed, 시험 데모 시드(feat/demo), SPEC 예시, 시험 데모 실제 응답이다. 음성은 양성을 한 군데씩 망가뜨린다.
import { execSync } from 'node:child_process';
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';

const DIR = '_local/MVP_사전정의_2026-11-04';
const clone = (value) => JSON.parse(JSON.stringify(value));
const readSeed = (path) => JSON.parse(readFileSync(path, 'utf8'));
const readDemoSeed = (name) => JSON.parse(execSync(`git show feat/demo:data/demo/forms/${name}`, { encoding: 'utf8', maxBuffer: 1 << 26 }));

const seeds = [
  readSeed('data/seed/forms/form_a_versions.json'),
  readSeed('data/seed/forms/form_b_mixed.json'),
  readDemoSeed('form_club_recruit.json'),
  readDemoSeed('form_event_signup.json'),
];

// ---- form.schema.json
const formCases = [];
for (const seed of seeds) {
  for (const version of seed.form_versions) {
    formCases.push({ name: `시드 ${seed.form_id} v${version.form_version}`, expect: 'pass', instance: version.form });
  }
}
const specExample = {
  title: '', note: '', deadline: '2026-11-01T23:59:00+09:00',
  settings: { editable: false, cancellable: false, display: { default_mode: 'section_page' }, ai_summary: { enabled: false } },
  payment_link: null, consent_template_id: null, note_attachments: [],
  sections: [{ section_id: 's1', title: '기본 정보', questions: [{
    question_id: 'q1', template_id: null, question_note: '학과', question_type: 'dropdown',
    options: ['소프트웨어학과', '컴퓨터공학과'], required: true, profile_key: 'department', attachments: [],
    branch_rules: [{ when: { match: 'all', conditions: [{ question_id: 'q1', option: '소프트웨어학과' }] }, go_to: { section_id: 's3' } }],
  }] }],
};
formCases.push({ name: 'SPEC 128행 초안 예시', expect: 'pass', instance: specExample });
formCases.push({ name: '갓 만든 빈 초안', expect: 'pass', instance: { title: '', note: '', settings: { editable: false, cancellable: false, display: { default_mode: 'all_questions' } }, payment_link: null, consent_template_id: null, note_attachments: [], sections: [] } });

const question = (extra) => ({ question_id: 'q1', template_id: null, question_note: '문항', question_type: 'short_answer', options: [], required: false, profile_key: null, attachments: [], branch_rules: [], ...extra });
const withQuestion = (q) => ({ ...clone(specExample), sections: [{ section_id: 's1', title: 's', questions: [q] }] });
const rule = (goTo, when = { match: 'all', conditions: [{ question_id: 'q1', option: '예' }] }) => question({ question_type: 'multiple_choice', options: ['예'], branch_rules: [{ when, go_to: goTo }] });
for (const [name, q] of [
  ['단답 + 입력 형식 + 글자 수', question({ input_format: { kind: 'mobile_phone', placeholder: '010' }, character_limit: 20 })],
  ['장문 + 글자 수', question({ question_type: 'long_answer', character_limit: 500 })],
  ['go_to 문항', rule({ question_id: 'q2' })],
  ['go_to 제출', rule({ submit: true })],
  ['when any', rule({ section_id: 's2' }, { match: 'any', conditions: [{ question_id: 'q1', option: '예' }] })],
]) formCases.push({ name, expect: 'pass', instance: withQuestion(q) });

for (const [name, q] of [
  ['문항 유형 밖의 값', question({ question_type: 'rating' })],
  ['입력 형식 밖의 값', question({ input_format: { kind: 'resident_number' } })],
  ['profile_key 닫힌 목록 밖', question({ profile_key: 'phone' })],
  ['단답에 선택지', question({ options: ['a'] })],
  ['장문에 입력 형식', question({ question_type: 'long_answer', input_format: { kind: 'name' } })],
  ['선택형에 글자 수', question({ question_type: 'checkbox', options: ['a'], character_limit: 5 })],
  ['중복 선택지', question({ question_type: 'dropdown', options: ['a', 'a'] })],
  ['글자 수 0', question({ character_limit: 0 })],
  ['문항에 모르는 키', question({ section: 's1' })],
  ['required 없음', (() => { const q = question({}); delete q.required; return q; })()],
  ['go_to 두 개', rule({ section_id: 's2', submit: true })],
  ['go_to 비어 있음', rule({})],
  ['go_to 문자열', rule('submit')],
  ['submit false', rule({ submit: false })],
  ['when 조건 없음', rule({ submit: true }, { match: 'all', conditions: [] })],
  ['when match 잘못됨', rule({ submit: true }, { match: 'not', conditions: [{ question_id: 'q1', option: '예' }] })],
]) formCases.push({ name, expect: 'fail', instance: withQuestion(q) });

const mutate = (fn) => { const form = clone(specExample); fn(form); return form; };
for (const [name, form] of [
  ['http 송금 링크', mutate((f) => { f.payment_link = 'http://pay.example.com'; })],
  ['마감이 날짜 형식 아님', mutate((f) => { f.deadline = '내일까지'; })],
  ['마감에 타임존 없음', mutate((f) => { f.deadline = '2026-11-01T23:59:00'; })],
  ['최상위 status 혼입', mutate((f) => { f.status = 'draft'; })],
  ['보기 방식 밖의 값', mutate((f) => { f.settings.display.default_mode = 'page'; })],
  ['선착순 0', mutate((f) => { f.settings.response_limit = 0; })],
  ['ai_summary enabled 없음', mutate((f) => { f.settings.ai_summary = {}; })],
  ['sections 없음', mutate((f) => { delete f.sections; })],
  ['첨부 size 없음', mutate((f) => { f.note_attachments = [{ attachment_id: 'a', filename: 'f', content_type: 'image/png' }]; })],
]) formCases.push({ name, expect: 'fail', instance: form });

// ---- form_question_info
const PERSONAL = new Set(['name', 'mobile_phone', 'landline_phone', 'address', 'student_id', 'email']);
const isPersonal = (q) => q.profile_key != null || PERSONAL.has(q.input_format?.kind);
const flatten = (form) => form.sections.flatMap((section) => section.questions);
function questionInfo(seed) {
  const submitted = new Set(seed.responses.filter((r) => r.status === 'submitted').map((r) => r.form_version));
  const latest = seed.form_versions.at(-1);
  const latestNumbers = new Map(flatten(latest.form).map((q, i) => [q.question_id, i + 1]));
  const versions = seed.form_versions.filter((v) => submitted.has(v.form_version));
  return {
    form_id: seed.form_id,
    title: latest.form.title,
    latest_version: latest.form_version,
    versions: (versions.length ? versions : [latest]).map((v) => ({
      form_version: v.form_version,
      questions: flatten(v.form).map((q, i) => ({
        number: i + 1,
        latest_number: latestNumbers.get(q.question_id) ?? null,
        question_id: q.question_id,
        question_note: q.question_note,
        question_type: q.question_type,
        options: q.options,
        is_personal: isPersonal(q),
      })),
    })),
  };
}
mkdirSync(`${DIR}/examples/form_question_info`, { recursive: true });
const infoCases = [];
for (const seed of seeds) {
  const info = questionInfo(seed);
  writeFileSync(`${DIR}/examples/form_question_info/${seed.form_id}.json`, `${JSON.stringify(info, null, 2)}\n`);
  infoCases.push({ name: `시드 ${seed.form_id}`, expect: 'pass', instance: info });
}
const base = infoCases[1].instance;
const mutateInfo = (fn) => { const info = clone(base); fn(info); return info; };
infoCases.push({ name: 'is_personal 없음', expect: 'fail', instance: mutateInfo((i) => { delete i.versions[0].questions[0].is_personal; }) });
infoCases.push({ name: 'latest_number 문자열', expect: 'fail', instance: mutateInfo((i) => { i.versions[0].questions[0].latest_number = '1'; }) });
infoCases.push({ name: '응답 값 혼입', expect: 'fail', instance: mutateInfo((i) => { i.versions[0].questions[0].answers = ['값']; }) });
infoCases.push({ name: '버전 없음', expect: 'fail', instance: mutateInfo((i) => { i.versions = []; }) });

// ---- analyze_response
const analyzeDir = `${DIR}/examples/analyze`;
const specMatched = (version) => ({ condition: '소프트웨어학과', form_version: version, question_id: 'q1', question_note: '소속 학과', kind: 'option', option: '소프트웨어학과' });
const specCoverage = { total_submitted_count: 9, not_evaluable_count: 4, not_evaluable_by_version: [{ form_version: 3, response_count: 4, unavailable_conditions: ['소프트웨어학과'] }] };
const specList = {
  status: 'ok', context: { conditions: ['소프트웨어학과'], operation: 'list' }, matched: [specMatched(1), specMatched(2)], coverage: specCoverage,
  result: { operation: 'list', count: 5, response_ids: ['r01', 'r02', 'r03', 'r04', 'r05'], summary: null }, clarify: null,
};
const specSummarize = {
  status: 'ok', context: { conditions: ['소프트웨어학과'], operation: 'summarize' }, matched: [specMatched(1), specMatched(2)], coverage: specCoverage,
  result: {
    operation: 'summarize', count: 5, response_ids: ['r01', 'r02', 'r03', 'r04', 'r05'],
    summary: {
      status: 'ok', excluded_no_consent_count: 2,
      items: [
        { type: 'themes', target: { question_id: 'q4', question_note: '지원 동기', form_versions: [1, 2] }, themes: [{ label: '실전 프로젝트 경험', count: 3, response_ids: ['r01', 'r03', 'r05'], text: '팀 프로젝트로 실제 서비스를 만들어 보고 싶다는 내용이 중심이다.', evidence: [{ response_id: 'r03', form_version: 2, question_id: 'q4', excerpt: '팀 프로젝트로 실제 서비스를 배포해 보고 싶어서 지원했습니다.' }] }] },
        { type: 'option_stats', target: { question_id: 'q2', question_note: '지원 분야', form_versions: [1, 2] }, options: [{ label: '백엔드', count: 4, form_versions: [1, 2] }, { label: '기획', count: 1, form_versions: [1, 2] }] },
      ],
      topic: null,
    },
  },
  clarify: null,
};
const parseFailed = { status: 'clarify', context: null, matched: null, coverage: null, result: null, clarify: { reason: 'parse_failed', message: '요청을 이해하지 못했어요. 다른 말로 다시 알려 주세요.', options: [] } };

const examples = {};
const analyzeCases = [
  { name: 'SPEC 249행 list 예시(question_note 추가)', expect: 'pass', instance: specList },
  { name: 'SPEC 264행 summarize 예시(question_note 추가)', expect: 'pass', instance: specSummarize },
  { name: 'parse_failed(직접 작성)', expect: 'pass', instance: parseFailed },
];
examples.spec_list = { request: { context: specList.context }, http_status: 200, response: specList, source: 'SPEC 5절 249행 + question_note' };
examples.spec_summarize = { request: { context: specSummarize.context }, http_status: 200, response: specSummarize, source: 'SPEC 5절 264행 + question_note' };
examples.clarify_2_parse_failed = { request: { query: '(형식 오류가 두 번 난 요청)' }, http_status: 200, response: parseFailed, source: '직접 작성(시험 데모에서 재현 못 함)' };

for (const file of readdirSync(analyzeDir).sort()) {
  const captured = JSON.parse(readFileSync(`${analyzeDir}/${file}`, 'utf8'));
  const name = file.replace('.json', '');
  const { form_id: formId, ...request } = captured._request;
  let response = captured.response;
  // 시험 데모는 null인 키를 빼서 보낸다. 계약은 operation 키를 둔다(결정 A2). 원본은 어긋남을 확인하고 예시에는 고친 값을 쓴다.
  if (response?.status === 'clarify' && response.context && !('operation' in response.context)) {
    analyzeCases.push({ name: `시험 데모 원본 ${name}(operation 키 없음)`, expect: 'fail', instance: response });
    response = { ...response, context: { ...response.context, operation: null } };
  }
  if (captured._http_status === 200) {
    analyzeCases.push({ name: `실제 응답 ${name}`, expect: 'pass', instance: response });
  }
  examples[name] = { form_id: formId, request, http_status: captured._http_status, response, source: '시험 데모 실제 응답(feat/demo d6cb1b9)' };
}
// 요약이 되묻기가 아니라 상태로 끝나는 경우. AC93 테스트 문구대로 직접 쓴 값이다.
const summaryState = (status, count, ids, excluded) => ({
  status: 'ok', context: { conditions: ['소프트웨어학과'], operation: 'summarize' }, matched: [specMatched(1)],
  coverage: { total_submitted_count: 9, not_evaluable_count: 0, not_evaluable_by_version: [] },
  result: { operation: 'summarize', count, response_ids: ids, summary: { status, excluded_no_consent_count: excluded, items: [], topic: null } },
  clarify: null,
});
for (const [name, body] of [
  ['ok_summary_no_matches', summaryState('no_matches', 0, [], 0)],
  ['ok_summary_no_consented_responses', summaryState('no_consented_responses', 2, ['r01', 'r02'], 2)],
  ['ok_summary_too_many_responses', summaryState('too_many_responses', 4, ['r01', 'r02', 'r03', 'r04'], 0)],
]) {
  analyzeCases.push({ name: `직접 작성 ${name}`, expect: 'pass', instance: body });
  examples[name] = { request: { context: body.context }, http_status: 200, response: body, source: '직접 작성(AC93 테스트 문구)' };
}
const mutateOk = (fn) => { const body = clone(specList); fn(body); return body; };
const mutateClarify = (reason, options) => ({ ...clone(parseFailed), clarify: { reason, message: 'm', options } });
const question0 = { form_version: 1, question_id: 'q1', question_note: 'n' };
for (const [name, body] of [
  ['ok인데 clarify 있음', mutateOk((b) => { b.clarify = parseFailed.clarify; })],
  ['ok인데 result 없음', mutateOk((b) => { b.result = null; })],
  ['clarify인데 result 있음', { ...clone(parseFailed), result: specList.result }],
  ['count인데 ID 목록', mutateOk((b) => { b.result.operation = 'count'; })],
  ['list인데 ID 없음', mutateOk((b) => { b.result.response_ids = null; })],
  ['summarize인데 summary 없음', mutateOk((b) => { b.result.operation = 'summarize'; })],
  ['matched에 문구 없음', mutateOk((b) => { delete b.matched[0].question_note; })],
  ['option인데 선택지 없음', mutateOk((b) => { b.matched[0].option = null; })],
  ['answered인데 선택지 있음', mutateOk((b) => { b.matched[0].kind = 'answered'; })],
  ['coverage 키 누락', mutateOk((b) => { delete b.coverage.not_evaluable_count; })],
  ['context에 버전 범위 혼입', mutateOk((b) => { b.context.form_versions = [1]; })],
  ['모르는 사유', mutateClarify('unknown_reason', [])],
  ['parse_failed에 선택지', mutateClarify('parse_failed', ['all'])],
  ['missing_operation에 statistics', mutateClarify('missing_operation', ['count', 'statistics'])],
  ['summary_disabled에 summarize', mutateClarify('summary_disabled', ['count', 'summarize'])],
  ['missing_match에 not', mutateClarify('missing_match', ['not'])],
  ['unknown_condition에 문자열', mutateClarify('unknown_condition', ['소프트웨어학과'])],
  ['ambiguous_match 후보 없음', mutateClarify('ambiguous_match', [])],
  ['ambiguous_match에 all', mutateClarify('ambiguous_match', ['all'])],
  ['double_negation 두 문장', mutateClarify('double_negation', ['a', 'b'])],
  ['unsortable_field 후보에 문항 ID 없음', mutateClarify('unsortable_field', [{ form_version: 1, question_note: 'n' }])],
  ['주제에 evidence 없음', (() => { const b = clone(specSummarize); delete b.result.summary.items[0].themes[0].evidence; return b; })()],
  ['topic에 count 혼입', (() => { const b = clone(specSummarize); b.result.summary.topic = { query: 'q', text: null, evidence: [], count: 3 }; return b; })()],
]) analyzeCases.push({ name, expect: 'fail', instance: body });
for (const [name, options] of [['ambiguous any', ['any']], ['ambiguous 후보', [question0]], ['혼합 부정 빈 선택지', []]]) {
  analyzeCases.push({ name, expect: 'pass', instance: mutateClarify(name.startsWith('혼합') ? 'missing_match' : 'ambiguous_match', options) });
}

mkdirSync(`${DIR}/검증/cases`, { recursive: true });
writeFileSync(`${DIR}/검증/cases/form.json`, JSON.stringify(formCases));
writeFileSync(`${DIR}/검증/cases/info.json`, JSON.stringify(infoCases));
writeFileSync(`${DIR}/검증/cases/analyze.json`, JSON.stringify(analyzeCases));
writeFileSync(`${DIR}/examples/analyze_examples.json`, `${JSON.stringify(examples, null, 2)}\n`);
console.log(`케이스: form ${formCases.length}, info ${infoCases.length}, analyze ${analyzeCases.length} / 예시 ${Object.keys(examples).length}개`);
