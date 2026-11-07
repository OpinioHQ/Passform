/**
 * API 타입 모음. 필드 이름은 백엔드 DTO와 같은 snake_case를 쓰고, 검증은 서버가 판정한다.
 * 정본: 폼은 `form.schema.json`, 분석은 `analyze_response.schema.json`, 나머지는 `docs/SPEC.md` 5절.
 * 화면 전용 축약 타입을 만들면 `...ViewModel`로 이름을 구분해 이곳 밖에 둔다.
 */
export * from './account';
export * from './form';
export * from './response';
export * from './analyze';
export * from './results';
