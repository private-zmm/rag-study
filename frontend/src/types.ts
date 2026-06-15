import type { LucideIcon } from 'lucide-react';

export type PageKey = 'chat' | 'search' | 'notes' | 'knowledge' | 'clipper' | 'settings';

export type NavItem = {
  icon: LucideIcon;
  label: string;
  key: PageKey;
};

export type ModelItem = {
  name: string;
};

export type Conversation = {
  id: number;
  title: string;
  time: string;
  marker?: string;
};

export type AttachmentAction = {
  label: string;
  icon: LucideIcon;
  hasSubmenu?: boolean;
};

export type ChatMessage = {
  id: string;
  role: 'ai' | 'user';
  content: string;
  streamStatus?: string;
  suggestedQuestions?: string[];
  knowledgeBase?: {
    id: string;
    name: string;
  };
  references?: ChatReference[];
};

export type ChatReference = {
  id: string;
  type: 'file' | 'note' | 'conversation';
  title: string;
  content?: string;
};

export type ChatConversation = {
  id: string;
  title: string;
  updatedAt: string;
  archived: boolean;
};

export type ChatModelConfig = {
  id: string;
  name: string;
  providerType: string;
  baseUrl: string;
  model: string;
  systemPrompt: string;
  defaultModel: boolean;
};

export type EmbeddingModelConfig = {
  id: string;
  name: string;
  providerType: string;
  baseUrl: string;
  model: string;
  dimensions: number;
  defaultModel: boolean;
};

export type Note = {
  id: string;
  title: string;
  updatedAt: string;
  tags: string[];
  content: string;
};

export type NoteFolder = {
  id: string;
  path: string;
  updatedAt: string;
};

export type KnowledgeBase = {
  id: string;
  name: string;
  description: string;
  documentCount: number;
  chunkCount: number;
  vectorStatus: 'ready' | 'indexing' | 'empty';
};

export type KnowledgeDocument = {
  id: string;
  knowledgeBaseId: string;
  title: string;
  sourceType: string;
  rawContent: string;
  parseStatus: string;
  vectorStatus: 'ready' | 'indexing' | 'pending' | 'failed';
  chunkCount: number;
  updatedAt: string;
};

export type NoteKnowledgeSyncTask = {
  taskId: string;
  knowledgeBaseId: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  totalNotes: number;
  processedNotes: number;
  syncedNotes: number;
  skippedNotes: number;
  indexedChunks: number;
  embeddingModel: string;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
};

export type PageResult<T> = {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
};

export type ClipperResult = {
  id: string;
  url: string;
  title: string;
  target: string;
  mode: string;
  status: string;
};

export type ClipperPreview = {
  url: string;
  title: string;
  content: string;
  excerpt: string;
  siteName: string;
  contentType: string;
  wordCount: number;
  existingClip: WebClip | null;
};

export type ClipperProxyConfig = {
  protocol: 'HTTP' | 'SOCKS5';
  host: string;
  port: number | null;
  username: string;
  passwordConfigured: boolean;
};

export type WebClip = {
  id: string;
  knowledgeBaseId: string | null;
  documentId: string | null;
  url: string;
  title: string;
  siteName: string;
  excerpt: string;
  content: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type GlobalSearchResult = {
  id: string;
  type: 'NOTE' | 'KNOWLEDGE' | 'CONVERSATION' | 'WEB';
  title: string;
  description: string;
  targetId: string;
  knowledgeBaseId: string | null;
  updatedAt: string;
};

export type BackupConfig = {
  enabled: boolean;
  endpoint: string;
  bucket: string;
  accessKey: string;
  region: string;
  prefix: string;
  cronExpression: string;
  retentionDays: number;
  retentionCount: number;
  lastBackupAt: string | null;
  updatedAt: string | null;
  pathStyleAccess: boolean;
  secretConfigured: boolean;
  pgDumpPath: string;
  psqlPath: string;
};

export type BackupItem = {
  objectName: string;
  fileName: string;
  size: number;
  createdAt: string | null;
};

export type BackupResult = BackupItem;

export type User = {
  id: string;
  username: string;
  email: string;
  nickname: string;
};

export type AuthSession = {
  token: string;
  user: User;
};
