// 사용자에게 보이는 폼 제목. 앞뒤 공백을 뗀 값이 비면 "제목 없는 폼"으로 보이고, 아니면 원래 값을 그대로 쓴다.
// 저장값·검색·정렬에는 쓰지 않는다(실제 제목 그대로).
export const UNTITLED_FORM = '제목 없는 폼';

export function displayTitle(title: string | null | undefined): string {
  return title?.trim() ? title : UNTITLED_FORM;
}
