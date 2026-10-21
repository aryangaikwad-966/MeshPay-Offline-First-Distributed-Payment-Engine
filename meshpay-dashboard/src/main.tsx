import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './ui';
import './style.css';
import './offline.css';

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js').catch(() => undefined);
  });
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
