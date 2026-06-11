import { Button, Form, Input, Segmented, Typography, message } from 'antd';
import { Lock, Mail, UserRound } from 'lucide-react';
import { useState } from 'react';
import { login, register } from '../../api/client';
import { storeSession } from '../../auth/session';
import type { AuthSession } from '../../types';
import styles from './AuthPage.module.css';

type AuthPageProps = {
  onAuthenticated: (session: AuthSession) => void;
};

type AuthMode = 'login' | 'register';

function AuthPage({ onAuthenticated }: AuthPageProps) {
  const [mode, setMode] = useState<AuthMode>('login');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (values: Record<string, string>) => {
    setSubmitting(true);

    try {
      const session =
        mode === 'login'
          ? await login({ account: values.account, password: values.password })
          : await register({
              username: values.username,
              email: values.email,
              password: values.password,
              nickname: values.nickname,
            });

      storeSession(session);
      onAuthenticated(session);
      message.success(mode === 'login' ? '登录成功' : '注册成功');
    } catch {
      message.error(mode === 'login' ? '登录失败，请检查账号密码' : '注册失败，请换一个用户名或邮箱');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className={styles.authPage}>
      <section className={styles.authPanel}>
        <div className={styles.authBrand}>
          <span className={styles.welcomeLogo}>R</span>
          <div>
            <Typography.Title level={2}>RAG Study</Typography.Title>
            <Typography.Text type="secondary">登录后开始管理你的笔记、知识库和对话。</Typography.Text>
          </div>
        </div>

        <Segmented
          block
          value={mode}
          onChange={(value) => setMode(value as AuthMode)}
          options={[
            { label: '登录', value: 'login' },
            { label: '注册', value: 'register' },
          ]}
        />

        <Form className={styles.authForm} layout="vertical" onFinish={handleSubmit}>
          {mode === 'login' ? (
            <Form.Item name="account" rules={[{ required: true, message: '请输入用户名或邮箱' }]}>
              <Input prefix={<UserRound size={16} />} placeholder="用户名或邮箱" />
            </Form.Item>
          ) : (
            <>
              <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
                <Input prefix={<UserRound size={16} />} placeholder="用户名" />
              </Form.Item>
              <Form.Item name="email" rules={[{ required: true, type: 'email', message: '请输入邮箱' }]}>
                <Input prefix={<Mail size={16} />} placeholder="邮箱" />
              </Form.Item>
              <Form.Item name="nickname">
                <Input prefix={<UserRound size={16} />} placeholder="昵称，可不填" />
              </Form.Item>
            </>
          )}

          <Form.Item name="password" rules={[{ required: true, min: 6, message: '密码至少 6 位' }]}>
            <Input.Password prefix={<Lock size={16} />} placeholder="密码" />
          </Form.Item>

          <Button block type="primary" htmlType="submit" loading={submitting}>
            {mode === 'login' ? '登录' : '创建账号'}
          </Button>
        </Form>
      </section>
    </main>
  );
}

export default AuthPage;
