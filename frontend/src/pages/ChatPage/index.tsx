import { Bubble, Sender } from '@ant-design/x';
import { Avatar, Button, Input, Select, Space, Typography, message } from 'antd';
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Database,
  FileText,
  Folder,
  FolderOpen,
  MessageCircle,
  Paperclip,
  Plus,
  Settings2,
  X,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react';
import {
  fetchChatConversations,
  fetchChatMessages,
  fetchChatModelConfigs,
  fetchKnowledgeBases,
  fetchNoteFolders,
  fetchNotes,
  streamChatMessage,
} from '../../api/client';
import MarkdownMessage from '../../components/MarkdownMessage';
import VditorPreviewMessage from '../../components/VditorPreviewMessage';
import { useApiResource } from '../../hooks/useApiResource';
import type { ChatConversation, ChatMessage, ChatModelConfig, ChatReference, KnowledgeBase, Note, NoteFolder } from '../../types';
import './ChatPage.css';

const referenceActions = [
  { key: 'file', label: '上传文件', icon: Paperclip },
  { key: 'note', label: '引用笔记', icon: FileText, hasSubmenu: true },
  { key: 'knowledge', label: '引用知识库', icon: Database, hasSubmenu: true },
  { key: 'conversation', label: '引用其他对话', icon: MessageCircle, hasSubmenu: true },
];

const fallbackModelConfigs: ChatModelConfig[] = [];
const fallbackKnowledgeBases: KnowledgeBase[] = [];
const fallbackNotes: Note[] = [];
const fallbackNoteFolders: NoteFolder[] = [];
const fallbackChatConversations: ChatConversation[] = [];
const MAX_REFERENCE_CHARS = 8000;
const MAX_FILE_CHARS = 20000;
const TEXT_FILE_EXTENSIONS = new Set([
  'csv',
  'html',
  'json',
  'log',
  'md',
  'markdown',
  'text',
  'ts',
  'tsx',
  'txt',
  'xml',
  'yaml',
  'yml',
]);

type ReferenceMenuView = 'main' | 'knowledge' | 'notes' | 'conversations';

type ReferenceNoteTreeNode = ReferenceNoteFolderTreeNode | ReferenceNoteFileTreeNode;

type ReferenceNoteFolderTreeNode = {
  type: 'folder';
  key: string;
  folderId?: string;
  name: string;
  children: ReferenceNoteTreeNode[];
};

type ReferenceNoteFileTreeNode = {
  type: 'note';
  key: string;
  name: string;
  note: Note;
};

type MutableReferenceNoteFolderTreeNode = ReferenceNoteFolderTreeNode & {
  folderMap: Map<string, MutableReferenceNoteFolderTreeNode>;
};

function fetchActiveChatConversations() {
  return fetchChatConversations(false);
}

type ChatPageProps = {
  newChatVersion: number;
  onConversationCreated: (conversationId: string) => void;
  selectedConversationId?: string;
};

function ChatPage({ newChatVersion, onConversationCreated, selectedConversationId }: ChatPageProps) {
  const [referenceMenuOpen, setReferenceMenuOpen] = useState(false);
  const [referenceMenuView, setReferenceMenuView] = useState<ReferenceMenuView>('main');
  const [hasConversation, setHasConversation] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [selectedModelConfigId, setSelectedModelConfigId] = useState<string>();
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<string>();
  const [selectedReferences, setSelectedReferences] = useState<ChatReference[]>([]);
  const [openNoteFolderKeys, setOpenNoteFolderKeys] = useState<Set<string>>(new Set());
  const senderWrapRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const streamingCreatedConversationRef = useRef<string | undefined>(undefined);
  const streamWatchdogRef = useRef<number | undefined>(undefined);
  const [currentConversationId, setCurrentConversationId] = useState<string | undefined>(selectedConversationId);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const { data: knowledgeBases } = useApiResource(fetchKnowledgeBases, fallbackKnowledgeBases);
  const { data: modelConfigs } = useApiResource(fetchChatModelConfigs, fallbackModelConfigs);
  const { data: notes } = useApiResource(fetchNotes, fallbackNotes);
  const { data: noteFolders } = useApiResource(fetchNoteFolders, fallbackNoteFolders);
  const { data: conversations } = useApiResource(fetchActiveChatConversations, fallbackChatConversations);
  const visibleMessages = useMemo(() => (hasConversation ? chatMessages : []), [chatMessages, hasConversation]);
  const noteTree = useMemo(() => buildReferenceNoteTree(notes, noteFolders), [noteFolders, notes]);
  const selectableConversations = useMemo(
    () => conversations.filter((conversation) => conversation.id !== currentConversationId),
    [conversations, currentConversationId],
  );

  const defaultModelConfigId = useMemo(() => {
    return (modelConfigs.find((config) => config.defaultModel) ?? modelConfigs[0])?.id;
  }, [modelConfigs]);

  const selectedKnowledgeBase = useMemo(() => {
    return knowledgeBases.find((knowledgeBase) => knowledgeBase.id === selectedKnowledgeBaseId);
  }, [knowledgeBases, selectedKnowledgeBaseId]);

  useEffect(() => {
    if (!selectedConversationId || selectedConversationId === currentConversationId) {
      return;
    }

    if (selectedConversationId === streamingCreatedConversationRef.current) {
      streamingCreatedConversationRef.current = undefined;
      return;
    }

    setCurrentConversationId(selectedConversationId);

    fetchChatMessages(selectedConversationId)
      .then((messages) => {
        setChatMessages(messages);
        setHasConversation(messages.length > 0);
      })
      .catch(() => message.error('会话消息加载失败'));
  }, [currentConversationId, selectedConversationId]);

  useEffect(() => {
    if (modelConfigs.length === 0) {
      return;
    }

    if ((!hasConversation || !selectedModelConfigId) && defaultModelConfigId) {
      setSelectedModelConfigId(defaultModelConfigId);
    }
  }, [defaultModelConfigId, hasConversation, modelConfigs.length, selectedModelConfigId]);

  useEffect(() => {
    if (newChatVersion === 0) {
      return;
    }

    setHasConversation(false);
    setInputValue('');
    setChatMessages([]);
    setCurrentConversationId(undefined);
    setSelectedModelConfigId(defaultModelConfigId);
    setSelectedKnowledgeBaseId(undefined);
    setSelectedReferences([]);
    setReferenceMenuOpen(false);
    setReferenceMenuView('main');
  }, [defaultModelConfigId, newChatVersion]);

  useEffect(() => {
    if (!referenceMenuOpen) {
      return;
    }

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;

      if (target instanceof Node && senderWrapRef.current?.contains(target)) {
        return;
      }

      setReferenceMenuOpen(false);
      setReferenceMenuView('main');
    };

    document.addEventListener('pointerdown', handlePointerDown);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
    };
  }, [referenceMenuOpen]);

  useEffect(() => {
    return () => {
      window.clearTimeout(streamWatchdogRef.current);
    };
  }, []);

  useEffect(() => {
    setOpenNoteFolderKeys(new Set(collectReferenceNoteFolderKeys(noteTree)));
  }, [noteTree]);

  const addSelectedReference = (reference: ChatReference) => {
    setSelectedReferences((currentReferences) => {
      if (currentReferences.some((item) => item.type === reference.type && item.id === reference.id)) {
        return currentReferences;
      }

      return [...currentReferences, reference];
    });
  };

  const closeReferenceMenu = () => {
    setReferenceMenuOpen(false);
    setReferenceMenuView('main');
  };

  const handleReferenceAction = (actionKey: string) => {
    if (actionKey === 'file') {
      fileInputRef.current?.click();
      closeReferenceMenu();
      return;
    }

    if (actionKey === 'note') {
      setReferenceMenuView('notes');
      return;
    }

    if (actionKey === 'knowledge') {
      setReferenceMenuView('knowledge');
      return;
    }

    if (actionKey === 'conversation') {
      setReferenceMenuView('conversations');
    }
  };

  const handleFileSelected = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';

    if (!file) {
      return;
    }

    if (!isReadableTextFile(file)) {
      message.warning('当前仅支持文本类文件作为对话上下文');
      return;
    }

    try {
      const text = await file.text();
      const content = text.trim();

      if (!content) {
        message.warning('这个文件没有可引用的文本内容');
        return;
      }

      addSelectedReference({
        id: `${file.name}-${file.size}-${file.lastModified}`,
        type: 'file',
        title: file.name,
        content: truncateReferenceContent(content, MAX_FILE_CHARS),
      });
    } catch {
      message.error('文件读取失败');
    }
  };

  const handleSelectNote = (note: Note) => {
    const content = note.content.trim();

    if (!content) {
      message.warning('这条笔记没有内容');
      return;
    }

    addSelectedReference({
      id: note.id,
      type: 'note',
      title: getReferenceNoteDisplayName(note),
      content: truncateReferenceContent(content),
    });
    closeReferenceMenu();
  };

  const toggleReferenceNoteFolder = (folderKey: string) => {
    setOpenNoteFolderKeys((currentKeys) => {
      const nextKeys = new Set(currentKeys);

      if (nextKeys.has(folderKey)) {
        nextKeys.delete(folderKey);
      } else {
        nextKeys.add(folderKey);
      }

      return nextKeys;
    });
  };

  const renderReferenceNoteTree = (treeNodes: ReferenceNoteTreeNode[]) => {
    return treeNodes.map((node) => {
      if (node.type === 'folder') {
        const isOpen = openNoteFolderKeys.has(node.key);

        return (
          <div className="reference-note-tree-node" key={node.key}>
            <button
              className={isOpen ? 'reference-menu-item reference-note-folder open' : 'reference-menu-item reference-note-folder'}
              type="button"
              onClick={() => toggleReferenceNoteFolder(node.key)}
            >
              <ChevronRight className="reference-note-folder-caret" size={16} strokeWidth={1.8} />
              {isOpen ? <FolderOpen size={19} strokeWidth={1.8} /> : <Folder size={19} strokeWidth={1.8} />}
              <span>{node.name}</span>
            </button>
            {isOpen ? <div className="reference-note-branch">{renderReferenceNoteTree(node.children)}</div> : null}
          </div>
        );
      }

      const selected = selectedReferences.some((reference) => reference.type === 'note' && reference.id === node.note.id);

      return (
        <button
          className={selected ? 'reference-menu-item reference-note-file selected' : 'reference-menu-item reference-note-file'}
          key={node.key}
          type="button"
          onClick={() => handleSelectNote(node.note)}
        >
          <FileText size={19} strokeWidth={1.8} />
          <span>{node.name}</span>
          {selected ? <Check size={18} strokeWidth={2} /> : null}
        </button>
      );
    });
  };

  const handleSelectConversation = async (conversation: ChatConversation) => {
    try {
      const messages = await fetchChatMessages(conversation.id);
      const content = messages
        .map((chatMessage) => `${chatMessage.role === 'user' ? '用户' : '助手'}：${chatMessage.content.trim()}`)
        .filter((line) => line.length > 3)
        .join('\n\n');

      if (!content) {
        message.warning('这个对话暂时没有可引用内容');
        return;
      }

      addSelectedReference({
        id: conversation.id,
        type: 'conversation',
        title: conversation.title || '未命名对话',
        content: truncateReferenceContent(content),
      });
      closeReferenceMenu();
    } catch {
      message.error('对话内容加载失败');
    }
  };

  const clearStreamWatchdog = () => {
    window.clearTimeout(streamWatchdogRef.current);
    streamWatchdogRef.current = undefined;
  };

  const armStreamWatchdog = (messageId: string) => {
    clearStreamWatchdog();
    streamWatchdogRef.current = window.setTimeout(() => {
      setChatMessages((currentMessages) =>
        currentMessages.map((messageItem) =>
          messageItem.id === messageId && !messageItem.content
            ? { ...messageItem, streamStatus: '模型响应时间较长，仍在等待中...' }
            : messageItem,
        ),
      );
    }, 20_000);
  };

  const handleSubmitMessage = async (content?: string) => {
    const messageContent = (content ?? inputValue).trim();

    if (!messageContent) {
      return;
    }

    if (!selectedModelConfigId) {
      message.warning('请先在系统设置里新增聊天模型');
      return;
    }

    setHasConversation(true);
    setInputValue('');
    setSelectedKnowledgeBaseId(undefined);
    setSelectedReferences([]);
    setReferenceMenuOpen(false);
    setReferenceMenuView('main');

    const requestKnowledgeBaseId = selectedKnowledgeBaseId;
    const requestReferences = selectedReferences;
    const requestContent = buildMessageWithReferences(messageContent, requestReferences);
    const userMessage: ChatMessage = {
      id: createClientMessageId(),
      role: 'user',
      content: messageContent,
      knowledgeBase: selectedKnowledgeBase
        ? {
            id: selectedKnowledgeBase.id,
            name: selectedKnowledgeBase.name,
          }
        : undefined,
      references: requestReferences.map(({ id, type, title }) => ({ id, type, title })),
    };
    const aiMessageId = createClientMessageId();
    const aiMessage: ChatMessage = { id: aiMessageId, role: 'ai', content: '', streamStatus: '正在连接模型...' };

    setChatMessages((currentMessages) => [...currentMessages, userMessage, aiMessage]);

    try {
      armStreamWatchdog(aiMessageId);
      await streamChatMessage(requestContent, selectedModelConfigId, currentConversationId, requestKnowledgeBaseId, {
        onConversation: (conversationId) => {
          armStreamWatchdog(aiMessageId);
          streamingCreatedConversationRef.current = conversationId;
          setCurrentConversationId(conversationId);
          onConversationCreated(conversationId);
        },
        onStatus: (status) => {
          armStreamWatchdog(aiMessageId);
          setChatMessages((currentMessages) =>
            currentMessages.map((messageItem) =>
              messageItem.id === aiMessageId && !messageItem.content
                ? { ...messageItem, streamStatus: status }
                : messageItem,
            ),
          );
        },
        onDelta: (delta) => {
          armStreamWatchdog(aiMessageId);
          setChatMessages((currentMessages) =>
            currentMessages.map((messageItem) =>
              messageItem.id === aiMessageId
                ? { ...messageItem, content: messageItem.content + delta, streamStatus: undefined }
                : messageItem,
            ),
          );
        },
        onSuggestions: (suggestions) => {
          clearStreamWatchdog();
          setChatMessages((currentMessages) =>
            currentMessages.map((messageItem) =>
              messageItem.id === aiMessageId
                ? { ...messageItem, suggestedQuestions: suggestions }
                : messageItem,
            ),
          );
        },
      });
      clearStreamWatchdog();
    } catch {
      clearStreamWatchdog();
      setChatMessages((currentMessages) => [
        ...currentMessages.filter((messageItem) => messageItem.id !== aiMessageId),
        { id: aiMessageId, role: 'ai', content: '后端暂时没有连接成功，先确认 Spring Boot 是否运行在 8080 端口。' },
      ]);
    }
  };

  return (
    <section className={hasConversation ? 'chat-page conversation-mode' : 'chat-page new-chat-mode'}>
      <header className="page-topbar">
        <div>
          <Select
            className="top-model-select"
            value={selectedModelConfigId}
            variant="borderless"
            placeholder="选择模型"
            onChange={setSelectedModelConfigId}
            options={modelConfigs.map((config) => ({
              label: config.defaultModel ? `${config.name}（默认）` : config.name,
              value: config.id,
            }))}
          />
          {hasConversation ? <Typography.Text type="secondary">设为默认</Typography.Text> : null}
        </div>
        <Space>
          <Button icon={<Settings2 size={16} />} />
        </Space>
      </header>

      <div className="chat-main">
        {!hasConversation ? (
          <Typography.Title className="new-chat-title" level={2}>
            我们先从哪里开始呢？
          </Typography.Title>
        ) : (
          <Bubble.List
            className="chat-bubbles"
            items={visibleMessages.map((message) => ({
              key: message.id,
              role: message.role,
              content:
                message.role === 'user' ? (
                  <UserChatMessage message={message} />
                ) : (
                  <AssistantChatMessage
                    content={message.content}
                    streamStatus={message.streamStatus}
                    suggestedQuestions={message.suggestedQuestions}
                    onSuggestionClick={(suggestion) => void handleSubmitMessage(suggestion)}
                  />
                ),
            }))}
            role={{
              ai: {
                placement: 'start',
                variant: 'borderless',
                avatar: <Avatar style={{ background: '#ffffff', color: '#111827' }}>AI</Avatar>,
              },
              user: {
                placement: 'end',
                variant: 'filled',
                avatar: <Avatar style={{ background: '#1677ff' }}>你</Avatar>,
              },
            }}
          />
        )}
      </div>

      <div className="chat-sender-wrap" ref={senderWrapRef}>
        <input
          ref={fileInputRef}
          className="hidden-file-input"
          type="file"
          accept=".csv,.html,.json,.log,.md,.markdown,.text,.ts,.tsx,.txt,.xml,.yaml,.yml,text/*,application/json"
          onChange={(event) => void handleFileSelected(event)}
        />
        {referenceMenuOpen ? (
          <div className="reference-popover" aria-label="引用菜单">
            {referenceMenuView === 'main' ? (
              referenceActions.map((item) => (
                <button
                  className="reference-menu-item"
                  key={item.label}
                  type="button"
                  onClick={() => handleReferenceAction(item.key)}
                >
                  <item.icon size={22} strokeWidth={1.8} />
                  <span>{item.label}</span>
                  {item.hasSubmenu ? <ChevronRight size={20} strokeWidth={1.8} /> : null}
                </button>
              ))
            ) : referenceMenuView === 'knowledge' ? (
              <>
                <button className="reference-menu-item reference-menu-back" type="button" onClick={() => setReferenceMenuView('main')}>
                  <ChevronLeft size={20} strokeWidth={1.8} />
                  <span>引用知识库</span>
                </button>
                <button
                  className={!selectedKnowledgeBaseId ? 'reference-menu-item selected' : 'reference-menu-item'}
                  type="button"
                  onClick={() => {
                    setSelectedKnowledgeBaseId(undefined);
                    setReferenceMenuOpen(false);
                    setReferenceMenuView('main');
                  }}
                >
                  <span className="reference-menu-icon-placeholder" />
                  <span>不引用知识库</span>
                  {!selectedKnowledgeBaseId ? <Check size={18} strokeWidth={2} /> : null}
                </button>
                {knowledgeBases.map((kb) => (
                  <button
                    className={selectedKnowledgeBaseId === kb.id ? 'reference-menu-item selected' : 'reference-menu-item'}
                    key={kb.id}
                    type="button"
                    onClick={() => {
                      setSelectedKnowledgeBaseId(kb.id);
                      setReferenceMenuOpen(false);
                      setReferenceMenuView('main');
                    }}
                  >
                    <Database size={22} strokeWidth={1.8} />
                    <span>{kb.name}</span>
                    {selectedKnowledgeBaseId === kb.id ? <Check size={18} strokeWidth={2} /> : null}
                  </button>
                ))}
                {knowledgeBases.length === 0 ? (
                  <div className="reference-menu-empty">暂无知识库</div>
                ) : null}
              </>
            ) : referenceMenuView === 'notes' ? (
              <>
                <button className="reference-menu-item reference-menu-back" type="button" onClick={() => setReferenceMenuView('main')}>
                  <ChevronLeft size={20} strokeWidth={1.8} />
                  <span>引用笔记</span>
                </button>
                <div className="reference-menu-list">
                  {noteTree.length > 0 ? renderReferenceNoteTree(noteTree) : <div className="reference-menu-empty">暂无笔记</div>}
                </div>
              </>
            ) : (
              <>
                <button className="reference-menu-item reference-menu-back" type="button" onClick={() => setReferenceMenuView('main')}>
                  <ChevronLeft size={20} strokeWidth={1.8} />
                  <span>引用其他对话</span>
                </button>
                <div className="reference-menu-list">
                  {selectableConversations
                    .map((conversation) => {
                      const selected = selectedReferences.some(
                        (reference) => reference.type === 'conversation' && reference.id === conversation.id,
                      );

                      return (
                        <button
                          className={selected ? 'reference-menu-item selected' : 'reference-menu-item'}
                          key={conversation.id}
                          type="button"
                          onClick={() => void handleSelectConversation(conversation)}
                        >
                          <MessageCircle size={22} strokeWidth={1.8} />
                          <span>{conversation.title || '未命名对话'}</span>
                          {selected ? <Check size={18} strokeWidth={2} /> : null}
                        </button>
                      );
                    })}
                  {selectableConversations.length === 0 ? (
                    <div className="reference-menu-empty">暂无可引用对话</div>
                  ) : null}
                </div>
              </>
            )}
          </div>
        ) : null}
        {!hasConversation ? (
          <div className="new-chat-input">
            <ReferenceCards
              knowledgeBase={selectedKnowledgeBase}
              references={selectedReferences}
              onRemoveKnowledgeBase={() => setSelectedKnowledgeBaseId(undefined)}
              onRemoveReference={(reference) =>
                setSelectedReferences((currentReferences) =>
                  currentReferences.filter((item) => !(item.type === reference.type && item.id === reference.id)),
                )
              }
            />
            <Button
              className="new-chat-plus-button"
              type={referenceMenuOpen ? 'primary' : 'text'}
              shape="circle"
              icon={<Plus size={18} />}
              onClick={() => {
                setReferenceMenuOpen((open) => !open);
                setReferenceMenuView('main');
              }}
            />
            <Input
              className="new-chat-text-input"
              variant="borderless"
              placeholder="有问题，尽管问"
              value={inputValue}
              onChange={(event) => setInputValue(event.target.value)}
              onPressEnter={() => void handleSubmitMessage()}
            />
          </div>
        ) : (
          <div className="conversation-sender-shell">
            <ReferenceCards
              compact
              knowledgeBase={selectedKnowledgeBase}
              references={selectedReferences}
              onRemoveKnowledgeBase={() => setSelectedKnowledgeBaseId(undefined)}
              onRemoveReference={(reference) =>
                setSelectedReferences((currentReferences) =>
                  currentReferences.filter((item) => !(item.type === reference.type && item.id === reference.id)),
                )
              }
            />
            <Sender
              placeholder="输入消息"
              value={inputValue}
              onChange={setInputValue}
              autoSize={{ minRows: 2, maxRows: 6 }}
              prefix={
                <Space size={4}>
                  <Button
                    type={referenceMenuOpen ? 'primary' : 'text'}
                    shape="circle"
                    icon={<Plus size={16} />}
                    onClick={() => {
                      setReferenceMenuOpen((open) => !open);
                      setReferenceMenuView('main');
                    }}
                  />
                  <Button type="text" shape="circle" icon={<Paperclip size={16} />} onClick={() => fileInputRef.current?.click()} />
                </Space>
              }
              onSubmit={(content) => void handleSubmitMessage(content)}
            />
          </div>
        )}
      </div>
    </section>
  );
}

function ReferenceCards({
  compact = false,
  knowledgeBase,
  references,
  onRemoveKnowledgeBase,
  onRemoveReference,
}: {
  compact?: boolean;
  knowledgeBase?: KnowledgeBase | ChatMessage['knowledgeBase'];
  references?: ChatReference[];
  onRemoveKnowledgeBase?: () => void;
  onRemoveReference?: (reference: ChatReference) => void;
}) {
  if (!knowledgeBase && (!references || references.length === 0)) {
    return null;
  }

  return (
    <div className={compact ? 'chat-reference-stack compact' : 'chat-reference-stack'}>
      {knowledgeBase ? (
        <div className={compact ? 'chat-reference-card compact' : 'chat-reference-card'}>
          <Database size={compact ? 16 : 17} strokeWidth={1.8} />
          <span className="chat-reference-title">{knowledgeBase.name}</span>
          <span className="chat-reference-type">知识库</span>
          {onRemoveKnowledgeBase ? (
            <Button
              aria-label="取消引用知识库"
              className="chat-reference-remove"
              type="text"
              shape="circle"
              icon={<X size={14} />}
              onClick={onRemoveKnowledgeBase}
            />
          ) : (
            <span />
          )}
        </div>
      ) : null}
      {references?.map((reference) => {
        const Icon = getReferenceIcon(reference.type);

        return (
          <div className={compact ? 'chat-reference-card compact' : 'chat-reference-card'} key={`${reference.type}-${reference.id}`}>
            <Icon size={compact ? 16 : 17} strokeWidth={1.8} />
            <span className="chat-reference-title">{reference.title}</span>
            <span className="chat-reference-type">{getReferenceTypeLabel(reference.type)}</span>
            {onRemoveReference ? (
              <Button
                aria-label={`取消引用${reference.title}`}
                className="chat-reference-remove"
                type="text"
                shape="circle"
                icon={<X size={14} />}
                onClick={() => onRemoveReference(reference)}
              />
            ) : (
              <span />
            )}
          </div>
        );
      })}
    </div>
  );
}

function UserChatMessage({ message }: { message: ChatMessage }) {
  const references = message.references?.length ? message.references : extractReferencesFromMessageContent(message.content);

  return (
    <div className="user-chat-message">
      <ReferenceCards knowledgeBase={message.knowledgeBase} references={references} />
      <MarkdownMessage content={getUserMessageDisplayContent(message.content)} />
    </div>
  );
}

function getUserMessageDisplayContent(content: string) {
  return content.split(/\n-{3,}\n以下是本次对话引用的上下文，请优先结合这些内容回答：/)[0].trim();
}

function extractReferencesFromMessageContent(content: string): ChatReference[] {
  const references: ChatReference[] = [];
  const referencePattern = /^\[引用\s+\d+：([^｜\]]+)｜([^\]]+)\]/gm;
  let match: RegExpExecArray | null;

  while ((match = referencePattern.exec(content)) !== null) {
    const type = getReferenceTypeByLabel(match[1].trim());

    if (!type) {
      continue;
    }

    references.push({
      id: `${type}-${references.length}-${match[2].trim()}`,
      type,
      title: match[2].trim(),
    });
  }

  return references;
}

function AssistantChatMessage({
  content,
  streamStatus,
  suggestedQuestions,
  onSuggestionClick,
}: {
  content: string;
  streamStatus?: string;
  suggestedQuestions?: string[];
  onSuggestionClick?: (suggestion: string) => void;
}) {
  return (
    <div className="assistant-chat-message">
      {content ? <VditorPreviewMessage content={content} /> : <WaitingMessage status={streamStatus} />}
      {suggestedQuestions?.length ? (
        <div className="follow-up-suggestions" aria-label="追问建议">
          <div className="follow-up-title">追问</div>
          {suggestedQuestions.map((suggestion) => (
            <button
              className="follow-up-suggestion"
              key={suggestion}
              type="button"
              onClick={() => onSuggestionClick?.(suggestion)}
            >
              {suggestion}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function WaitingMessage({ status }: { status?: string }) {
  return (
    <div className="assistant-waiting-message">
      <span className="assistant-waiting-dots" aria-hidden="true">
        <span />
        <span />
        <span />
      </span>
      <span>{status || '正在等待模型响应...'}</span>
    </div>
  );
}

function buildMessageWithReferences(content: string, references: ChatReference[]) {
  if (references.length === 0) {
    return content;
  }

  const referenceContent = references
    .map((reference, index) => {
      return `[引用 ${index + 1}：${getReferenceTypeLabel(reference.type)}｜${reference.title}]\n${reference.content ?? ''}`;
    })
    .join('\n\n');

  return `${content}\n\n---\n以下是本次对话引用的上下文，请优先结合这些内容回答：\n\n${referenceContent}`;
}

function truncateReferenceContent(content: string, limit = MAX_REFERENCE_CHARS) {
  if (content.length <= limit) {
    return content;
  }

  return `${content.slice(0, limit)}\n\n[内容已截断]`;
}

function isReadableTextFile(file: File) {
  if (file.type.startsWith('text/') || file.type === 'application/json' || file.type.includes('xml')) {
    return true;
  }

  const extension = file.name.split('.').pop()?.toLowerCase();
  return extension ? TEXT_FILE_EXTENSIONS.has(extension) : false;
}

function getReferenceIcon(type: ChatReference['type']) {
  if (type === 'file' || type === 'note') {
    return FileText;
  }

  return MessageCircle;
}

function getReferenceTypeLabel(type: ChatReference['type']) {
  if (type === 'file') {
    return '文件';
  }

  if (type === 'note') {
    return '笔记';
  }

  return '对话';
}

function getReferenceTypeByLabel(label: string): ChatReference['type'] | undefined {
  if (label === '文件') {
    return 'file';
  }

  if (label === '笔记') {
    return 'note';
  }

  if (label === '对话') {
    return 'conversation';
  }

  return undefined;
}

function getReferenceNoteDisplayName(note: Note) {
  const pathParts = splitReferenceNotePath(note.title);
  return pathParts[pathParts.length - 1] || note.title || '未命名笔记';
}

function buildReferenceNoteTree(notes: Note[], folders: NoteFolder[]): ReferenceNoteTreeNode[] {
  const root: MutableReferenceNoteFolderTreeNode = {
    type: 'folder',
    key: 'root',
    name: 'root',
    children: [],
    folderMap: new Map(),
  };

  folders.forEach((folder) => {
    ensureReferenceNoteFolderPath(root, splitReferenceNotePath(folder.path), folder.id);
  });

  notes.forEach((note) => {
    const pathParts = splitReferenceNotePath(note.title);
    const fileName = pathParts[pathParts.length - 1] || note.title || '未命名笔记';
    const currentFolder = ensureReferenceNoteFolderPath(root, pathParts.slice(0, -1));

    currentFolder.children.push({
      type: 'note',
      key: note.id,
      name: fileName,
      note,
    });
  });

  return sortReferenceNoteTreeNodes(stripMutableReferenceNoteFolderState(root.children));
}

function ensureReferenceNoteFolderPath(
  root: MutableReferenceNoteFolderTreeNode,
  pathParts: string[],
  folderId?: string,
) {
  let currentFolder = root;

  pathParts.forEach((pathPart) => {
    const folderKey = `${currentFolder.key}/${pathPart}`;
    let folderNode = currentFolder.folderMap.get(pathPart);

    if (!folderNode) {
      folderNode = {
        type: 'folder',
        key: folderKey,
        name: pathPart,
        children: [],
        folderMap: new Map(),
      };
      currentFolder.folderMap.set(pathPart, folderNode);
      currentFolder.children.push(folderNode);
    }

    currentFolder = folderNode;
  });

  if (folderId) {
    currentFolder.folderId = folderId;
  }

  return currentFolder;
}

function splitReferenceNotePath(title: string) {
  return title
    .replace(/\\/g, '/')
    .split('/')
    .map((part) => part.trim())
    .filter(Boolean);
}

function stripMutableReferenceNoteFolderState(nodes: ReferenceNoteTreeNode[]): ReferenceNoteTreeNode[] {
  return nodes.map((node) => {
    if (node.type === 'note') {
      return node;
    }

    return {
      type: 'folder',
      key: node.key,
      folderId: node.folderId,
      name: node.name,
      children: stripMutableReferenceNoteFolderState(node.children),
    };
  });
}

function sortReferenceNoteTreeNodes(nodes: ReferenceNoteTreeNode[]): ReferenceNoteTreeNode[] {
  return [...nodes]
    .sort((firstNode, secondNode) => {
      if (firstNode.type !== secondNode.type) {
        return firstNode.type === 'folder' ? -1 : 1;
      }

      return firstNode.name.localeCompare(secondNode.name, 'zh-Hans-CN', {
        numeric: true,
        sensitivity: 'base',
      });
    })
    .map((node) => {
      if (node.type === 'note') {
        return node;
      }

      return {
        ...node,
        children: sortReferenceNoteTreeNodes(node.children),
      };
    });
}

function collectReferenceNoteFolderKeys(nodes: ReferenceNoteTreeNode[]) {
  const folderKeys: string[] = [];

  nodes.forEach((node) => {
    if (node.type === 'folder') {
      folderKeys.push(node.key, ...collectReferenceNoteFolderKeys(node.children));
    }
  });

  return folderKeys;
}

function createClientMessageId() {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }

  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;

    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0'))
      .join('')
      .replace(/^(.{8})(.{4})(.{4})(.{4})(.{12})$/, '$1-$2-$3-$4-$5');
  }

  return `message-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export default ChatPage;
