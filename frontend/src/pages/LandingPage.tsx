import { Button, Icon, Panel } from '../components/UI';
import { LANDING_FEATURES_ID } from '../landing/landingFeatures';
import { LazyHeroAnimation } from '../landing/LazyHeroAnimation';
import { useRevealOnce } from '../landing/useRevealOnce';
export function LandingPage({
  onStart,
  onCreate,
  onExplore,
  onTour,
}: {
  onStart: () => void;
  onCreate: () => void;
  onExplore: () => void;
  /** "서비스 둘러보기". 없으면 onExplore를 쓴다. */
  onTour?: () => void;
}) {
  const features = useRevealOnce<HTMLDivElement>();
  return (
    <div className="landing-page">
      <section className="landing-hero">
        {/* 등장 효과: landing-rise-N 순서로 한 번 떠오른다(CSS만, 움직임 줄이기면 없음). */}
        <div className="landing-hero-copy">
          <div className="overline landing-rise">YOUR FORMS, ALL IN ONE PLACE</div>
          <h1>
            <span className="landing-rise landing-rise-1">폼의 시작부터,</span>
            <br />
            <span className="landing-rise landing-rise-2">마지막 답변까지.</span>
          </h1>
          <p className="landing-rise landing-rise-3">
            받은 폼은 놓치지 않고, 만든 폼은 더 명료하게.
            <br />
            작성부터 제출, 응답 탐색까지 Passform에서 이어가세요.
          </p>
          <div className="button-row landing-rise landing-rise-4">
            <Button onClick={onStart}>
              Passform 시작하기 <Icon name="arrow" size={16} />
            </Button>
            <Button variant="secondary" onClick={onTour ?? onExplore}>
              서비스 둘러보기
            </Button>
          </div>
        </div>
        <LazyHeroAnimation />
      </section>
      <div
        ref={features.ref}
        id={LANDING_FEATURES_ID}
        className={`landing-feature-grid${features.stage === 'idle' ? '' : ' landing-reveal'}${
          features.stage === 'ready' ? ' landing-reveal-wait' : ''
        }`}
      >
        <Panel>
          <div className="overline">01 · 응답자를 위해</div>
          <h2>
            미뤄둔 답변도,
            <br />
            끊김 없이 이어서.
          </h2>
          <p className="muted">
            받은 폼을 내 폼함에 저장하세요. 중단한 폼은 이어 쓰고, 제출한 답을 다시 확인하세요.
          </p>
          <Button variant="secondary" onClick={onStart}>
            내 폼함 가기
          </Button>
        </Panel>
        <Panel>
          <div className="overline">02 · 제작자를 위해</div>
          <h2>
            익숙하게 만들고,
            <br />
            간편하게 보내세요.
          </h2>
          <p className="muted">질문을 더하고 순서를 바꾼 뒤, 미리 보고 배포하세요.</p>
          <Button variant="secondary" onClick={onCreate}>
            폼 만들기
          </Button>
        </Panel>
        <Panel>
          <div className="overline">03 · 탐색 도우미</div>
          <h2>
            필터 대신,
            <br />
            평소 쓰는 말로.
          </h2>
          <p className="muted">모인 답변을 조건에 따라 탐색하는 인터페이스를 확인해보세요.</p>
          <Button variant="secondary" onClick={onExplore}>
            답변 탐색 보기
          </Button>
        </Panel>
      </div>
      {/* 정적 파일(frontend/public/privacy.html)로 가야 하므로 라우터 Link가 아닌 일반 링크를 쓴다. */}
      {/* 공통 레이아웃에 이미 footer가 있어 여기서는 div를 쓴다(contentinfo가 둘이 되지 않게). */}
      <div className="muted small">
        <a href="/privacy.html">개인정보처리방침</a>
      </div>
    </div>
  );
}
