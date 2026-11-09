import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
// Pretendard는 npm 패키지에서 번들해 자체 호스팅한다(외부 CDN을 쓰지 않는다).
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
