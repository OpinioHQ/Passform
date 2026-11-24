/**
 * 화면 문구는 "폼 버전 1"로 쓴다(AGENTS 5절).
 * `ResultsPage`와 `ListResult`가 함께 쓰므로 순환 import가 없게 따로 둔다. `ResultsPage`에서 다시 내보낸다.
 */
export function versionLabel(versions: number[]): string {
  return `폼 버전 ${versions.join('·')}`;
}
