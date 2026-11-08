import { matchPath } from 'react-router-dom';
import { routePatterns } from '../routes/paths';

/**
 * `VITE_USE_API=true`에서도 가짜 값이 남아 있는 라우트. 안내 띠를 이 목록으로 정한다.
 * 화면에서 가짜 값을 모두 걷어 낸 담당자가 그 경로를 뺀다.
 */
const routesWithDemoValues: string[] = [
  routePatterns.myForms,
  routePatterns.myResponses,
  routePatterns.myResponse,
  routePatterns.formExplore,
];

export function showsDemoValues(pathname: string): boolean {
  return routesWithDemoValues.some((pattern) => matchPath(pattern, pathname));
}
