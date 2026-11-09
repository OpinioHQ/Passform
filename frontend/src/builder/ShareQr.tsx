import React from 'react';
import { ApiError } from '../api/client';

type Load =
  { state: 'loading' } | { state: 'ready'; url: string } | { state: 'failed'; message: string };

function failureText(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) {
    return '아직 배포하지 않아 QR이 없어요.';
  }
  return 'QR을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}

/**
 * 서버가 만든 공유 링크 QR(SPEC 126행, AC10)을 보이고 PNG로 내려받게 한다. 화면에서 QR을 따로 만들지 않는다(사람 결정).
 * `label`은 화면 읽기 프로그램이 어느 폼의 QR인지 구분하게 하는 이름이다.
 */
export function ShareQr({ load, label }: { load: () => Promise<Blob>; label: string }) {
  const [result, setResult] = React.useState<Load>({ state: 'loading' });
  React.useEffect(() => {
    let active = true;
    let url: string | null = null;
    load()
      .then((blob) => {
        if (!active) return;
        url = URL.createObjectURL(blob);
        setResult({ state: 'ready', url });
      })
      .catch((error: unknown) => {
        if (active) setResult({ state: 'failed', message: failureText(error) });
      });
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [load]);

  if (result.state === 'loading') return <p className="small muted">QR을 불러오는 중이에요.</p>;
  if (result.state === 'failed') {
    return (
      <p className="error" role="alert">
        {result.message}
      </p>
    );
  }
  return (
    <div className="share-qr">
      <img src={result.url} alt={`${label} 공유 링크 QR`} width={200} height={200} />
      <a className="btn btn-secondary" href={result.url} download="passform-qr.png">
        PNG 내려받기
      </a>
    </div>
  );
}
