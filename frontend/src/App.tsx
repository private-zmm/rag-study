import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useEffect, useState } from 'react';
import { SESSION_EXPIRED_EVENT } from './api/client';
import { clearStoredSession, getStoredSession } from './auth/session';
import AppLayout from './layouts/AppLayout';
import { clearStoredActivePage, getStoredActivePage, storeActivePage } from './navigation/pageState';
import AuthPage from './pages/AuthPage';
import type { AuthSession, PageKey } from './types';

function App() {
  const [activePage, setActivePage] = useState<PageKey>(() => getStoredActivePage());
  const [session, setSession] = useState<AuthSession | null>(() => getStoredSession());

  const handlePageChange = (page: PageKey) => {
    setActivePage(page);
    storeActivePage(page);
  };

  const handleLogout = () => {
    clearStoredSession();
    clearStoredActivePage();
    setSession(null);
    handlePageChange('chat');
  };

  useEffect(() => {
    const handleSessionExpired = () => {
      clearStoredActivePage();
      setSession(null);
      handlePageChange('chat');
    };

    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);

    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    };
  }, []);

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
          onRequireLogin={handleLogout}
          onPageChange={handlePageChange}
          user={session.user}
        />
      ) : (
        <AuthPage onAuthenticated={setSession} />
      )}
    </ConfigProvider>
  );
}

export default App;
