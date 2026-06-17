import { Bubble, Sender } from '@ant-design/x';
import { Avatar, Button, Input, Select, Space, Typography, message } from 'antd';
import { Paperclip, Plus, Settings2, Square } from 'lucide-react';
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
import { useApiResource } from '../../hooks/useApiResource';
import type { ChatConversation, ChatMessage, ChatModelConfig, ChatReference, KnowledgeBase, Note, NoteFolder } from '../../types';
import { AssistantChatMessage, UserChatMessage } from './components/ChatMessages';
import ReferenceCards from './components/ReferenceCards';
import ReferenceMenu from './components/ReferenceMenu';
import {
  MAX_FILE_CHARS,
  buildMessageWithReferences,
  buildReferenceNoteTree,
  collectReferenceNoteFolderKeys,
  getReferenceNoteDisplayName,
  isReadableTextFile,
  truncateReferenceContent,
  type ReferenceMenuView,
} from './components/referenceUtils';
import './ChatPage.css';

const fallbackModelConfigs: ChatModelConfig[] = [];
const fallbackKnowledgeBases: KnowledgeBase[] = [];
const fallbackNotes: Note[] = [];
const fallbackNoteFolders: NoteFolder[] = [];
const fallbackChatConversations: ChatConversation[] = [];

function fetchActiveChatConversations() {
  return fetchChatConversations(false);
}

type ChatPageProps = {
  newChatVersion: number;
  onConversationCreated: (conversationId: string) => void;
  selectedConversationId?: string;
};

type PendingChatRequest = {
  requestContent: string;
  modelConfigId: string;
  knowledgeBaseId?: string;
  userMessage: ChatMessage;
  userMessageDisplayed: boolean;
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
  const activeStreamControllerRef = useRef<AbortController | undefined>(undefined);
  const pendingChatRequestsRef = useRef<PendingChatRequest[]>([]);
  const isStreamingResponseRef = useRef(false);
  const currentConversationIdRef = useRef<string | undefined>(selectedConversationId);
  const [isStreamingResponse, setIsStreamingResponse] = useState(false);
  const [pendingChatRequestCount, setPendingChatRequestCount] = useState(0);
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
    pendingChatRequestsRef.current = [];
    setPendingChatRequestCount(0);
    activeStreamControllerRef.current?.abort();
    activeStreamControllerRef.current = undefined;
    isStreamingResponseRef.current = false;
    setIsStreamingResponse(false);
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
      activeStreamControllerRef.current?.abort();
      activeStreamControllerRef.current = undefined;
      pendingChatRequestsRef.current = [];
    };
  }, []);

  useEffect(() => {
    currentConversationIdRef.current = currentConversationId;
  }, [currentConversationId]);

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

  const syncPendingChatRequestCount = () => {
    setPendingChatRequestCount(pendingChatRequestsRef.current.length);
  };

  const stopActiveStream = () => {
    if (!activeStreamControllerRef.current) {
      return;
    }

    activeStreamControllerRef.current.abort();
    activeStreamControllerRef.current = undefined;
    clearStreamWatchdog();
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
    const pendingRequest: PendingChatRequest = {
      requestContent,
      modelConfigId: selectedModelConfigId,
      knowledgeBaseId: requestKnowledgeBaseId,
      userMessage,
      userMessageDisplayed: false,
    };

    if (isStreamingResponseRef.current) {
      pendingChatRequestsRef.current.push({ ...pendingRequest, userMessageDisplayed: true });
      syncPendingChatRequestCount();
      setChatMessages((currentMessages) => [...currentMessages, userMessage]);
      return;
    }

    await runChatRequest(pendingRequest);
  };

  const runChatRequest = async (request: PendingChatRequest) => {
    const aiMessageId = createClientMessageId();
    const aiMessage: ChatMessage = { id: aiMessageId, role: 'ai', content: '', streamStatus: '正在连接模型...' };
    const streamController = new AbortController();

    activeStreamControllerRef.current = streamController;
    isStreamingResponseRef.current = true;
    setIsStreamingResponse(true);
    setChatMessages((currentMessages) =>
      request.userMessageDisplayed ? [...currentMessages, aiMessage] : [...currentMessages, request.userMessage, aiMessage],
    );

    try {
      armStreamWatchdog(aiMessageId);
      await streamChatMessage(request.requestContent, request.modelConfigId, currentConversationIdRef.current, request.knowledgeBaseId, streamController.signal, {
        onConversation: (conversationId) => {
          armStreamWatchdog(aiMessageId);
          streamingCreatedConversationRef.current = conversationId;
          currentConversationIdRef.current = conversationId;
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
    } catch (error) {
      clearStreamWatchdog();
      if (isAbortError(error)) {
        setChatMessages((currentMessages) =>
          currentMessages.map((messageItem) => {
            if (messageItem.id !== aiMessageId) {
              return messageItem;
            }

            return messageItem.content.trim()
              ? { ...messageItem, streamStatus: '已停止生成' }
              : { ...messageItem, content: '已停止生成。', streamStatus: undefined };
          }),
        );
        return;
      }

      setChatMessages((currentMessages) => [
        ...currentMessages.filter((messageItem) => messageItem.id !== aiMessageId),
        { id: aiMessageId, role: 'ai', content: '后端暂时没有连接成功，先确认 Spring Boot 是否运行在 8080 端口。' },
      ]);
    } finally {
      if (activeStreamControllerRef.current === streamController) {
        activeStreamControllerRef.current = undefined;
      }

      isStreamingResponseRef.current = false;
      setIsStreamingResponse(false);

      const nextRequest = pendingChatRequestsRef.current.shift();
      syncPendingChatRequestCount();

      if (nextRequest) {
        void runChatRequest(nextRequest);
      }
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
          <ReferenceMenu
            view={referenceMenuView}
            knowledgeBases={knowledgeBases}
            noteTree={noteTree}
            selectableConversations={selectableConversations}
            selectedKnowledgeBaseId={selectedKnowledgeBaseId}
            selectedReferences={selectedReferences}
            openNoteFolderKeys={openNoteFolderKeys}
            onViewChange={setReferenceMenuView}
            onClose={closeReferenceMenu}
            onFileClick={() => fileInputRef.current?.click()}
            onKnowledgeBaseChange={setSelectedKnowledgeBaseId}
            onNoteSelect={handleSelectNote}
            onConversationSelect={(conversation) => void handleSelectConversation(conversation)}
            onNoteFolderToggle={toggleReferenceNoteFolder}
          />
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
            {isStreamingResponse || pendingChatRequestCount > 0 ? (
              <div className="chat-generation-control">
                <Typography.Text type="secondary">
                  {isStreamingResponse ? '正在回答' : '等待回答'}
                  {pendingChatRequestCount > 0 ? `，已排队 ${pendingChatRequestCount} 条补充` : ''}
                </Typography.Text>
                <Button danger size="small" disabled={!isStreamingResponse} icon={<Square size={13} />} onClick={stopActiveStream}>
                  停止生成
                </Button>
              </div>
            ) : null}
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

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError';
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
