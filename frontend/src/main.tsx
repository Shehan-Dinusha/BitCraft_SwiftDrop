import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from '@/app/App';
import { QueryProvider } from '@/app/providers/QueryProvider';
import { AuthProvider } from '@/app/providers/AuthProvider';
import { DensityProvider } from '@/app/providers/DensityProvider';
import { WebSocketProvider } from '@/app/providers/WebSocketProvider';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryProvider>
      <AuthProvider>
        <DensityProvider>
          <WebSocketProvider>
            <BrowserRouter><App /></BrowserRouter>
          </WebSocketProvider>
        </DensityProvider>
      </AuthProvider>
    </QueryProvider>
  </React.StrictMode>
);
