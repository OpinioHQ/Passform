import { useSearchParams } from 'react-router-dom';
import { googleLoginUrl } from '../api/client';
import { Heading } from '../components/UI';

/** 로그인은 서버가 Google로 보내는 전체 페이지 이동이다. 실패하면 서버가 `?error`를 붙여 이 화면으로 돌려보낸다. */
export function LoginPage() {
  const [params] = useSearchParams();
  return (
    <div className="page-shell">
      <Heading
        eyebrow="계정"
        title="로그인"
        desc="폼 제작과 제출 내역 확인에 필요합니다. 폼에 응답하는 데는 로그인이 없어도 됩니다."
      />
      {params.has('error') && (
        <p className="error" role="alert">
          로그인하지 못했습니다. 다시 시도해 주세요.
        </p>
      )}
      <a className="btn btn-primary" href={googleLoginUrl}>
        Google로 로그인
      </a>
    </div>
  );
}
