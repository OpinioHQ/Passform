import { useState, type ButtonHTMLAttributes, type ReactNode } from 'react';

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-xl border border-line bg-surface p-5 ${className}`}>{children}</section>;
}

type ButtonVariant = 'primary' | 'secondary';

export function Button({
  variant = 'secondary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  const look =
    variant === 'primary'
      ? 'bg-accent text-white hover:opacity-90'
      : 'border border-line bg-surface text-ink hover:bg-canvas';
  return (
    <button
      type="button"
      className={`min-h-11 rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${look} ${className}`}
      {...props}
    />
  );
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'warn' | 'danger'; children: ReactNode }) {
  const look = {
    info: 'bg-accent-soft text-ink',
    warn: 'bg-warn-soft text-warn',
    danger: 'bg-danger-soft text-danger',
  }[tone];
  return <div className={`rounded-lg px-4 py-3 text-sm leading-relaxed ${look}`}>{children}</div>;
}

export function Chip({ children }: { children: ReactNode }) {
  return <span className="inline-block rounded-full bg-canvas px-3 py-1 text-xs text-ink">{children}</span>;
}

// 글 옆의 물음표. 눌러야 설명이 보인다.
export function Explained({ children, notes }: { children: ReactNode; notes: string[] }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="text-sm text-muted">
      <div className="flex flex-wrap items-center gap-x-1">
        <span>{children}</span>
        {notes.length > 0 ? (
          <button
            type="button"
            aria-expanded={open}
            aria-label="자세한 설명 보기"
            onClick={() => setOpen(!open)}
            className="flex min-h-8 min-w-8 items-center justify-center"
          >
            <span className="flex h-5 w-5 items-center justify-center rounded-full border border-line text-xs hover:bg-canvas">?</span>
          </button>
        ) : null}
      </div>
      {open ? (
        <ul className="mt-1 grid gap-1 rounded-lg bg-canvas px-3 py-2 text-xs leading-relaxed">
          {notes.map((note) => <li key={note}>{note}</li>)}
        </ul>
      ) : null}
    </div>
  );
}

export function Bar({ label, count, max, hint }: { label: string; count: number; max: number; hint?: string }) {
  const width = max > 0 ? Math.round((count / max) * 100) : 0;
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_3rem] items-center gap-x-3 gap-y-1 text-sm">
      <div className="min-w-0">
        <span>{label}</span>
        {hint ? <span className="ml-2 text-xs text-muted">{hint}</span> : null}
      </div>
      <div className="text-right tabular-nums">{count}</div>
      <div className="col-span-2 h-1.5 rounded-full bg-canvas">
        <div className="h-1.5 rounded-full bg-accent" style={{ width: `${width}%` }} />
      </div>
    </div>
  );
}

export function PageTitle({ title, description }: { title: string; description?: string }) {
  return (
    <header className="mb-5">
      <h1 className="text-xl font-semibold">{title}</h1>
      {description ? <p className="mt-1 text-sm text-muted">{description}</p> : null}
    </header>
  );
}
