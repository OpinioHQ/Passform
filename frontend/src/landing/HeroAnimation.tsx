import { useCallback, useEffect, useRef } from 'react';
import type { PointerEvent as ReactPointerEvent, ReactNode } from 'react';
import {
  animate,
  backOut,
  easeIn,
  easeInOut,
  easeOut,
  motion,
  useMotionValue,
  useSpring,
  useTransform,
  type MotionValue,
} from 'motion/react';
import { useMediaQuery } from './useMediaQuery';

/*
 * 홈 hero 오른쪽 그림: 제목 "폼의 시작부터, 마지막 답변까지."를 글자 없이 옮긴 반복 장면.
 * 시간 t(초) 하나를 0→8로 돌리고 모든 움직임을 t에서 계산한다.
 * 1 떠다님(0~1.6) → 2 모임(1.6~2.8) → 3 작성(2.8~5.2) → 4 접고 날기(5.2~6.8) → 5 쉼(6.8~8)
 */
const LOOP = 8;
const FLOAT_END = 1.6;
/** 움직임 줄이기: 3번 장면 끝(칸이 다 채워진 폼)에서 멈춘 모습. */
const STILL_AT = 5.15;

const linear = (v: number) => v;

const CARD = { x: 60, y: 48, width: 280, height: 296 };
const CENTER = { x: CARD.x + CARD.width / 2, y: CARD.y + CARD.height / 2 };
const LEFT = CARD.x + 24;

// 떠다님 단계의 흩어진 위치(제자리 기준 이동·회전)와 그동안 흘러가는 양.
const SCATTER = [
  { x: -34, y: -30, rotate: -7, driftX: 8, driftY: -6 },
  { x: 30, y: -12, rotate: 5, driftX: -6, driftY: 6 },
  { x: -40, y: 2, rotate: -4, driftX: 7, driftY: 4 },
  { x: 38, y: 14, rotate: 6, driftX: -5, driftY: -7 },
  { x: -28, y: 26, rotate: -8, driftX: 6, driftY: -5 },
  { x: 42, y: 30, rotate: 7, driftX: -7, driftY: 5 },
  { x: -16, y: 46, rotate: -5, driftX: 5, driftY: -6 },
] as const;

// 마우스 밀어내기: 조각과 이 거리(px) 안이면 최대 PUSH_MAX만큼 밀렸다가 돌아온다.
const PUSH_RADIUS = 56;
const PUSH_MAX = 12;
const PUSH_RETURN_MS = 320;
const PUSH_SPRING = { stiffness: 260, damping: 20 };

// 종이비행기 경로(카드 가운데 → 오른쪽 위 곡선)
const FLIGHT_START = 5.9;
const FLIGHT_END = 6.65;
const FLIGHT_FROM = CENTER;
const FLIGHT_BEND = { x: 330, y: 202 };
const FLIGHT_TO = { x: 386, y: 38 };
const FLIGHT_STEPS = 7;
const FLIGHT_POWER = 1.5;
const TRAIL_DOTS = 10;

function bezier(s: number) {
  const a = (1 - s) * (1 - s);
  const b = 2 * (1 - s) * s;
  const c = s * s;
  return {
    x: a * FLIGHT_FROM.x + b * FLIGHT_BEND.x + c * FLIGHT_TO.x,
    y: a * FLIGHT_FROM.y + b * FLIGHT_BEND.y + c * FLIGHT_TO.y,
  };
}

function bezierAngle(s: number) {
  const dx = 2 * (1 - s) * (FLIGHT_BEND.x - FLIGHT_FROM.x) + 2 * s * (FLIGHT_TO.x - FLIGHT_BEND.x);
  const dy = 2 * (1 - s) * (FLIGHT_BEND.y - FLIGHT_FROM.y) + 2 * s * (FLIGHT_TO.y - FLIGHT_BEND.y);
  return (Math.atan2(dy, dx) * 180) / Math.PI;
}

/** 경로 위치 s(0~1)에 비행기가 닿는 시각. 시간 u에 대해 s = u^FLIGHT_POWER(점점 빨라짐). */
function flightTimeAt(s: number) {
  return FLIGHT_START + (FLIGHT_END - FLIGHT_START) * Math.pow(s, 1 / FLIGHT_POWER);
}

const FLIGHT_TIMES = Array.from(
  { length: FLIGHT_STEPS },
  (_, k) => FLIGHT_START + ((FLIGHT_END - FLIGHT_START) * k) / (FLIGHT_STEPS - 1),
);
const FLIGHT_S = FLIGHT_TIMES.map((time) =>
  Math.pow((time - FLIGHT_START) / (FLIGHT_END - FLIGHT_START), FLIGHT_POWER),
);
const FLIGHT_X = FLIGHT_S.map((s) => bezier(s).x - FLIGHT_FROM.x);
const FLIGHT_Y = FLIGHT_S.map((s) => bezier(s).y - FLIGHT_FROM.y);
// 비행기 그림은 코가 오른쪽 위(-45°)를 보므로 진행 방향에 45°를 더한다.
const FLIGHT_ROTATE = FLIGHT_S.map((s) => bezierAngle(s) + 45);
const TRAIL = Array.from({ length: TRAIL_DOTS }, (_, i) => {
  const s = ((i + 0.6) / TRAIL_DOTS) * 0.88;
  return { ...bezier(s), at: flightTimeAt(s) };
});

interface PieceHandle {
  el: SVGGElement;
  nudge: (dx: number, dy: number) => void;
}

function Piece({
  index,
  t,
  onReady,
  children,
}: {
  index: number;
  t: MotionValue<number>;
  onReady: (index: number, handle: PieceHandle | null) => void;
  children: ReactNode;
}) {
  const ref = useRef<SVGGElement>(null);
  const pushX = useSpring(0, PUSH_SPRING);
  const pushY = useSpring(0, PUSH_SPRING);
  const s = SCATTER[index];
  const start = FLOAT_END + index * 0.06;
  const times = [0, 0.8, start, start + 0.75, LOOP];
  const ease = [easeInOut, easeInOut, backOut, linear];
  const x = useTransform(t, times, [s.x, s.x + s.driftX * 0.5, s.x + s.driftX, 0, 0], { ease });
  const y = useTransform(t, times, [s.y, s.y + s.driftY, s.y + s.driftY * 0.3, 0, 0], { ease });
  const rotate = useTransform(t, times, [s.rotate, s.rotate * 0.7, s.rotate * 0.5, 0, 0], {
    ease,
  });
  const opacity = useTransform(t, [0.05 * index, 0.35 + 0.05 * index], [0, 1]);

  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    let timer: number | undefined;
    onReady(index, {
      el,
      nudge(dx, dy) {
        pushX.set(dx);
        pushY.set(dy);
        window.clearTimeout(timer);
        timer = window.setTimeout(() => {
          pushX.set(0);
          pushY.set(0);
        }, PUSH_RETURN_MS);
      },
    });
    return () => {
      window.clearTimeout(timer);
      onReady(index, null);
    };
  }, [index, onReady, pushX, pushY]);

  return (
    <motion.g style={{ x: pushX, y: pushY }}>
      <motion.g ref={ref} style={{ x, y, rotate, opacity }}>
        {children}
      </motion.g>
    </motion.g>
  );
}

function TrailDot({ t, x, y, at }: { t: MotionValue<number>; x: number; y: number; at: number }) {
  const opacity = useTransform(t, [at, at + 0.06, 6.95, 7.35], [0, 1, 1, 0]);
  return <motion.circle className="landing-art-trail" cx={x} cy={y} r={2} style={{ opacity }} />;
}

export function HeroAnimation() {
  const reduce = useMediaQuery('(prefers-reduced-motion: reduce)');
  const finePointer = useMediaQuery('(hover: hover) and (pointer: fine)');
  // 설정을 알 수 없으면(matchMedia 없음) 움직이지 않는 쪽을 고른다.
  const still = reduce !== false;
  const t = useMotionValue(still ? STILL_AT : 0);

  useEffect(() => {
    if (still) {
      t.set(STILL_AT);
      return undefined;
    }
    t.set(0);
    const controls = animate(t, [0, LOOP], { duration: LOOP, ease: 'linear', repeat: Infinity });
    // 탭이 안 보이면 멈추고, 돌아오면 멈춘 자리부터 이어서 돈다.
    const sync = () => {
      if (document.hidden) controls.pause();
      else controls.play();
    };
    sync();
    document.addEventListener('visibilitychange', sync);
    return () => {
      document.removeEventListener('visibilitychange', sync);
      controls.stop();
    };
  }, [still, t]);

  const pieces = useRef<(PieceHandle | null)[]>([]);
  const onReady = useCallback((index: number, handle: PieceHandle | null) => {
    pieces.current[index] = handle;
  }, []);
  const interactive = !still && finePointer === true;
  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (t.get() >= FLOAT_END) return;
    const { clientX: px, clientY: py } = event;
    for (const piece of pieces.current) {
      if (!piece) continue;
      const box = piece.el.getBoundingClientRect();
      const nearX = Math.min(Math.max(px, box.left), box.right);
      const nearY = Math.min(Math.max(py, box.top), box.bottom);
      const gap = Math.hypot(px - nearX, py - nearY);
      if (gap >= PUSH_RADIUS) continue;
      const awayX = box.left + box.width / 2 - px;
      const awayY = box.top + box.height / 2 - py;
      const length = Math.hypot(awayX, awayY) || 1;
      const strength = PUSH_MAX * (1 - gap / PUSH_RADIUS);
      piece.nudge((awayX / length) * strength, (awayY / length) * strength);
    }
  };

  // 2 모임: 카드 테두리·그림자가 나타난다.
  const frameOpacity = useTransform(t, [1.75, 2.45], [0, 1]);
  const frameScale = useTransform(t, [1.75, 2.45], [0.96, 1], { ease: easeOut });
  // 3 작성
  const fillFirst = useTransform(t, [2.95, 3.65], [0, 1], { ease: easeInOut });
  const fillSecond = useTransform(t, [3.55, 4.15], [0, 1], { ease: easeInOut });
  const choiceRing = useTransform(t, [4.15, 4.3], [0, 1]);
  const choiceDot = useTransform(t, [4.15, 4.4], [0, 1], { ease: backOut });
  const tickBox = useTransform(t, [4.45, 4.55], [0, 1]);
  const tick = useTransform(t, [4.45, 4.75], [0, 1], { ease: easeOut });
  const press = useTransform(t, [4.85, 4.95, 5.1], [1, 0.92, 1]);
  // 4 접기: 반으로 접히듯 납작해지고 좁아지며 사라진다.
  const foldY = useTransform(t, [5.2, 5.5], [1, 0.5], { ease: easeInOut });
  const foldX = useTransform(t, [5.45, 5.8], [1, 0.3], { ease: easeIn });
  const foldRotate = useTransform(t, [5.45, 5.8], [0, -10], { ease: easeIn });
  const foldOpacity = useTransform(t, [5.62, 5.82], [1, 0]);
  const crease = useTransform(t, [5.2, 5.32, 5.62], [0, 1, 1]);
  // 4 날기: 종이비행기
  const planeX = useTransform(t, FLIGHT_TIMES, FLIGHT_X);
  const planeY = useTransform(t, FLIGHT_TIMES, FLIGHT_Y);
  const planeRotate = useTransform(t, FLIGHT_TIMES, FLIGHT_ROTATE);
  const planeScale = useTransform(t, [5.7, FLIGHT_START, FLIGHT_END], [0.5, 1, 0.55], {
    ease: [backOut, easeIn],
  });
  const planeOpacity = useTransform(t, [5.7, 5.82, FLIGHT_END - 0.2, FLIGHT_END], [0, 1, 1, 0]);
  // 5 쉼: 완료 표시
  const doneOpacity = useTransform(t, [6.85, 7.05, 7.55, 7.85], [0, 1, 1, 0]);
  const doneScale = useTransform(t, [6.85, 7.15], [0.6, 1], { ease: backOut });
  const doneTick = useTransform(t, [6.95, 7.3], [0, 1], { ease: easeOut });

  const choiceX = [LEFT + 8, LEFT + 74, LEFT + 140];
  const boxX = [LEFT, LEFT + 92];

  return (
    <div
      className="landing-art"
      aria-hidden="true"
      data-motion={still ? 'static' : 'animated'}
      onPointerMove={interactive ? onPointerMove : undefined}
    >
      <svg viewBox="0 0 400 400" focusable="false">
        <motion.g
          style={{ scaleX: foldX, scaleY: foldY, rotate: foldRotate, opacity: foldOpacity }}
        >
          <motion.rect
            className="landing-art-card"
            x={CARD.x}
            y={CARD.y}
            width={CARD.width}
            height={CARD.height}
            rx={16}
            style={{ opacity: frameOpacity, scale: frameScale }}
          />
          <motion.line
            className="landing-art-crease"
            x1={CARD.x}
            x2={CARD.x + CARD.width}
            y1={CENTER.y}
            y2={CENTER.y}
            style={{ opacity: crease }}
          />
          {/* 글줄 2: 제목·설명 자리 */}
          <Piece index={0} t={t} onReady={onReady}>
            <rect className="landing-art-line" x={LEFT} y={72} width={140} height={14} rx={7} />
          </Piece>
          <Piece index={1} t={t} onReady={onReady}>
            <rect className="landing-art-line" x={LEFT} y={96} width={196} height={8} rx={4} />
          </Piece>
          {/* 입력칸 2: 글자 대신 회색 막대가 차오른다 */}
          <Piece index={2} t={t} onReady={onReady}>
            <rect className="landing-art-field" x={LEFT} y={122} width={232} height={34} rx={8} />
            <motion.rect
              className="landing-art-fill"
              x={LEFT + 14}
              y={136}
              width={118}
              height={6}
              rx={3}
              style={{ scaleX: fillFirst, originX: 0 }}
            />
          </Piece>
          <Piece index={3} t={t} onReady={onReady}>
            <rect className="landing-art-field" x={LEFT} y={166} width={232} height={34} rx={8} />
            <motion.rect
              className="landing-art-fill"
              x={LEFT + 14}
              y={180}
              width={82}
              height={6}
              rx={3}
              style={{ scaleX: fillSecond, originX: 0 }}
            />
          </Piece>
          {/* 선택 동그라미 3 + 짧은 막대: 가운데가 채워진다 */}
          <Piece index={4} t={t} onReady={onReady}>
            {choiceX.map((cx) => (
              <g key={cx}>
                <circle className="landing-art-mark" cx={cx} cy={226} r={7} />
                <rect
                  className="landing-art-line"
                  x={cx + 13}
                  y={223}
                  width={34}
                  height={6}
                  rx={3}
                />
              </g>
            ))}
            <motion.circle
              className="landing-art-mark-on"
              cx={choiceX[1]}
              cy={226}
              r={7}
              style={{ opacity: choiceRing }}
            />
            <motion.circle
              className="landing-art-ink"
              cx={choiceX[1]}
              cy={226}
              r={3.5}
              style={{ scale: choiceDot }}
            />
          </Piece>
          {/* 체크 네모 묶음: 첫 칸에 체크가 그려진다 */}
          <Piece index={5} t={t} onReady={onReady}>
            {boxX.map((bx) => (
              <g key={bx}>
                <rect className="landing-art-mark" x={bx} y={250} width={15} height={15} rx={4} />
                <rect
                  className="landing-art-line"
                  x={bx + 23}
                  y={254.5}
                  width={52}
                  height={6}
                  rx={3}
                />
              </g>
            ))}
            <motion.rect
              className="landing-art-mark-on"
              x={boxX[0]}
              y={250}
              width={15}
              height={15}
              rx={4}
              style={{ opacity: tickBox }}
            />
            <motion.path
              className="landing-art-tick"
              d={`M${boxX[0] + 3.5} 257.5 L${boxX[0] + 6.5} 260.5 L${boxX[0] + 11.5} 254.5`}
              style={{ pathLength: tick, opacity: tickBox }}
            />
          </Piece>
          {/* 버튼 막대: 한 번 눌린다 */}
          <Piece index={6} t={t} onReady={onReady}>
            <motion.g style={{ scale: press }}>
              <rect className="landing-art-ink" x={LEFT} y={286} width={100} height={34} rx={8} />
              <rect
                className="landing-art-label"
                x={LEFT + 22}
                y={300}
                width={56}
                height={6}
                rx={3}
              />
            </motion.g>
          </Piece>
        </motion.g>

        {TRAIL.map((dot) => (
          <TrailDot key={dot.at} t={t} x={dot.x} y={dot.y} at={dot.at} />
        ))}

        <g transform={`translate(${FLIGHT_FROM.x} ${FLIGHT_FROM.y})`}>
          <motion.g
            style={{
              x: planeX,
              y: planeY,
              rotate: planeRotate,
              scale: planeScale,
              opacity: planeOpacity,
            }}
          >
            <path className="landing-art-plane" d="M26 -26 L-26 -7.8 L-2.6 2.6 Z" />
            <path className="landing-art-plane-shade" d="M26 -26 L-2.6 2.6 L7.8 26 Z" />
          </motion.g>
        </g>

        <g transform={`translate(${CENTER.x} ${CENTER.y})`}>
          <motion.g style={{ opacity: doneOpacity, scale: doneScale }}>
            <circle className="landing-art-done" cx={0} cy={0} r={22} />
            <motion.path
              className="landing-art-done"
              d="M-9 0.5 L-3 6.5 L9.5 -6"
              style={{ pathLength: doneTick }}
            />
          </motion.g>
        </g>
      </svg>
    </div>
  );
}
