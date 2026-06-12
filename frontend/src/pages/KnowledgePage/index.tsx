import { Badge, Button, Card, Descriptions, Dropdown, Empty, Form, Input, Modal, Space, Table, Typography, Upload, message } from 'antd';
import type { MenuProps } from 'antd';
import type { UploadProps } from 'antd';
import { Database, Ellipsis, FileText, Pencil, Plus, RefreshCw, Trash2, UploadCloud } from 'lucide-react';
import {
  batchDeleteKnowledgeDocuments,
  createKnowledgeBase,
  createKnowledgeDocument,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  fetchKnowledgeBases,
  fetchKnowledgeDocuments,
  rebuildKnowledgeDocumentIndex,
  rebuildKnowledgeIndex,
  updateKnowledgeBase,
  updateKnowledgeDocument,
  uploadKnowledgeDocument,
} from '../../api/client';
import { useEffect, useState } from 'react';
import type { KnowledgeBase, KnowledgeDocument } from '../../types';
import './KnowledgePage.css';

const statusMap: Record<KnowledgeBase['vectorStatus'], { text: string; status: 'success' | 'processing' | 'default' }> = {
  ready: { text: '已完成', status: 'success' },
  indexing: { text: '索引中', status: 'processing' },
  empty: { text: '空知识库', status: 'default' },
};

const defaultDocumentPageSize = 10;

type KnowledgeBaseFormValues = {
  name: string;
  description: string;
};

type KnowledgeDocumentFormValues = {
  title: string;
  rawContent: string;
};

type KnowledgePageProps = {
  selectedDocumentId?: string;
  selectedKnowledgeBaseId?: string;
};

function KnowledgePage({ selectedDocumentId, selectedKnowledgeBaseId: selectedKnowledgeBaseIdProp }: KnowledgePageProps) {
  const [knowledgeBaseForm] = Form.useForm<KnowledgeBaseFormValues>();
  const [documentForm] = Form.useForm<KnowledgeDocumentFormValues>();
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [documentPagination, setDocumentPagination] = useState({
    current: 1,
    pageSize: defaultDocumentPageSize,
    total: 0,
  });
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<string>();
  const [editingKnowledgeBase, setEditingKnowledgeBase] = useState<KnowledgeBase | null>(null);
  const [editingDocument, setEditingDocument] = useState<KnowledgeDocument | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [documentFormOpen, setDocumentFormOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [documentsLoading, setDocumentsLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [documentSaving, setDocumentSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [indexing, setIndexing] = useState(false);
  const [indexingDocumentId, setIndexingDocumentId] = useState<string>();
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([]);
  const [batchDeleting, setBatchDeleting] = useState(false);
  const selectedKnowledgeBase = knowledgeBases.find((knowledgeBase) => knowledgeBase.id === selectedKnowledgeBaseId);

  const loadKnowledgeBases = async () => {
    setLoading(true);

    try {
      const nextKnowledgeBases = await fetchKnowledgeBases();
      setKnowledgeBases(nextKnowledgeBases);
      setSelectedKnowledgeBaseId((currentId) => {
        if (selectedKnowledgeBaseIdProp && nextKnowledgeBases.some((knowledgeBase) => knowledgeBase.id === selectedKnowledgeBaseIdProp)) {
          return selectedKnowledgeBaseIdProp;
        }

        return currentId ?? nextKnowledgeBases[0]?.id;
      });
    } catch {
      message.error('知识库加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadKnowledgeBases();
  }, []);

  useEffect(() => {
    if (selectedKnowledgeBaseIdProp) {
      setSelectedKnowledgeBaseId(selectedKnowledgeBaseIdProp);
    }
  }, [selectedKnowledgeBaseIdProp]);

  const loadDocuments = async (knowledgeBaseId: string, page = documentPagination.current, pageSize = documentPagination.pageSize) => {
    setDocumentsLoading(true);

    try {
      const documentPage = await fetchKnowledgeDocuments(knowledgeBaseId, page, pageSize);
      setDocuments(documentPage.items);
      setSelectedDocumentIds((currentIds) => {
        const visibleDocumentIds = new Set(documentPage.items.map((document) => document.id));
        return currentIds.filter((documentId) => visibleDocumentIds.has(documentId));
      });
      setDocumentPagination({
        current: documentPage.page,
        pageSize: documentPage.pageSize,
        total: documentPage.total,
      });
    } catch {
      setDocuments([]);
      setDocumentPagination((currentPagination) => ({ ...currentPagination, total: 0 }));
      message.error('文档加载失败');
    } finally {
      setDocumentsLoading(false);
    }
  };

  useEffect(() => {
    if (!selectedKnowledgeBaseId) {
      setDocuments([]);
      setDocumentPagination((currentPagination) => ({ ...currentPagination, current: 1, total: 0 }));
      return;
    }

    void loadDocuments(selectedKnowledgeBaseId, 1, documentPagination.pageSize);
  }, [selectedKnowledgeBaseId]);

  useEffect(() => {
    setSelectedDocumentIds([]);
  }, [selectedKnowledgeBaseId]);

  const openCreateForm = () => {
    setEditingKnowledgeBase(null);
    knowledgeBaseForm.resetFields();
    setFormOpen(true);
  };

  const openEditForm = (knowledgeBase: KnowledgeBase) => {
    setEditingKnowledgeBase(knowledgeBase);
    knowledgeBaseForm.setFieldsValue({
      name: knowledgeBase.name,
      description: knowledgeBase.description,
    });
    setFormOpen(true);
  };

  const handleSave = async () => {
    const values = await knowledgeBaseForm.validateFields();
    setSaving(true);

    try {
      if (editingKnowledgeBase) {
        await updateKnowledgeBase(editingKnowledgeBase.id, values);
        message.success('知识库已更新');
      } else {
        await createKnowledgeBase(values);
        message.success('知识库已创建');
      }

      setFormOpen(false);
      setEditingKnowledgeBase(null);
      knowledgeBaseForm.resetFields();
      await loadKnowledgeBases();
    } catch {
      message.error(editingKnowledgeBase ? '知识库更新失败' : '知识库创建失败');
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = (knowledgeBase: KnowledgeBase) => {
    Modal.confirm({
      title: '删除这个知识库？',
      content: `将删除「${knowledgeBase.name}」的知识库记录。后续接入文档后，会同步清理文档与向量索引。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteKnowledgeBase(knowledgeBase.id);
          setKnowledgeBases((currentKnowledgeBases) =>
            currentKnowledgeBases.filter((currentKnowledgeBase) => currentKnowledgeBase.id !== knowledgeBase.id),
          );
          setSelectedKnowledgeBaseId((currentId) => (currentId === knowledgeBase.id ? undefined : currentId));
          message.success('知识库已删除');
        } catch {
          message.error('知识库删除失败');
        }
      },
    });
  };

  const getKnowledgeBaseMenuItems = (): MenuProps['items'] => [
    { key: 'edit', icon: <Pencil size={15} />, label: '编辑' },
    { key: 'delete', icon: <Trash2 size={15} />, label: <span className="danger-menu-item">删除</span> },
  ];

  const openCreateDocumentForm = () => {
    if (!selectedKnowledgeBaseId) {
      message.info('请先选择一个知识库');
      return;
    }

    setEditingDocument(null);
    documentForm.resetFields();
    setDocumentFormOpen(true);
  };

  const openEditDocumentForm = (document: KnowledgeDocument) => {
    setEditingDocument(document);
    documentForm.setFieldsValue({
      title: document.title,
      rawContent: document.rawContent,
    });
    setDocumentFormOpen(true);
  };

  const handleSaveDocument = async () => {
    if (!selectedKnowledgeBaseId) {
      return;
    }

    const values = await documentForm.validateFields();
    setDocumentSaving(true);

    try {
      if (editingDocument) {
        await updateKnowledgeDocument(selectedKnowledgeBaseId, editingDocument.id, values);
        message.success('文档已更新');
      } else {
        await createKnowledgeDocument(selectedKnowledgeBaseId, values);
        message.success('文档已新增');
      }

      setDocumentFormOpen(false);
      setEditingDocument(null);
      documentForm.resetFields();
      await loadDocuments(selectedKnowledgeBaseId, editingDocument ? documentPagination.current : 1, documentPagination.pageSize);
      await loadKnowledgeBases();
    } catch {
      message.error(editingDocument ? '文档更新失败' : '文档新增失败');
    } finally {
      setDocumentSaving(false);
    }
  };

  const confirmDeleteDocument = (document: KnowledgeDocument) => {
    Modal.confirm({
      title: '删除这个文档？',
      content: `将删除「${document.title}」。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteKnowledgeDocument(document.knowledgeBaseId, document.id);
          const nextTotal = Math.max(0, documentPagination.total - 1);
          const nextMaxPage = Math.max(1, Math.ceil(nextTotal / documentPagination.pageSize));
          await loadDocuments(
            document.knowledgeBaseId,
            Math.min(documentPagination.current, nextMaxPage),
            documentPagination.pageSize,
          );
          await loadKnowledgeBases();
          message.success('文档已删除');
        } catch {
          message.error('文档删除失败');
        }
      },
    });
  };

  const confirmBatchDeleteDocuments = () => {
    if (!selectedKnowledgeBaseId || selectedDocumentIds.length === 0) {
      return;
    }

    Modal.confirm({
      title: `删除选中的 ${selectedDocumentIds.length} 个文档？`,
      content: '将同步删除文档、切片和向量索引。这个操作不可恢复。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setBatchDeleting(true);

        try {
          await batchDeleteKnowledgeDocuments(selectedKnowledgeBaseId, selectedDocumentIds);
          const nextTotal = Math.max(0, documentPagination.total - selectedDocumentIds.length);
          const nextMaxPage = Math.max(1, Math.ceil(nextTotal / documentPagination.pageSize));
          setSelectedDocumentIds([]);
          await loadDocuments(selectedKnowledgeBaseId, Math.min(documentPagination.current, nextMaxPage), documentPagination.pageSize);
          await loadKnowledgeBases();
          message.success('选中文档已删除');
        } catch {
          message.error('批量删除失败');
        } finally {
          setBatchDeleting(false);
        }
      },
    });
  };

  const handleUpload: UploadProps['beforeUpload'] = async (file) => {
    if (!selectedKnowledgeBaseId) {
      message.info('请先选择一个知识库');
      return Upload.LIST_IGNORE;
    }

    setUploading(true);

    try {
      await uploadKnowledgeDocument(selectedKnowledgeBaseId, file);
      message.success('文件已上传');
      await loadDocuments(selectedKnowledgeBaseId, 1, documentPagination.pageSize);
      await loadKnowledgeBases();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文件上传失败');
    } finally {
      setUploading(false);
    }

    return Upload.LIST_IGNORE;
  };

  const handleRebuildIndex = async () => {
    if (!selectedKnowledgeBaseId) {
      message.info('请先选择一个知识库');
      return;
    }

    setIndexing(true);

    try {
      const result = await rebuildKnowledgeIndex(selectedKnowledgeBaseId);
      message.success(`索引已重建：${result.indexedChunks} 个分块，模型：${result.embeddingModel}`);
      await loadDocuments(selectedKnowledgeBaseId, documentPagination.current, documentPagination.pageSize);
      await loadKnowledgeBases();
    } catch {
      message.error('索引重建失败，请检查 Qdrant 是否可访问');
    } finally {
      setIndexing(false);
    }
  };

  const handleRebuildDocumentIndex = async (document: KnowledgeDocument) => {
    setIndexingDocumentId(document.id);

    try {
      const result = await rebuildKnowledgeDocumentIndex(document.knowledgeBaseId, document.id);
      message.success(`文档索引已重建：${result.indexedChunks} 个分块，模型：${result.embeddingModel}`);
      await loadDocuments(document.knowledgeBaseId, documentPagination.current, documentPagination.pageSize);
      await loadKnowledgeBases();
    } catch {
      message.error('文档索引重建失败');
    } finally {
      setIndexingDocumentId(undefined);
    }
  };

  return (
    <section className="page-surface">
      <header className="section-header">
        <div>
          <Typography.Title level={3}>知识库</Typography.Title>
          <Typography.Text type="secondary">管理文档、网页剪藏和笔记向量索引。</Typography.Text>
        </div>
        <Space>
          <Button icon={<RefreshCw size={16} />} loading={loading} onClick={() => void loadKnowledgeBases()}>
            刷新
          </Button>
          <Button icon={<RefreshCw size={16} />} disabled={!selectedKnowledgeBaseId} loading={indexing} onClick={() => void handleRebuildIndex()}>
            重建索引
          </Button>
          <Button type="primary" icon={<Plus size={16} />} onClick={openCreateForm}>
            新建知识库
          </Button>
        </Space>
      </header>

      <div className="knowledge-grid">
        {knowledgeBases.map((kb) => (
          <Card
            key={kb.id}
            className={kb.id === selectedKnowledgeBaseId ? 'knowledge-card active' : 'knowledge-card'}
            onClick={() => setSelectedKnowledgeBaseId(kb.id)}
            title={
              <Space>
                <Database size={18} />
                {kb.name}
              </Space>
            }
            extra={
              <Space size={8}>
                <Badge status={statusMap[kb.vectorStatus].status} text={statusMap[kb.vectorStatus].text} />
                <Dropdown
                  menu={{
                    items: getKnowledgeBaseMenuItems(),
                    onClick: ({ key, domEvent }) => {
                      domEvent.stopPropagation();

                      if (key === 'edit') {
                        openEditForm(kb);
                      }

                      if (key === 'delete') {
                        confirmDelete(kb);
                      }
                    },
                  }}
                  trigger={['click']}
                >
                  <Button type="text" shape="circle" icon={<Ellipsis size={16} />} onClick={(event) => event.stopPropagation()} />
                </Dropdown>
              </Space>
            }
          >
            <Typography.Paragraph type="secondary">
              {kb.description || '还没有描述。可以先创建知识库，后续再接入文档、网页剪藏和笔记。'}
            </Typography.Paragraph>
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="文档">{kb.documentCount}</Descriptions.Item>
              <Descriptions.Item label="分块">{kb.chunkCount}</Descriptions.Item>
            </Descriptions>
          </Card>
        ))}
      </div>

      <Card
        title={selectedKnowledgeBase ? `${selectedKnowledgeBase.name} / 文档` : '文档'}
        className="page-card"
        extra={
          <Space>
            <Button
              danger
              icon={<Trash2 size={15} />}
              disabled={!selectedKnowledgeBaseId || selectedDocumentIds.length === 0}
              loading={batchDeleting}
              onClick={confirmBatchDeleteDocuments}
            >
              删除选中{selectedDocumentIds.length > 0 ? ` (${selectedDocumentIds.length})` : ''}
            </Button>
            <Upload showUploadList={false} beforeUpload={handleUpload}>
              <Button icon={<UploadCloud size={15} />} disabled={!selectedKnowledgeBaseId} loading={uploading}>
                上传文件
              </Button>
            </Upload>
            <Button type="primary" icon={<Plus size={15} />} disabled={!selectedKnowledgeBaseId} onClick={openCreateDocumentForm}>
              新增文档
            </Button>
          </Space>
        }
      >
        {selectedKnowledgeBaseId ? (
          <Table
            rowKey="id"
            loading={documentsLoading}
            rowSelection={{
              selectedRowKeys: selectedDocumentIds,
              preserveSelectedRowKeys: false,
              onChange: (selectedRowKeys) => setSelectedDocumentIds(selectedRowKeys.map(String)),
            }}
            rowClassName={(document) => (document.id === selectedDocumentId ? 'knowledge-document-row active' : '')}
            pagination={{
              current: documentPagination.current,
              pageSize: documentPagination.pageSize,
              total: documentPagination.total,
              showSizeChanger: true,
              showQuickJumper: documentPagination.total > documentPagination.pageSize,
              pageSizeOptions: [10, 20, 50],
              showTotal: (total, range) => `${range[0]}-${range[1]} / 共 ${total} 个文档`,
              onChange: (current, pageSize) => {
                setDocumentPagination((currentPagination) => ({ ...currentPagination, current, pageSize }));
                void loadDocuments(selectedKnowledgeBaseId, current, pageSize);
              },
            }}
            dataSource={documents}
            locale={{ emptyText: <Empty description="这个知识库还没有文档" /> }}
            columns={[
              {
                title: '名称',
                dataIndex: 'title',
                render: (title: string) => (
                  <Space>
                    <FileText size={15} />
                    {title}
                  </Space>
                ),
              },
              {
                title: '来源',
                dataIndex: 'sourceType',
                render: (sourceType: KnowledgeDocument['sourceType']) => {
                  if (sourceType === 'upload') {
                    return '文件上传';
                  }

                  if (sourceType === 'web') {
                    return '网页剪藏';
                  }

                  if (sourceType === 'note') {
                    return '笔记同步';
                  }

                  return '手动录入';
                },
              },
              {
                title: '状态',
                dataIndex: 'vectorStatus',
                render: (vectorStatus: KnowledgeDocument['vectorStatus']) =>
                  vectorStatus === 'ready' ? '已向量化' : vectorStatus === 'failed' ? '失败' : '待向量化',
              },
              { title: '分块', dataIndex: 'chunkCount' },
              { title: '更新时间', dataIndex: 'updatedAt' },
              {
                title: '操作',
                width: 260,
                render: (_, document) => (
                  <Space>
                    <Button
                      type="text"
                      icon={<RefreshCw size={15} />}
                      loading={indexingDocumentId === document.id}
                      onClick={() => void handleRebuildDocumentIndex(document)}
                    >
                      重建索引
                    </Button>
                    <Button type="text" icon={<Pencil size={15} />} onClick={() => openEditDocumentForm(document)}>
                      编辑
                    </Button>
                    <Button
                      danger
                      type="text"
                      icon={<Trash2 size={15} />}
                      onClick={() => confirmDeleteDocument(document)}
                    >
                      删除
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        ) : (
          <Empty description="请先新建或选择一个知识库" />
        )}
      </Card>

      <Modal
        title={editingKnowledgeBase ? '编辑知识库' : '新建知识库'}
        open={formOpen}
        okText={editingKnowledgeBase ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saving}
        onCancel={() => {
          setFormOpen(false);
          setEditingKnowledgeBase(null);
          knowledgeBaseForm.resetFields();
        }}
        onOk={() => void handleSave()}
      >
        <Form form={knowledgeBaseForm} layout="vertical">
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入知识库名称' }]}>
            <Input placeholder="项目知识库" />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 6 }} placeholder="说明这个知识库收纳哪些资料" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingDocument ? '编辑文档' : '新增文档'}
        open={documentFormOpen}
        okText={editingDocument ? '保存' : '新增'}
        cancelText="取消"
        confirmLoading={documentSaving}
        width={760}
        onCancel={() => {
          setDocumentFormOpen(false);
          setEditingDocument(null);
          documentForm.resetFields();
        }}
        onOk={() => void handleSaveDocument()}
      >
        <Form form={documentForm} layout="vertical">
          <Form.Item label="标题" name="title" rules={[{ required: true, message: '请输入文档标题' }]}>
            <Input placeholder="项目技术栈.md" />
          </Form.Item>
          <Form.Item label="内容" name="rawContent" rules={[{ required: true, message: '请输入文档内容' }]}>
            <Input.TextArea
              autoSize={{ minRows: 12, maxRows: 20 }}
              placeholder="粘贴 Markdown、网页正文或资料摘要。后续会把这里的内容用于切块和向量化。"
            />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}

export default KnowledgePage;
