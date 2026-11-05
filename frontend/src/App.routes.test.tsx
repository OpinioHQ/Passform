// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { demoForm } from './demo/data';
import { ResponsePage } from './pages/ResponsePage';

function CurrentPath() {
  return <output data-testid="path">{useLocation().pathname}</output>;
}

function openAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <CurrentPath />
    </MemoryRouter>,
  );
}

const menuLinks = () =>
  within(screen.getByRole('navigation', { name: '주 메뉴' })).getAllByRole('link');

beforeEach(() => {
  window.scrollTo = vi.fn();
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.reject(new Error('가짜 데이터 모드에서는 API를 부르지 않는다'))),
  );
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('라우트 표', () => {
  it.each([
    ['/', '폼의 시작부터,마지막 답변까지.'],
    ['/login', '로그인'],
    ['/me/forms', '내 폼함'],
    ['/me/responses', '제출한 답변'],
    ['/me/responses/demo-r002', '제출한 답변'],
    ['/me/profile', '내 프로필'],
    ['/f/test', '응답 작성'],
    ['/forms/demo-form-team-experience', '응답 작성'],
    ['/creator', '내가 만든 폼'],
    ['/creator/forms/new', '폼 편집'],
    ['/creator/forms/abc/edit', '폼 편집'],
    ['/creator/forms/abc/results', '결과'],
    ['/creator/forms/abc/explore', '모인 답변을 살펴보세요'],
    ['/없는/경로', '페이지를 찾을 수 없어요'],
  ])('%s 는 공통 레이아웃과 함께 "%s" 화면을 연다', (path, heading) => {
    openAt(path);
    expect(screen.getByRole('banner')).toBeTruthy();
    expect(screen.getByRole('contentinfo')).toBeTruthy();
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(heading);
    expect(screen.getByText(/가상 데이터 모드/)).toBeTruthy();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('/me/responses/:responseId 는 그 제출 내역을 고르고, 없는 ID면 안내한다', () => {
    openAt('/me/responses/demo-r002');
    expect(screen.getByText('박서연')).toBeTruthy();
    cleanup();
    openAt('/me/responses/no-such');
    expect(screen.getByText('이 제출 내역을 찾을 수 없어요.')).toBeTruthy();
  });

  it('제출 내역 목록을 누르면 상세 주소로 간다', () => {
    openAt('/me/responses');
    const choices = screen.getAllByRole('button', { name: /제출 완료/ });
    fireEvent.click(choices[1]);
    expect(screen.getByTestId('path').textContent).toBe('/me/responses/demo-r002');
  });
});

describe('주 메뉴', () => {
  it.each([
    ['/', null],
    ['/login', null],
    ['/me/forms', '내 폼함'],
    ['/f/test', '내 폼함'],
    ['/forms/abc', '내 폼함'],
    ['/me/responses', '제출 내역'],
    ['/me/responses/demo-r002', '제출 내역'],
    ['/me/profile', '내 프로필'],
    ['/creator', '내가 만든 폼'],
    ['/creator/forms/new', '내가 만든 폼'],
    ['/creator/forms/abc/edit', '내가 만든 폼'],
    ['/creator/forms/abc/results', '내가 만든 폼'],
    ['/creator/forms/abc/explore', '내가 만든 폼'],
    ['/없는/경로', null],
  ])('%s 에서 메뉴 4개가 보이고 활성 메뉴는 %s 이다', (path, active) => {
    openAt(path);
    expect(menuLinks().map((link) => link.textContent)).toEqual([
      '내 폼함',
      '제출 내역',
      '내 프로필',
      '내가 만든 폼',
    ]);
    const activeLabels = menuLinks()
      .filter((link) => link.classList.contains('active'))
      .map((link) => link.textContent);
    expect(activeLabels).toEqual(active ? [active] : []);
  });

  it('메뉴로 제작자 라우트가 열린다', () => {
    openAt('/me/forms');
    fireEvent.click(screen.getByRole('link', { name: '내가 만든 폼' }));
    expect(screen.getByTestId('path').textContent).toBe('/creator');
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('내가 만든 폼');
  });
});

describe('응답 화면의 폼 설명', () => {
  const noteElement = (container: HTMLElement) => container.querySelector('.form-note');

  function renderForm(note: string) {
    return render(
      <ResponsePage
        form={{ ...demoForm, note }}
        onBack={vi.fn()}
        onSubmit={vi.fn(() => Promise.resolve())}
        saving={false}
      />,
    );
  }

  it('제목 바로 아래에 입력한 줄바꿈 그대로 보여 준다', () => {
    openAt('/f/test');
    const title = screen.getByRole('heading', { level: 2, name: demoForm.title });
    expect(demoForm.note).toContain('\n');
    expect(title.nextElementSibling?.className).toBe('form-note');
    expect(title.nextElementSibling?.textContent).toBe(demoForm.note);
  });

  it.each([
    ['빈 문자열', ''],
    ['공백만', ' \n '],
  ])('%s이면 자리를 만들지 않는다', (_label, note) => {
    const { container } = renderForm(note);
    expect(noteElement(container)).toBeNull();
    const title = screen.getByRole('heading', { level: 2, name: demoForm.title });
    expect(title.nextElementSibling?.classList.contains('muted')).toBe(true);
  });
});

describe('/creator/forms/new', () => {
  it('빈 폼에 초안 배지를 보이고, 미리보기는 끄고, 응답 수와 고정 섹션 이름은 숨긴다', () => {
    openAt('/creator/forms/new');
    expect((screen.getByLabelText('폼 제목') as HTMLInputElement).value).toBe('');
    expect(screen.getByText('초안')).toBeTruthy();
    expect((screen.getByRole('button', { name: '미리보기' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(screen.queryByRole('button', { name: /^응답 / })).toBeNull();
    expect(screen.queryByText(/섹션 01/)).toBeNull();
    expect(screen.queryByLabelText('질문 1 문구')).toBeNull();
  });

  it('기존 폼 편집 화면에는 배포 상태와 응답 수가 보이고 v1을 쓰지 않는다', () => {
    openAt('/creator/forms/abc/edit');
    expect(screen.getByText('배포 중 · 폼 버전 1')).toBeTruthy();
    expect(screen.getByRole('button', { name: '응답 6' })).toBeTruthy();
    expect(screen.queryByText(/v1/)).toBeNull();
    expect((screen.getByRole('button', { name: '미리보기' }) as HTMLButtonElement).disabled).toBe(
      false,
    );
  });
});

describe('VITE_USE_API=true', () => {
  const liveForm = { ...demoForm, form_id: 'live-form', title: '서버에서 받은 폼' };

  async function openLiveAt(path: string, body: unknown = liveForm) {
    vi.resetModules();
    vi.stubEnv('VITE_USE_API', 'true');
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
    const fetchMock = vi.fn(() =>
      Promise.resolve(new Response(JSON.stringify(body), { status: 200 })),
    );
    vi.stubGlobal('fetch', fetchMock);
    const { App: LiveApp } = await import('./App');
    render(
      <MemoryRouter initialEntries={[path]}>
        <LiveApp />
      </MemoryRouter>,
    );
    return () => fetchMock.mock.calls.map((call) => String((call as unknown[])[0]));
  }

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it('/f/:slug 는 공개 조회만 부르고 내 폼함을 부르지 않는다', async () => {
    const urls = await openLiveAt('/f/test');
    expect(await screen.findByText('서버에서 받은 폼')).toBeTruthy();
    expect(urls()).toEqual(['http://api.test/f/test']);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('/forms/:formId 는 formById 를 부른다', async () => {
    const urls = await openLiveAt('/forms/abc');
    expect(await screen.findByText('서버에서 받은 폼')).toBeTruthy();
    expect(urls()).toEqual(['http://api.test/forms/abc']);
  });

  it('/me/forms 에서만 내 폼함을 부른다', async () => {
    const urls = await openLiveAt('/me/forms', []);
    await waitFor(() => expect(urls()).toEqual(['http://api.test/me/forms']));
  });

  it('연결한 세 호출 밖의 화면은 API를 부르지 않는다', async () => {
    const urls = await openLiveAt('/me/responses');
    await waitFor(() => expect(screen.getByRole('banner')).toBeTruthy());
    expect(urls()).toEqual([]);
  });

  it('가짜 값이 남은 화면에는 띠를 보이고, 없는 화면에는 보이지 않는다', async () => {
    await openLiveAt('/creator');
    expect(screen.getByText(/가상 값이 남아 있습니다/)).toBeTruthy();
    cleanup();
    await openLiveAt('/login');
    expect(screen.queryByText(/가상 값이 남아 있습니다/)).toBeNull();
    expect(screen.queryByText(/가상 데이터 모드/)).toBeNull();
  });

  it('가짜 사용자 대신 로그인 자리를 보인다', async () => {
    await openLiveAt('/login');
    expect(screen.queryByText('김지윤')).toBeNull();
    expect(screen.getByRole('link', { name: '로그인' })).toBeTruthy();
  });
});
