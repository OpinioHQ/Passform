import React from 'react';
import mark from '../assets/figma/layers.svg';
import bellOriginal from '../assets/figma/bell.svg';
import chevronOriginal from '../assets/figma/chevron-down.svg';
import searchOriginal from '../assets/figma/search.svg';
import bookmarkOriginal from '../assets/figma/bookmark-check.svg';
import plusOriginal from '../assets/figma/plus.svg';
import arrowOriginal from '../assets/figma/arrow-right.svg';

export function Icon({ name, size = 18 }: { name: string; size?: number }) {
  const original: Record<string, string> = {
    bell: bellOriginal,
    chevron: chevronOriginal,
    search: searchOriginal,
    bookmark: bookmarkOriginal,
    plus: plusOriginal,
    arrow: arrowOriginal,
  };
  if (original[name])
    return <img src={original[name]} width={size} height={size} alt="" aria-hidden="true" />;
  const paths: Record<string, React.ReactNode> = {
    bell: (
      <>
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
        <path d="M10 21h4" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    search: (
      <>
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-4-4" />
      </>
    ),
    arrow: (
      <>
        <path d="M5 12h14" />
        <path d="m13 6 6 6-6 6" />
      </>
    ),
    chevron: <path d="m6 9 6 6 6-6" />,
    back: (
      <>
        <path d="M19 12H5" />
        <path d="m11 6-6 6 6 6" />
      </>
    ),
    bookmark: <path d="M6 3h12v18l-6-4-6 4z" />,
    check: <path d="m5 12 4 4L19 6" />,
    x: <path d="M6 6l12 12M18 6 6 18" />,
    download: (
      <>
        <path d="M12 3v13m-5-5 5 5 5-5M4 21h16" />
      </>
    ),
    edit: (
      <>
        <path d="M12 20h9M4 16l-1 5 5-1L19 9l-5-5L4 16z" />
      </>
    ),
    menu: <path d="M4 7h16M4 12h16M4 17h16" />,
    message: <path d="M20 11.5a8.5 8.5 0 0 1-8.5 8.5H4l2-4A8.5 8.5 0 1 1 20 11.5z" />,
  };
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {paths[name] || paths.plus}
    </svg>
  );
}
export function Brand({ onClick }: { onClick?: () => void }) {
  return (
    <button className="brand" onClick={onClick} aria-label="Passform 홈">
      <span className="brand-mark">
        <img src={mark} width="18" height="18" alt="" />
      </span>
      <span>Passform</span>
    </button>
  );
}
export function Badge({
  children,
  tone = 'neutral',
}: {
  children: React.ReactNode;
  tone?: 'neutral' | 'blue' | 'amber' | 'green';
}) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}
export function Button({
  children,
  onClick,
  variant = 'primary',
  disabled,
  type = 'button',
}: {
  children: React.ReactNode;
  onClick?: () => void;
  variant?: 'primary' | 'secondary' | 'ghost';
  disabled?: boolean;
  type?: 'button' | 'submit';
}) {
  return (
    <button className={`btn btn-${variant}`} type={type} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  );
}
export function Panel({
  children,
  className = '',
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return <section className={`panel ${className}`}>{children}</section>;
}
export function Heading({
  eyebrow,
  title,
  desc,
  action,
}: {
  eyebrow: string;
  title: string;
  desc?: string;
  action?: React.ReactNode;
}) {
  return (
    <>
      <div className="eyebrow">{eyebrow}</div>
      <div className="heading-row">
        <div>
          <h1>{title}</h1>
          {desc && <p className="page-desc">{desc}</p>}
        </div>
        {action}
      </div>
    </>
  );
}
