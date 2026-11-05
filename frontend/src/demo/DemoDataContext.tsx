import React from 'react';
import type { SubmittedResponse } from '../types/api';
import { demoResponses } from './data';

interface DemoData {
  responses: SubmittedResponse[];
  addResponse: (response: SubmittedResponse) => void;
}

const DemoDataContext = React.createContext<DemoData | null>(null);

/** 데모 제출을 제출 내역과 응답 탐색에 이어 보여 주려고 화면 사이에서 공유한다. */
export function DemoDataProvider({ children }: { children: React.ReactNode }) {
  const [responses, setResponses] = React.useState<SubmittedResponse[]>(demoResponses);
  const value = React.useMemo<DemoData>(
    () => ({
      responses,
      addResponse: (response) => setResponses((prev) => [response, ...prev]),
    }),
    [responses],
  );
  return <DemoDataContext.Provider value={value}>{children}</DemoDataContext.Provider>;
}

export function useDemoData(): DemoData {
  const value = React.useContext(DemoDataContext);
  if (!value) throw new Error('useDemoData는 DemoDataProvider 안에서만 쓴다.');
  return value;
}
