import { Alert, Button, Empty, Form, Input, List, Modal, Radio, Select, Space, Statistic, Tooltip, Typography, message } from 'antd';
import { Clock3, FileText, Globe, Save, Trash2, WandSparkles } from 'lucide-react';
import { Fragment, useEffect, useMemo, useState } from 'react';
import { deleteWebClipHistory, fetchKnowledgeBases, fetchWebClipHistory, fetchWebClipHistoryItem, previewClipper, submitClipper } from '../api/client';
import MarkdownMessage from '../components/MarkdownMessage';
import type { ClipperPreview, ClipperResult, KnowledgeBase, WebClip } from '../types';

function ClipperPage() {
  const [url, setUrl] = useState('');
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [knowledgeBaseId, setKnowledgeBaseId] = useState<string>();
  const [mode, setMode] = useState('auto');
  const [preview, setPreview] = useState<ClipperPreview | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [previewing, setPreviewing] = useState(false);
  const [submittingTarget, setSubmittingTarget] = useState<'clip' | 'knowledge' | null>(null);
  const [knowledgeBasesLoading, setKnowledgeBasesLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyPreviewLoading, setHistoryPreviewLoading] = useState(false);
  const [deletingClipId, setDeletingClipId] = useState<string | null>(null);
  const [selectedClipId, setSelectedClipId] = useState<string | null>(null);
  const [history, setHistory] = useState<WebClip[]>([]);
  const [result, setResult] = useState<ClipperResult | null>(null);
  const groupedHistory = useMemo(() => groupWebClipsByTime(history), [history]);

  const loadHistory = async () => {
    setHistoryLoading(true);

    try {
      setHistory(await fetchWebClipHistory(40));
    } catch {
      message.error('剪藏历史加载失败');
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    setKnowledgeBasesLoading(true);
    fetchKnowledgeBases()
      .then((nextKnowledgeBases) => {
        setKnowledgeBases(nextKnowledgeBases);
        setKnowledgeBaseId((currentId) => currentId ?? nextKnowledgeBases[0]?.id);
      })
      .catch(() => {
        message.error('知识库加载失败');
      })
      .finally(() => {
        setKnowledgeBasesLoading(false);
      });

    void loadHistory();
  }, []);

  const handlePreview = async () => {
    const nextUrl = url.trim();

    if (!nextUrl) {
      message.info('请先输入网页链接');
      return;
    }

    setPreviewing(true);
    setResult(null);
    setSelectedClipId(null);

    try {
      const nextPreview = await previewClipper({ url: nextUrl, mode });
      setPreview(nextPreview);
      setUrl(nextPreview.url);
      setTitle(nextPreview.title);
      setContent(nextPreview.content);

      if (nextPreview.existingClip) {
        message.info('这个 URL 之前已经剪藏过');
      } else {
        message.success('网页已解析，可以预览和调整内容');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '解析失败');
    } finally {
      setPreviewing(false);
    }
  };

  const handleSubmit = async (target: 'clip' | 'knowledge') => {
    if (!url.trim()) {
      message.info('请先输入网页链接');
      return;
    }

    if (target === 'knowledge' && !knowledgeBaseId) {
      message.info('请先选择一个知识库');
      return;
    }

    if (!title.trim() || !content.trim()) {
      message.info('请先抓取预览，或填写标题和正文');
      return;
    }

    setSubmittingTarget(target);

    try {
      const clipperResult = await submitClipper({
        url,
        knowledgeBaseId: target === 'knowledge' ? knowledgeBaseId : undefined,
        title,
        content,
        target,
        mode,
      });
      setResult(clipperResult);
      message.success(target === 'knowledge' ? '网页已保存到知识库' : '剪藏已保存');
      await loadHistory();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSubmittingTarget(null);
    }
  };

  const handleClear = () => {
    setUrl('');
    setPreview(null);
    setTitle('');
    setContent('');
    setResult(null);
    setSelectedClipId(null);
  };

  const handleHistorySelect = async (clip: WebClip) => {
    setUrl(clip.url);
    setKnowledgeBaseId(clip.knowledgeBaseId ?? undefined);
    setResult(null);
    setSelectedClipId(clip.id);
    setHistoryPreviewLoading(true);

    try {
      const detail = await fetchWebClipHistoryItem(clip.id);
      const detailContent = detail.content ?? '';
      setTitle(detail.title);
      setContent(detailContent);
      setPreview({
        url: detail.url,
        title: detail.title,
        content: detailContent,
        excerpt: detail.excerpt,
        siteName: detail.siteName,
        contentType: 'text/markdown',
        wordCount: detailContent.replace(/\s+/g, '').length,
        existingClip: detail,
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '历史剪藏加载失败');
    } finally {
      setHistoryPreviewLoading(false);
    }
  };

  const confirmDeleteHistory = (clip: WebClip) => {
    Modal.confirm({
      title: '删除这条剪藏？',
      content: clip.documentId
        ? `将删除「${clip.title}」的剪藏历史、知识库文档和本地图片资源。`
        : `将删除「${clip.title}」的剪藏历史和本地图片资源。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setDeletingClipId(clip.id);

        try {
          await deleteWebClipHistory(clip.id);
          setHistory((items) => items.filter((item) => item.id !== clip.id));

          if (selectedClipId === clip.id || preview?.existingClip?.id === clip.id || result?.id === clip.documentId || result?.id === clip.id) {
            handleClear();
          }

          message.success('剪藏已删除');
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        } finally {
          setDeletingClipId((currentId) => (currentId === clip.id ? null : currentId));
        }
      },
    });
  };

  return (
    <section className="clipper-page">
      <div className="clipper-workspace">
        <aside className="clipper-history-panel">
          <div className="clipper-history-header">
            <Space>
              <Clock3 size={16} />
              <Typography.Text strong>剪藏历史</Typography.Text>
            </Space>
            <Button type="text" size="small" loading={historyLoading} onClick={() => void loadHistory()}>
              刷新
            </Button>
          </div>

          {history.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有剪藏历史" />
          ) : (
            <div className="clipper-history-groups">
              {groupedHistory.map((group) => (
                <Fragment key={group.label}>
                  <div className="clipper-history-group-title">{group.label}</div>
                  <List
                    className="clipper-history-list"
                    split={false}
                    dataSource={group.items}
                    renderItem={(clip) => (
                      <List.Item
                        className={`clipper-history-item${selectedClipId === clip.id ? ' active' : ''}`}
                        onClick={() => void handleHistorySelect(clip)}
                      >
                        <div className="clipper-history-row">
                          <div className="clipper-history-content">
                            <Typography.Text className="clipper-history-title">{clip.title}</Typography.Text>
                            <Typography.Text className="clipper-history-url" type="secondary">
                              {clip.url}
                            </Typography.Text>
                          </div>
                          <Tooltip title="删除剪藏">
                            <Button
                              type="text"
                              size="small"
                              danger
                              className="clipper-history-delete"
                              icon={<Trash2 size={14} />}
                              loading={deletingClipId === clip.id}
                              onClick={(event) => {
                                event.stopPropagation();
                                confirmDeleteHistory(clip);
                              }}
                            />
                          </Tooltip>
                        </div>
                      </List.Item>
                    )}
                  />
                </Fragment>
              ))}
            </div>
          )}
        </aside>

        <div className="clipper-control-panel">
          <div className="search-hero clipper-hero">
            <Typography.Title level={2}>剪藏网页内容</Typography.Title>
            <Typography.Text type="secondary">抓取正文，预览修正后保存为剪藏</Typography.Text>
          </div>

          <Form className="clipper-form" layout="vertical">
            <Form.Item label="网页链接">
              <Input
                className="clipper-url-input"
                size="large"
                prefix={<Globe size={18} />}
                placeholder="粘贴网页链接，例如 https://example.com/article"
                value={url}
                onChange={(event) => setUrl(event.target.value)}
              />
            </Form.Item>

            <Form.Item label="知识库（可选）">
              <Select
                loading={knowledgeBasesLoading}
                allowClear
                placeholder="需要入库时选择知识库"
                value={knowledgeBaseId}
                onChange={setKnowledgeBaseId}
                options={knowledgeBases.map((knowledgeBase) => ({
                  label: knowledgeBase.name,
                  value: knowledgeBase.id,
                }))}
              />
            </Form.Item>

            <Form.Item label="抓取模式">
              <Radio.Group className="clipper-mode-group" value={mode} onChange={(event) => setMode(event.target.value)}>
                <Radio value="auto">智能正文</Radio>
                <Radio value="full">完整页面</Radio>
              </Radio.Group>
            </Form.Item>

            <Space wrap>
              <Button icon={<WandSparkles size={16} />} loading={previewing} onClick={handlePreview}>
                抓取预览
              </Button>
              <Button type="primary" icon={<Save size={16} />} loading={submittingTarget === 'clip'} onClick={() => void handleSubmit('clip')}>
                保存剪藏
              </Button>
              <Button icon={<Save size={16} />} loading={submittingTarget === 'knowledge'} onClick={() => void handleSubmit('knowledge')}>
                保存到知识库
              </Button>
              <Button onClick={handleClear}>清空</Button>
            </Space>

            {preview ? (
              <div className="clipper-meta">
                <Statistic title="站点" value={preview.siteName || '-'} />
                <Statistic title="字数" value={preview.wordCount} />
                <Statistic title="类型" value={preview.contentType.split(';')[0]} />
              </div>
            ) : null}

            {preview?.existingClip ? (
              <Alert
                showIcon
                type="warning"
                message="这个网页已剪藏过"
                description={`上次保存：${preview.existingClip.updatedAt}，标题：${preview.existingClip.title}`}
              />
            ) : null}

            {result ? (
              <Alert
                showIcon
                type="success"
                message={result.target === 'knowledge' ? '网页已保存到知识库' : '剪藏已保存'}
                description={`${result.target === 'knowledge' ? '文档' : '剪藏'} ${result.id}，标题：${result.title}，状态：${result.status}`}
              />
            ) : null}
          </Form>
        </div>

        <div className="clipper-preview-panel">
          <div className="clipper-preview-header">
            <Space>
              <FileText size={18} />
              <Typography.Title level={4}>预览与编辑</Typography.Title>
            </Space>
            {preview?.excerpt ? <Typography.Text type="secondary">{preview.excerpt}</Typography.Text> : null}
          </div>

          <Form layout="vertical" className="clipper-editor-form">
            <Form.Item label="标题">
              <Input placeholder="抓取后会自动填入标题" value={title} onChange={(event) => setTitle(event.target.value)} />
            </Form.Item>
            <div className="clipper-rendered-preview">
              {content ? (
                <MarkdownMessage content={content} />
              ) : (
                <Empty description={historyPreviewLoading ? '正在加载历史剪藏' : '点击“抓取预览”后在这里查看正文'} />
              )}
            </div>
          </Form>
        </div>
      </div>
    </section>
  );
}

type WebClipTimeGroup = {
  label: string;
  items: WebClip[];
};

function groupWebClipsByTime(clips: WebClip[]): WebClipTimeGroup[] {
  const groups: WebClipTimeGroup[] = [
    { label: '今天', items: [] },
    { label: '过去 7 天', items: [] },
    { label: '过去 30 天', items: [] },
    { label: '更早', items: [] },
  ];

  clips.forEach((clip) => {
    const days = getDaysSince(clip.updatedAt);

    if (days <= 0) {
      groups[0].items.push(clip);
      return;
    }

    if (days <= 7) {
      groups[1].items.push(clip);
      return;
    }

    if (days <= 30) {
      groups[2].items.push(clip);
      return;
    }

    groups[3].items.push(clip);
  });

  return groups.filter((group) => group.items.length > 0);
}

function getDaysSince(updatedAt: string) {
  const updatedDate = parseClipDate(updatedAt);

  if (Number.isNaN(updatedDate.getTime())) {
    return Number.POSITIVE_INFINITY;
  }

  const today = new Date();
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  const updatedStart = new Date(updatedDate.getFullYear(), updatedDate.getMonth(), updatedDate.getDate()).getTime();

  return Math.floor((todayStart - updatedStart) / 86_400_000);
}

function parseClipDate(updatedAt: string) {
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

export default ClipperPage;
