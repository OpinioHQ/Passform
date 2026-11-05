import { Heading } from '../components/UI';

/** 라우트만 잡아 둔 화면. 담당 이슈에서 실제 화면으로 바꾼다. */
export function PlaceholderPage({
  eyebrow,
  title,
  issue,
}: {
  eyebrow: string;
  title: string;
  issue: string;
}) {
  return (
    <div className="page-shell">
      <Heading eyebrow={eyebrow} title={title} desc={`이 화면은 ${issue}에서 만듭니다.`} />
    </div>
  );
}
