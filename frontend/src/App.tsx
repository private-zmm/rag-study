import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useState } from 'react';
import { clearStoredSession, getStoredSession } from './auth/session';
import AppLayout from './layouts/AppLayout';
import AuthPage from './pages/AuthPage';
import type { AuthSession, PageKey } from './types';

function App() {
  const [activePage, setActivePage] = useState<PageKey>('chat');
  const [session, setSession] = useState<AuthSession | null>(() => getStoredSession());

  const handleLogout = () => {
    clearStoredSession();
    setSession(null);
    setActivePage('chat');
  };

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 8,
          fontFamily:
            'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        },
        components: {
          Layout: {
            bodyBg: '#ffffff',
            siderBg: '#f7f8fa',
          },
        },
      }}
    >
      {session ? (
        <AppLayout
          activePage={activePage}
          onLogout={handleLogout}
          onPageChange={setActivePage}
          user={session.user}
        />
      ) : (
        <AuthPage onAuthenticated={setSession} />
      )}
    </ConfigProvider>
  );
}

export default App;
