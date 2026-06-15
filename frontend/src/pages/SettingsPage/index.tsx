import { Button, Card, Checkbox, Form, Input, List, Modal, Select, Space, Switch, Tabs, Tag, Typography, message } from 'antd';
import { Check, CloudUpload, Pencil, Plus, RotateCcw, Save, Trash2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  createEmbeddingModelConfig,
  createBackup,
  deleteBackup,
  createChatModelConfig,
  deleteChatConversation,
  fetchBackupConfig,
  fetchBackups,
  fetchChatConversations,
  fetchClipperProxyConfig,
  fetchEmbeddingModelConfigs,
  fetchChatModelConfigs,
  restoreBackup,
  restoreChatConversation,
  saveBackupConfig,
  saveClipperProxyConfig,
  setDefaultEmbeddingModelConfig,
  setDefaultChatModelConfig,
  testBackupDatabaseTools,
  testBackupConfig,
  testClipperProxyConfig,
  updateEmbeddingModelConfig,
  updateChatModelConfig,
} from '../../api/client';
import type { BackupConfig, BackupItem, ChatConversation, ChatModelConfig, ClipperProxyConfig, EmbeddingModelConfig } from '../../types';
import './SettingsPage.css';

type SettingsFormValues = {
  name: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  systemPrompt: string;
  defaultModel: boolean;
};

type EmbeddingFormValues = {
  name: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  dimensions: number;
  defaultModel: boolean;
};

type BackupFormValues = {
  enabled: boolean;
  endpoint: string;
  bucket: string;
  accessKey: string;
  secretKey?: string;
  region?: string;
  prefix: string;
  cronExpression: string;
  retentionDays: number;
  retentionCount: number;
  pathStyleAccess: boolean;
  pgDumpPath?: string;
  psqlPath?: string;
};

type ClipperProxyFormValues = {
  protocol: 'HTTP' | 'SOCKS5';
  host: string;
  port?: number;
  username?: string;
  password?: string;
};

type SettingsPageProps = {
  onConversationsChanged?: () => void;
  onRequireLogin?: () => void;
};

function SettingsPage({ onConversationsChanged, onRequireLogin }: SettingsPageProps) {
  const [form] = Form.useForm<SettingsFormValues>();
  const [embeddingForm] = Form.useForm<EmbeddingFormValues>();
  const [backupForm] = Form.useForm<BackupFormValues>();
  const [clipperProxyForm] = Form.useForm<ClipperProxyFormValues>();
  const [modelConfigs, setModelConfigs] = useState<ChatModelConfig[]>([]);
  const [embeddingConfigs, setEmbeddingConfigs] = useState<EmbeddingModelConfig[]>([]);
  const [archivedConversations, setArchivedConversations] = useState<ChatConversation[]>([]);
  const [backupConfig, setBackupConfig] = useState<BackupConfig | null>(null);
  const [clipperProxyConfig, setClipperProxyConfig] = useState<ClipperProxyConfig | null>(null);
  const [backups, setBackups] = useState<BackupItem[]>([]);
  const [editingConfig, setEditingConfig] = useState<ChatModelConfig | null>(null);
  const [editingEmbeddingConfig, setEditingEmbeddingConfig] = useState<EmbeddingModelConfig | null>(null);
  const [savingBackupConfig, setSavingBackupConfig] = useState(false);
  const [savingClipperProxyConfig, setSavingClipperProxyConfig] = useState(false);
  const [testingBackupConfig, setTestingBackupConfig] = useState(false);
  const [testingClipperProxyConfig, setTestingClipperProxyConfig] = useState(false);
  const [testingDatabaseTools, setTestingDatabaseTools] = useState(false);
  const [runningBackup, setRunningBackup] = useState(false);
  const [restoringBackupObjectName, setRestoringBackupObjectName] = useState<string>();

  const loadModelConfigs = async () => {
    try {
      setModelConfigs(await fetchChatModelConfigs());
    } catch {
      message.error('模型配置加载失败');
    }
  };

  useEffect(() => {
    void loadModelConfigs();
    void loadEmbeddingConfigs();
    void loadArchivedConversations();
    void loadBackupConfig();
    void loadClipperProxyConfig();
    void loadBackups();
  }, []);

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload = {
      ...values,
      defaultModel: values.defaultModel || (!editingConfig && modelConfigs.length === 0),
    };

    if (editingConfig) {
      await updateChatModelConfig(editingConfig.id, payload);
      message.success('模型配置已更新');
    } else {
      await createChatModelConfig(payload);
      message.success('模型配置已新增');
    }

    setEditingConfig(null);
    form.resetFields();
    await loadModelConfigs();
  };

  const handleEdit = (config: ChatModelConfig) => {
    setEditingConfig(config);
    form.setFieldsValue({
      name: config.name,
      providerType: config.providerType,
      baseUrl: config.baseUrl,
      apiKey: '',
      model: config.model,
      systemPrompt: config.systemPrompt || '',
      defaultModel: config.defaultModel,
    });
  };

  const handleSetDefault = async (id: string) => {
    await setDefaultChatModelConfig(id);
    message.success('默认模型已更新');
    await loadModelConfigs();
  };

  const loadEmbeddingConfigs = async () => {
    try {
      setEmbeddingConfigs(await fetchEmbeddingModelConfigs());
    } catch {
      message.error('Embedding 配置加载失败');
    }
  };

  const handleSaveEmbedding = async () => {
    const values = await embeddingForm.validateFields();
    const payload = {
      ...values,
      dimensions: Number(values.dimensions),
      defaultModel: values.defaultModel || (!editingEmbeddingConfig && embeddingConfigs.length === 0),
    };

    if (editingEmbeddingConfig) {
      await updateEmbeddingModelConfig(editingEmbeddingConfig.id, payload);
      message.success('Embedding 配置已更新');
    } else {
      await createEmbeddingModelConfig(payload);
      message.success('Embedding 配置已新增');
    }

    setEditingEmbeddingConfig(null);
    embeddingForm.resetFields();
    await loadEmbeddingConfigs();
  };

  const handleEditEmbedding = (config: EmbeddingModelConfig) => {
    setEditingEmbeddingConfig(config);
    embeddingForm.setFieldsValue({
      name: config.name,
      providerType: config.providerType,
      baseUrl: config.baseUrl,
      apiKey: '',
      model: config.model,
      dimensions: config.dimensions,
      defaultModel: config.defaultModel,
    });
  };

  const handleSetDefaultEmbedding = async (id: string) => {
    await setDefaultEmbeddingModelConfig(id);
    message.success('默认 Embedding 已更新');
    await loadEmbeddingConfigs();
  };

  const loadArchivedConversations = async () => {
    try {
      setArchivedConversations(await fetchChatConversations(true));
    } catch {
      message.error('归档会话加载失败');
    }
  };

  const handleRestoreConversation = async (conversationId: string) => {
    try {
      await restoreChatConversation(conversationId);
      setArchivedConversations((currentConversations) =>
        currentConversations.filter((conversation) => conversation.id !== conversationId),
      );
      onConversationsChanged?.();
      message.success('会话已恢复');
    } catch {
      message.error('会话恢复失败');
    }
  };

  const handleDeleteConversation = async (conversationId: string) => {
    try {
      await deleteChatConversation(conversationId);
      setArchivedConversations((currentConversations) =>
        currentConversations.filter((conversation) => conversation.id !== conversationId),
      );
      onConversationsChanged?.();
      message.success('会话已删除');
    } catch {
      message.error('会话删除失败');
    }
  };

  const confirmDeleteConversation = (conversation: ChatConversation) => {
    Modal.confirm({
      title: '删除这个归档会话？',
      content: '删除后无法恢复。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleDeleteConversation(conversation.id),
    });
  };

  const loadBackupConfig = async () => {
    try {
      const config = await fetchBackupConfig();
      setBackupConfig(config);
      backupForm.setFieldsValue({
        enabled: config.enabled,
        endpoint: config.endpoint,
        bucket: config.bucket,
        accessKey: config.accessKey,
        secretKey: '',
        region: config.region,
        prefix: config.prefix,
        cronExpression: toFivePartCron(config.cronExpression),
        retentionDays: config.retentionDays,
        retentionCount: config.retentionCount,
        pathStyleAccess: config.pathStyleAccess,
        pgDumpPath: config.pgDumpPath || 'pg_dump',
        psqlPath: config.psqlPath || 'psql',
      });
    } catch {
      message.error('S3 备份配置加载失败');
    }
  };

  const loadBackups = async () => {
    try {
      setBackups(await fetchBackups());
    } catch {
      setBackups([]);
    }
  };

  const loadClipperProxyConfig = async () => {
    try {
      const config = await fetchClipperProxyConfig();
      setClipperProxyConfig(config);
      clipperProxyForm.setFieldsValue({
        protocol: config.protocol,
        host: config.host,
        port: config.port ?? undefined,
        username: config.username,
        password: '',
      });
    } catch {
      message.error('剪藏代理配置加载失败');
    }
  };

  const handleSaveClipperProxyConfig = async () => {
    const values = await clipperProxyForm.validateFields();
    setSavingClipperProxyConfig(true);

    try {
      const savedConfig = await saveClipperProxyConfig({
        ...values,
        port: Number(values.port),
      });
      setClipperProxyConfig(savedConfig);
      clipperProxyForm.setFieldValue('password', '');
      message.success('剪藏代理配置已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '剪藏代理配置保存失败');
    } finally {
      setSavingClipperProxyConfig(false);
    }
  };

  const handleTestClipperProxyConfig = async () => {
    const values = await clipperProxyForm.validateFields();
    setTestingClipperProxyConfig(true);

    try {
      await testClipperProxyConfig({
        ...values,
        port: Number(values.port),
      });
      message.success('剪藏代理测试成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '剪藏代理测试失败');
    } finally {
      setTestingClipperProxyConfig(false);
    }
  };

  const handleSaveBackupConfig = async () => {
    const values = await backupForm.validateFields();
    setSavingBackupConfig(true);

    try {
      const savedConfig = await saveBackupConfig({
        ...values,
        retentionDays: Number(values.retentionDays),
        retentionCount: Number(values.retentionCount),
      });
      setBackupConfig(savedConfig);
      backupForm.setFieldValue('secretKey', '');
      message.success('S3 备份配置已保存');
      await loadBackups();
    } catch {
      message.error('S3 备份配置保存失败');
    } finally {
      setSavingBackupConfig(false);
    }
  };

  const handleTestBackupConfig = async () => {
    const values = await backupForm.validateFields();
    setTestingBackupConfig(true);

    try {
      await testBackupConfig({
        ...values,
        retentionDays: Number(values.retentionDays),
        retentionCount: Number(values.retentionCount),
      });
      message.success('S3 连接测试成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'S3 连接测试失败');
    } finally {
      setTestingBackupConfig(false);
    }
  };

  const handleTestDatabaseTools = async () => {
    const values = await backupForm.validateFields();
    setTestingDatabaseTools(true);

    try {
      await testBackupDatabaseTools({
        ...values,
        retentionDays: Number(values.retentionDays),
        retentionCount: Number(values.retentionCount),
      });
      message.success('数据库备份工具测试成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '数据库备份工具测试失败');
    } finally {
      setTestingDatabaseTools(false);
    }
  };

  const handleCreateBackup = async () => {
    setRunningBackup(true);

    try {
      await createBackup();
      message.success('备份已创建');
      await loadBackupConfig();
      await loadBackups();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '备份创建失败，请检查 S3 配置和 pg_dump 命令');
    } finally {
      setRunningBackup(false);
    }
  };

  const confirmRestoreBackup = (backup: BackupItem) => {
    Modal.confirm({
      title: '全量恢复这个备份？',
      content: '恢复会使用该备份覆盖当前数据库和资源文件。恢复前不会自动备份当前数据，请确认你已经手动备份。',
      okText: '确认恢复',
      cancelText: '取消',
      okButtonProps: { danger: true },
      centered: true,
      onOk: async () => {
        setRestoringBackupObjectName(backup.objectName);

        try {
          await restoreBackup(backup.objectName);
          message.success('备份已恢复，请重新登录');
          onRequireLogin?.();
        } catch {
          message.error('备份恢复失败');
        } finally {
          setRestoringBackupObjectName(undefined);
        }
      },
    });
  };

  const confirmDeleteBackup = (backup: BackupItem) => {
    Modal.confirm({
      title: '删除这个备份？',
      content: backup.fileName,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      centered: true,
      onOk: async () => {
        try {
          await deleteBackup(backup.objectName);
          setBackups((currentBackups) => currentBackups.filter((item) => item.objectName !== backup.objectName));
          message.success('备份已删除');
        } catch {
          message.error('备份删除失败');
        }
      },
    });
  };

  return (
    <section className="page-surface">
      <header className="section-header">
        <div>
          <Typography.Title level={3}>系统设置</Typography.Title>
          <Typography.Text type="secondary">配置模型供应商、S3 备份和系统参数。</Typography.Text>
        </div>
        <Button type="primary" icon={<Save size={16} />} onClick={() => void handleSave()}>
          保存设置
        </Button>
      </header>

      <Form
        form={form}
        initialValues={{ providerType: 'openai-compatible', defaultModel: false, systemPrompt: '' }}
        layout="vertical"
      >
        <Tabs
          items={[
            {
              key: 'model',
              label: '模型供应商',
              children: (
                <Card variant="borderless">
                  <List
                    dataSource={modelConfigs}
                    locale={{ emptyText: '还没有聊天模型配置' }}
                    renderItem={(config) => (
                      <List.Item
                        actions={[
                          <Button key="edit" type="text" icon={<Pencil size={15} />} onClick={() => handleEdit(config)}>
                            编辑
                          </Button>,
                          <Button
                            key="default"
                            type="text"
                            icon={<Check size={15} />}
                            disabled={config.defaultModel}
                            onClick={() => void handleSetDefault(config.id)}
                          >
                            设为默认
                          </Button>,
                        ]}
                      >
                        <List.Item.Meta
                          title={
                            <Space>
                              <span>{config.name}</span>
                              {config.defaultModel ? <Tag color="blue">默认</Tag> : null}
                            </Space>
                          }
                          description={`${config.providerType} · ${config.model} · ${config.baseUrl}`}
                        />
                      </List.Item>
                    )}
                  />

                  <Typography.Title level={5}>{editingConfig ? '编辑聊天模型' : '新增聊天模型'}</Typography.Title>
                  <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
                    <Input placeholder="DeepSeek Chat" />
                  </Form.Item>
                  <Form.Item label="供应商类型" name="providerType">
                    <Select
                      options={[
                        { label: 'OpenAI 兼容协议', value: 'openai-compatible' },
                        { label: 'OpenAI API', value: 'openai' },
                        { label: '本地模型', value: 'local' },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item label="API 地址" name="baseUrl" rules={[{ required: true, message: '请输入 API 地址' }]}>
                    <Input placeholder="https://api.example.com/v1" />
                  </Form.Item>
                  <Form.Item label="API Key" name="apiKey" rules={[{ required: true, message: '请输入 API Key' }]}>
                    <Input.Password placeholder={editingConfig ? '编辑时需要重新输入 API Key' : '请输入 API Key'} />
                  </Form.Item>
                  <Form.Item label="模型名" name="model" rules={[{ required: true, message: '请输入模型名' }]}>
                    <Input placeholder="deepseek-chat" />
                  </Form.Item>
                  <Form.Item label="系统提示词" name="systemPrompt">
                    <Input.TextArea autoSize={{ minRows: 4, maxRows: 8 }} />
                  </Form.Item>
                  <Form.Item name="defaultModel" valuePropName="checked">
                    <Checkbox>设为默认聊天模型</Checkbox>
                  </Form.Item>
                  <Space>
                    <Button type="primary" icon={editingConfig ? <Save size={15} /> : <Plus size={15} />} onClick={() => void handleSave()}>
                      {editingConfig ? '保存模型' : '新增模型'}
                    </Button>
                    {editingConfig ? (
                      <Button
                        onClick={() => {
                          setEditingConfig(null);
                          form.resetFields();
                        }}
                      >
                        取消编辑
                      </Button>
                    ) : null}
                  </Space>
                </Card>
              ),
            },
            {
              key: 'embedding',
              label: 'Embedding 模型',
              children: (
                <Card variant="borderless">
                  <List
                    dataSource={embeddingConfigs}
                    locale={{ emptyText: '还没有 Embedding 模型配置' }}
                    renderItem={(config) => (
                      <List.Item
                        actions={[
                          <Button key="edit" type="text" icon={<Pencil size={15} />} onClick={() => handleEditEmbedding(config)}>
                            编辑
                          </Button>,
                          <Button
                            key="default"
                            type="text"
                            icon={<Check size={15} />}
                            disabled={config.defaultModel}
                            onClick={() => void handleSetDefaultEmbedding(config.id)}
                          >
                            设为默认
                          </Button>,
                        ]}
                      >
                        <List.Item.Meta
                          title={
                            <Space>
                              <span>{config.name}</span>
                              {config.defaultModel ? <Tag color="blue">默认</Tag> : null}
                            </Space>
                          }
                          description={`${config.providerType} · ${config.model} · ${config.dimensions} 维 · ${config.baseUrl}`}
                        />
                      </List.Item>
                    )}
                  />

                  <Typography.Title level={5}>
                    {editingEmbeddingConfig ? '编辑 Embedding 模型' : '新增 Embedding 模型'}
                  </Typography.Title>
                  <Form
                    form={embeddingForm}
                    initialValues={{
                      name: '阿里 text-embedding-v4',
                      providerType: 'dashscope-compatible',
                      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
                      model: 'text-embedding-v4',
                      dimensions: 1024,
                      defaultModel: false,
                    }}
                    layout="vertical"
                  >
                    <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
                      <Input placeholder="阿里 text-embedding-v4" />
                    </Form.Item>
                    <Form.Item label="供应商类型" name="providerType">
                      <Select
                        options={[
                          { label: 'DashScope OpenAI 兼容', value: 'dashscope-compatible' },
                          { label: 'OpenAI 兼容协议', value: 'openai-compatible' },
                          { label: '本地 Hash 测试', value: 'local-hash' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item label="API 地址" name="baseUrl" rules={[{ required: true, message: '请输入 API 地址' }]}>
                      <Input placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
                    </Form.Item>
                    <Form.Item label="API Key" name="apiKey" rules={[{ required: true, message: '请输入 API Key' }]}>
                      <Input.Password placeholder={editingEmbeddingConfig ? '编辑时需要重新输入 API Key' : '请输入 DashScope API Key'} />
                    </Form.Item>
                    <Form.Item label="模型名" name="model" rules={[{ required: true, message: '请输入模型名' }]}>
                      <Input placeholder="text-embedding-v4" />
                    </Form.Item>
                    <Form.Item label="向量维度" name="dimensions" rules={[{ required: true, message: '请输入向量维度' }]}>
                      <Input type="number" min={1} max={4096} />
                    </Form.Item>
                    <Form.Item name="defaultModel" valuePropName="checked">
                      <Checkbox>设为默认 Embedding 模型</Checkbox>
                    </Form.Item>
                    <Space>
                      <Button
                        type="primary"
                        icon={editingEmbeddingConfig ? <Save size={15} /> : <Plus size={15} />}
                        onClick={() => void handleSaveEmbedding()}
                      >
                        {editingEmbeddingConfig ? '保存 Embedding' : '新增 Embedding'}
                      </Button>
                      {editingEmbeddingConfig ? (
                        <Button
                          onClick={() => {
                            setEditingEmbeddingConfig(null);
                            embeddingForm.resetFields();
                          }}
                        >
                          取消编辑
                        </Button>
                      ) : null}
                    </Space>
                  </Form>
                </Card>
              ),
            },
            {
              key: 'backup',
              label: 'S3 备份',
              children: (
                <Card variant="borderless">
                  <Form
                    form={backupForm}
                    initialValues={{
                      enabled: false,
                      prefix: 'backups/',
                      cronExpression: '0 2 * * *',
                      retentionDays: 14,
                      retentionCount: 10,
                      pathStyleAccess: true,
                      pgDumpPath: 'pg_dump',
                      psqlPath: 'psql',
                    }}
                    layout="vertical"
                  >
                    <div className="settings-backup-section">
                      <Typography.Title level={4}>S3 存储配置</Typography.Title>
                      <Typography.Paragraph type="secondary">配置 S3 兼容存储，支持 Cloudflare R2。</Typography.Paragraph>
                      <div className="settings-backup-grid">
                        <Form.Item label="端点地址" name="endpoint" rules={[{ required: true, message: '请输入端点地址' }]}>
                          <Input placeholder="https://s3.example.com" />
                        </Form.Item>
                        <Form.Item label="区域" name="region">
                          <Input placeholder="us-east-1" />
                        </Form.Item>
                        <Form.Item label="存储桶" name="bucket" rules={[{ required: true, message: '请输入存储桶' }]}>
                          <Input placeholder="rag-study-backups" />
                        </Form.Item>
                        <Form.Item label="Key 前缀" name="prefix" rules={[{ required: true, message: '请输入 Key 前缀' }]}>
                          <Input placeholder="backups/" />
                        </Form.Item>
                        <Form.Item label="Access Key ID" name="accessKey" rules={[{ required: true, message: '请输入 Access Key ID' }]}>
                          <Input />
                        </Form.Item>
                        <Form.Item
                          label="Secret Access Key"
                          name="secretKey"
                          rules={[{ required: !backupConfig?.secretConfigured, message: '首次保存必须输入 Secret Access Key' }]}
                        >
                          <Input.Password placeholder={backupConfig?.secretConfigured ? '已配置，留空保持不变' : '请输入 Secret Access Key'} />
                        </Form.Item>
                      </div>
                      <Form.Item name="pathStyleAccess" valuePropName="checked">
                        <Checkbox>强制路径风格</Checkbox>
                      </Form.Item>
                      <Space>
                        <Button loading={testingBackupConfig} onClick={() => void handleTestBackupConfig()}>
                          测试连接
                        </Button>
                        <Button type="primary" loading={savingBackupConfig} onClick={() => void handleSaveBackupConfig()}>
                          保存
                        </Button>
                      </Space>
                    </div>

                    <div className="settings-backup-section">
                      <Typography.Title level={4}>数据库备份工具</Typography.Title>
                      <Typography.Paragraph type="secondary">
                        Docker Compose 部署时保持默认即可；本地开发如果没有加入 PATH，可以填写 PostgreSQL 客户端的完整路径。
                      </Typography.Paragraph>
                      <div className="settings-backup-grid">
                        <Form.Item label="pg_dump 路径" name="pgDumpPath">
                          <Input placeholder="pg_dump" />
                        </Form.Item>
                        <Form.Item label="psql 路径" name="psqlPath">
                          <Input placeholder="psql" />
                        </Form.Item>
                      </div>
                      <Space>
                        <Button loading={testingDatabaseTools} onClick={() => void handleTestDatabaseTools()}>
                          测试数据库工具
                        </Button>
                        <Button type="primary" loading={savingBackupConfig} onClick={() => void handleSaveBackupConfig()}>
                          保存
                        </Button>
                      </Space>
                    </div>

                    <div className="settings-backup-section">
                      <Typography.Title level={4}>定时备份</Typography.Title>
                      <Typography.Paragraph type="secondary">配置自动定时备份。</Typography.Paragraph>
                      <Form.Item name="enabled" valuePropName="checked">
                        <Checkbox>启用定时备份</Checkbox>
                      </Form.Item>
                      <div className="settings-backup-grid">
                        <Form.Item label="Cron 表达式" name="cronExpression" rules={[{ required: true, message: '请输入 Cron 表达式' }]}>
                          <Input placeholder="0 2 * * *" />
                        </Form.Item>
                        <Form.Item label="备份过期天数" name="retentionDays" rules={[{ required: true, message: '请输入备份过期天数' }]}>
                          <Input type="number" min={0} max={3650} />
                        </Form.Item>
                        <Form.Item label="最大保留份数" name="retentionCount" rules={[{ required: true, message: '请输入最大保留份数' }]}>
                          <Input type="number" min={0} max={1000} />
                        </Form.Item>
                      </div>
                      <Typography.Paragraph type="secondary">
                        例如 "0 2 * * *" 表示每天凌晨 2 点；过期天数或保留份数为 0 表示不限制。
                      </Typography.Paragraph>
                      <Button type="primary" loading={savingBackupConfig} onClick={() => void handleSaveBackupConfig()}>
                        保存
                      </Button>
                    </div>

                    <Space>
                      <Button icon={<CloudUpload size={15} />} loading={runningBackup} onClick={() => void handleCreateBackup()}>
                        立即备份
                      </Button>
                      <Button onClick={() => void loadBackups()}>刷新列表</Button>
                    </Space>
                    {backupConfig?.lastBackupAt ? (
                      <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
                        上次备份：{formatArchivedConversationTime(backupConfig.lastBackupAt)}
                      </Typography.Paragraph>
                    ) : null}
                  </Form>
                  <Typography.Title level={5} style={{ marginTop: 24 }}>
                    备份列表
                  </Typography.Title>
                  <List
                    dataSource={backups}
                    locale={{ emptyText: '还没有备份' }}
                    renderItem={(backup) => (
                      <List.Item
                        actions={[
                          <Button
                            key="restore"
                            danger
                            type="text"
                            icon={<RotateCcw size={15} />}
                            loading={restoringBackupObjectName === backup.objectName}
                            onClick={() => confirmRestoreBackup(backup)}
                          >
                            恢复
                          </Button>,
                          <Button danger key="delete" type="text" icon={<Trash2 size={15} />} onClick={() => confirmDeleteBackup(backup)}>
                            删除
                          </Button>,
                        ]}
                      >
                        <List.Item.Meta
                          title={backup.fileName}
                          description={`创建时间：${formatArchivedConversationTime(backup.createdAt ?? '')} · 大小：${formatFileSize(
                            backup.size,
                          )}`}
                        />
                      </List.Item>
                    )}
                  />
                </Card>
              ),
            },
            {
              key: 'clipper-proxy',
              label: '剪藏代理',
              children: (
                <Card variant="borderless">
                  <Form
                    form={clipperProxyForm}
                    initialValues={{
                      protocol: 'HTTP',
                      host: '',
                      port: 7890,
                    }}
                    layout="vertical"
                  >
                    <Typography.Title level={4}>剪藏代理设置</Typography.Title>
                    <Typography.Paragraph type="secondary">
                      仅影响网页剪藏的抓取请求。抓取 HTTPS 网页时也选 HTTP 代理；SOCKS5 请使用对应的 SOCKS 端口。
                    </Typography.Paragraph>
                    <div className="settings-backup-grid">
                      <Form.Item label="代理协议" name="protocol" rules={[{ required: true, message: '请选择代理协议' }]}>
                        <Select
                          options={[
                            { label: 'HTTP', value: 'HTTP' },
                            { label: 'SOCKS5', value: 'SOCKS5' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item label="代理地址" name="host" rules={[{ required: true, message: '请输入代理地址' }]}>
                        <Input placeholder="192.168.0.198" />
                      </Form.Item>
                      <Form.Item label="端口" name="port" rules={[{ required: true, message: '请输入端口' }]}>
                        <Input type="number" min={1} max={65535} placeholder="7890" />
                      </Form.Item>
                      <Form.Item label="认证用户名" name="username">
                        <Input placeholder="可选" />
                      </Form.Item>
                      <Form.Item label="认证密码" name="password">
                        <Input.Password placeholder={clipperProxyConfig?.passwordConfigured ? '留空保持不变' : '可选'} />
                      </Form.Item>
                    </div>
                    <Space>
                      <Button loading={testingClipperProxyConfig} onClick={() => void handleTestClipperProxyConfig()}>
                        测试连接
                      </Button>
                      <Button type="primary" loading={savingClipperProxyConfig} onClick={() => void handleSaveClipperProxyConfig()}>
                        保存
                      </Button>
                    </Space>
                  </Form>
                </Card>
              ),
            },
            {
              key: 'archived',
              label: '已归档',
              children: (
                <Card variant="borderless">
                  <List
                    dataSource={archivedConversations}
                    locale={{ emptyText: '还没有归档会话' }}
                    renderItem={(conversation) => (
                      <List.Item
                        actions={[
                          <Button
                            key="restore"
                            type="text"
                            icon={<RotateCcw size={15} />}
                            onClick={() => void handleRestoreConversation(conversation.id)}
                          >
                            恢复
                          </Button>,
                          <Button
                            danger
                            key="delete"
                            type="text"
                            icon={<Trash2 size={15} />}
                            onClick={() => confirmDeleteConversation(conversation)}
                          >
                            删除
                          </Button>,
                        ]}
                      >
                        <List.Item.Meta
                          title={conversation.title}
                          description={`更新时间：${formatArchivedConversationTime(conversation.updatedAt)}`}
                        />
                      </List.Item>
                    )}
                  />
                </Card>
              ),
            },
          ]}
        />
      </Form>
    </section>
  );
}

function formatArchivedConversationTime(updatedAt: string) {
  const date = parseArchivedConversationDate(updatedAt);

  if (Number.isNaN(date.getTime())) {
    return updatedAt || '-';
  }

  const pad = (value: number) => String(value).padStart(2, '0');

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}`;
}

function formatFileSize(size: number) {
  if (!Number.isFinite(size) || size <= 0) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB'];
  let value = size;
  let unitIndex = 0;

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }

  return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function toFivePartCron(cronExpression: string) {
  const parts = cronExpression.trim().split(/\s+/);

  if (parts.length === 6 && parts[0] === '0') {
    return parts.slice(1).join(' ');
  }

  return cronExpression;
}

function parseArchivedConversationDate(updatedAt: string) {
  const legacyDateMatch = updatedAt.match(/^(\d{2})-(\d{2})\s+(\d{2}):(\d{2})$/);

  if (legacyDateMatch) {
    const currentYear = new Date().getFullYear();
    return new Date(
      currentYear,
      Number(legacyDateMatch[1]) - 1,
      Number(legacyDateMatch[2]),
      Number(legacyDateMatch[3]),
      Number(legacyDateMatch[4]),
    );
  }

  return new Date(updatedAt);
}

export default SettingsPage;
