import { clearStoredSession, getStoredSession } from '../auth/session';
import type { AuthSession, BackupConfig, BackupItem, BackupResult, ChatConversation, ChatMessage, ChatModelConfig, ClipperPreview, ClipperProxyConfig, ClipperResult, EmbeddingModelConfig, GlobalSearchResult, KnowledgeBase, KnowledgeDocument, Note, NoteFolder, NoteKnowledgeSyncTask, PageResult, User, WebClip } from '../types';

type ApiResponse<T> = {
  data: T;
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';
export const SESSION_EXPIRED_EVENT = 'rag-study-session-expired';

export function notifySessionExpired() {
  clearStoredSession();
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

async function request<T>(path: string, init?: RequestInit & { auth?: boolean }): Promise<T> {
  const { auth, ...fetchInit } = init ?? {};
  const token = getStoredSession()?.token;
  const headers = new Headers(fetchInit.headers);
  const isFormData = fetchInit.body instanceof FormData;
  const shouldSendAuth = auth ?? true;

  if (!isFormData) {
    headers.set('Content-Type', 'application/json');
  }

  if (shouldSendAuth && token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await sendRequest(`${API_BASE_URL}${path}`, {
    ...fetchInit,
    headers,
  });

  if (!response.ok) {
    const errorText = await readErrorMessage(response);

    if (response.status === 401) {
      notifySessionExpired();
    }

    throw new Error(errorText || `API request failed: ${response.status}`);
  }

  const body = (await response.json()) as ApiResponse<T>;
  return body.data;
}

async function readErrorMessage(response: Response) {
  const errorText = await response.text().catch(() => '');

  if (!errorText) {
    return '';
  }

  try {
    const errorBody = JSON.parse(errorText) as { message?: string; error?: string };
    return errorBody.message || errorBody.error || errorText;
  } catch {
    return errorText;
  }
}

function readDownloadFileName(contentDisposition: string | null) {
  if (!contentDisposition) {
    return undefined;
  }

  const encodedFileNameMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);

  if (encodedFileNameMatch) {
    try {
      return decodeURIComponent(encodedFileNameMatch[1]);
    } catch {
      return encodedFileNameMatch[1];
    }
  }

  const fileNameMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
  return fileNameMatch?.[1];
}

export function register(payload: { username: string; email: string; password: string; nickname?: string }) {
  return request<AuthSession>('/auth/register', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(payload),
  });
}

export function login(payload: { account: string; password: string }) {
  return request<AuthSession>('/auth/login', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(payload),
  });
}

export function fetchCurrentUser() {
  return request<User>('/auth/me');
}

export function fetchNotes() {
  return request<Note[]>('/notes');
}

export async function exportNotesArchive(payload: { noteIds?: string[]; folderPath?: string }) {
  const token = getStoredSession()?.token;
  const headers = new Headers();
  headers.set('Content-Type', 'application/json');

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await sendRequest(`${API_BASE_URL}/notes/export`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const errorText = await readErrorMessage(response);

    if (response.status === 401) {
      notifySessionExpired();
    }

    throw new Error(errorText || `API request failed: ${response.status}`);
  }

  return {
    blob: await response.blob(),
    fileName: readDownloadFileName(response.headers.get('Content-Disposition')) ?? 'notes.zip',
  };
}

export function fetchNoteFolders() {
  return request<NoteFolder[]>('/note-folders');
}

export function createNoteFolder(path: string) {
  return request<NoteFolder>('/note-folders', {
    method: 'POST',
    body: JSON.stringify({ path }),
  });
}

export function deleteNoteFolder(id: string) {
  return request<void>(`/note-folders/${id}`, {
    method: 'DELETE',
  });
}

export function importNotes(notes: Array<Pick<Note, 'title' | 'content'>>) {
  return request<Note[]>('/notes/import', {
    method: 'POST',
    body: JSON.stringify({ notes }),
  });
}

export function syncNotesToKnowledge(payload: { knowledgeBaseId: string; noteIds: string[] }) {
  return request<NoteKnowledgeSyncTask>('/notes/sync-to-knowledge', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchNoteKnowledgeSyncTask(taskId: string) {
  return request<NoteKnowledgeSyncTask>(`/notes/sync-to-knowledge/tasks/${taskId}`);
}

export function createNote(payload: Pick<Note, 'title' | 'content'>) {
  return request<Note>('/notes', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function uploadNoteAsset(file: File, signal?: AbortSignal) {
  const formData = new FormData();
  formData.append('file', file);

  return request<{ url: string; storagePath: string; objectName: string }>('/note-assets/upload', {
    method: 'POST',
    body: formData,
    signal,
  });
}

export function bindNoteAssets(noteId: string, objectNames: string[]) {
  return request<void>('/note-assets/bind', {
    method: 'POST',
    body: JSON.stringify({ noteId, objectNames }),
  });
}

export function fetchKnowledgeBases() {
  return request<KnowledgeBase[]>('/knowledge-bases');
}

export function createKnowledgeBase(payload: { name: string; description?: string }) {
  return request<KnowledgeBase>('/knowledge-bases', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateKnowledgeBase(id: string, payload: { name: string; description?: string }) {
  return request<KnowledgeBase>(`/knowledge-bases/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function deleteKnowledgeBase(id: string) {
  return request<void>(`/knowledge-bases/${id}`, {
    method: 'DELETE',
  });
}

export function fetchKnowledgeDocuments(knowledgeBaseId: string, page: number, pageSize: number) {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  });

  return request<PageResult<KnowledgeDocument>>(`/knowledge-bases/${knowledgeBaseId}/documents?${params.toString()}`);
}

export function fetchKnowledgeDocument(knowledgeBaseId: string, documentId: string) {
  return request<KnowledgeDocument>(`/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`);
}

export function createKnowledgeDocument(knowledgeBaseId: string, payload: { title: string; rawContent: string }) {
  return request<KnowledgeDocument>(`/knowledge-bases/${knowledgeBaseId}/documents`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function uploadKnowledgeDocument(knowledgeBaseId: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);

  return request<KnowledgeDocument>(`/knowledge-bases/${knowledgeBaseId}/documents/upload`, {
    method: 'POST',
    body: formData,
  });
}

export function updateKnowledgeDocument(
  knowledgeBaseId: string,
  documentId: string,
  payload: { title: string; rawContent: string },
) {
  return request<KnowledgeDocument>(`/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function deleteKnowledgeDocument(knowledgeBaseId: string, documentId: string) {
  return request<void>(`/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`, {
    method: 'DELETE',
  });
}

export function batchDeleteKnowledgeDocuments(knowledgeBaseId: string, documentIds: string[]) {
  return request<void>(`/knowledge-bases/${knowledgeBaseId}/documents/batch-delete`, {
    method: 'POST',
    body: JSON.stringify({ documentIds }),
  });
}

export function rebuildKnowledgeIndex(knowledgeBaseId: string) {
  return request<{ knowledgeBaseId: string; indexedChunks: number; embeddingModel: string }>(`/knowledge-bases/${knowledgeBaseId}/index/rebuild`, {
    method: 'POST',
  });
}

export function rebuildKnowledgeDocumentIndex(knowledgeBaseId: string, documentId: string) {
  return request<{ knowledgeBaseId: string; indexedChunks: number; embeddingModel: string }>(
    `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/index/rebuild`,
    {
      method: 'POST',
    },
  );
}

export function fetchChatConversations(archived = false) {
  return request<ChatConversation[]>(`/chat/conversations?archived=${archived}`);
}

export function fetchChatMessages(conversationId: string) {
  return request<ChatMessage[]>(`/chat/conversations/${conversationId}/messages`);
}

export function renameChatConversation(conversationId: string, title: string) {
  return request<ChatConversation>(`/chat/conversations/${conversationId}/title`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  });
}

export function deleteChatConversation(conversationId: string) {
  return request<void>(`/chat/conversations/${conversationId}`, {
    method: 'DELETE',
  });
}

export function archiveChatConversation(conversationId: string) {
  return request<ChatConversation>(`/chat/conversations/${conversationId}/archive`, {
    method: 'PATCH',
  });
}

export function restoreChatConversation(conversationId: string) {
  return request<ChatConversation>(`/chat/conversations/${conversationId}/restore`, {
    method: 'PATCH',
  });
}

export function sendChatMessage(content: string, modelConfigId?: string, conversationId?: string) {
  return request<{ conversationId: string; messages: ChatMessage[] }>('/chat/messages', {
    method: 'POST',
    body: JSON.stringify({ content, modelConfigId, conversationId }),
  });
}

export async function streamChatMessage(
  content: string,
  modelConfigId: string,
  conversationId: string | undefined,
  knowledgeBaseId: string | undefined,
  handlers: {
    onConversation: (conversationId: string) => void;
    onDelta: (delta: string) => void;
    onStatus?: (status: string) => void;
    onSuggestions?: (suggestions: string[]) => void;
  },
) {
  const token = getStoredSession()?.token;
  const headers = new Headers();
  headers.set('Content-Type', 'application/json');

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await sendRequest(`${API_BASE_URL}/chat/messages/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ content, modelConfigId, conversationId, knowledgeBaseId }),
  });

  if (!response.ok || !response.body) {
    if (response.status === 401) {
      notifySessionExpired();
    }

    throw new Error(`API stream request failed: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();

    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() ?? '';

    events.forEach((event) => {
      const eventName = readSseEventName(event);
      const data = readSseData(event);

      if (!data || data === '[DONE]') {
        return;
      }

      if (eventName === 'conversation') {
        handlers.onConversation(data);
        return;
      }

      if (eventName === 'status') {
        handlers.onStatus?.(data);
        return;
      }

      if (eventName === 'suggestions') {
        handlers.onSuggestions?.(parseSuggestedQuestions(data));
        return;
      }

      handlers.onDelta(data);
    });
  }
}

async function sendRequest(input: RequestInfo | URL, init?: RequestInit) {
  try {
    return await fetch(input, init);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }

    throw new Error(buildNetworkErrorMessage());
  }
}

function buildNetworkErrorMessage() {
  const apiBase = API_BASE_URL || '/api';

  if (apiBase.startsWith('/')) {
    return `无法连接后端接口 ${apiBase}。请确认前端通过 Vite dev server 打开，并且后端 8080 正在运行；如果 5173 被占用，请使用终端显示的新端口。`;
  }

  return `无法连接后端接口 ${apiBase}。请确认后端服务已启动、地址正确，并且浏览器允许跨域访问。`;
}

function parseSuggestedQuestions(data: string) {
  try {
    const parsedData = JSON.parse(data) as unknown;
    return Array.isArray(parsedData) ? parsedData.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}

function readSseEventName(event: string) {
  return event
    .split(/\r?\n/)
    .find((line) => line.startsWith('event:'))
    ?.slice('event:'.length)
    .trim();
}

function readSseData(event: string) {
  return event
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice('data:'.length).trimStart())
    .join('\n');
}

export function fetchChatModelConfigs() {
  return request<ChatModelConfig[]>('/chat-models');
}

export function createChatModelConfig(payload: {
  name: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  systemPrompt?: string;
  defaultModel: boolean;
}) {
  return request<ChatModelConfig>('/chat-models', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateChatModelConfig(
  id: string,
  payload: {
    name: string;
    providerType: string;
    baseUrl: string;
    apiKey: string;
    model: string;
    systemPrompt?: string;
    defaultModel: boolean;
  },
) {
  return request<ChatModelConfig>(`/chat-models/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function setDefaultChatModelConfig(id: string) {
  return request<ChatModelConfig>(`/chat-models/${id}/default`, {
    method: 'PATCH',
  });
}

export function fetchEmbeddingModelConfigs() {
  return request<EmbeddingModelConfig[]>('/embedding-models');
}

export function createEmbeddingModelConfig(payload: {
  name: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  dimensions: number;
  defaultModel: boolean;
}) {
  return request<EmbeddingModelConfig>('/embedding-models', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateEmbeddingModelConfig(
  id: string,
  payload: {
    name: string;
    providerType: string;
    baseUrl: string;
    apiKey: string;
    model: string;
    dimensions: number;
    defaultModel: boolean;
  },
) {
  return request<EmbeddingModelConfig>(`/embedding-models/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function setDefaultEmbeddingModelConfig(id: string) {
  return request<EmbeddingModelConfig>(`/embedding-models/${id}/default`, {
    method: 'PATCH',
  });
}

export function saveNote(note: Pick<Note, 'id' | 'title' | 'content'>) {
  return request<Note>(`/notes/${note.id}`, {
    method: 'PUT',
    body: JSON.stringify({
      title: note.title,
      content: note.content,
    }),
  });
}

export function renameNote(note: Pick<Note, 'id' | 'title'>) {
  return request<Note>(`/notes/${note.id}/title`, {
    method: 'PATCH',
    body: JSON.stringify({
      title: note.title,
    }),
  });
}

export function deleteNote(id: string) {
  return request<void>(`/notes/${id}`, {
    method: 'DELETE',
  });
}

export function previewClipper(payload: { url: string; mode: string; useProxy?: boolean }) {
  return request<ClipperPreview>('/clipper/preview', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchWebClipHistory(limit = 20) {
  return request<WebClip[]>(`/clipper/history?limit=${limit}`);
}

export function fetchWebClipHistoryItem(clipId: string) {
  return request<WebClip>(`/clipper/history/${clipId}`);
}

export function deleteWebClipHistory(clipId: string) {
  return request<void>(`/clipper/history/${clipId}`, {
    method: 'DELETE',
  });
}

export function submitClipper(payload: {
  url: string;
  knowledgeBaseId?: string;
  title: string;
  content: string;
  target: string;
  mode: string;
  useProxy?: boolean;
}) {
  return request<ClipperResult>('/clipper', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchClipperProxyConfig() {
  return request<ClipperProxyConfig>('/clipper/proxy-config');
}

export function saveClipperProxyConfig(payload: {
  protocol: 'HTTP' | 'SOCKS5';
  host: string;
  port: number;
  username?: string;
  password?: string;
}) {
  return request<ClipperProxyConfig>('/clipper/proxy-config', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function testClipperProxyConfig(payload: {
  protocol: 'HTTP' | 'SOCKS5';
  host: string;
  port: number;
  username?: string;
  password?: string;
}) {
  return request<void>('/clipper/proxy-config/test', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function searchGlobal(payload: { query: string; scope: string; limit?: number }, signal?: AbortSignal) {
  const params = new URLSearchParams({
    query: payload.query,
    scope: payload.scope,
    limit: String(payload.limit ?? 50),
  });

  return request<GlobalSearchResult[]>(`/search?${params.toString()}`, {
    signal,
  });
}

export function fetchBackupConfig() {
  return request<BackupConfig>('/backups/config');
}

export function saveBackupConfig(payload: {
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
}) {
  return request<BackupConfig>('/backups/config', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function testBackupConfig(payload: {
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
}) {
  return request<void>('/backups/config/test', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function testBackupDatabaseTools(payload: {
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
}) {
  return request<void>('/backups/database-tools/test', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchBackups() {
  return request<BackupItem[]>('/backups');
}

export function createBackup() {
  return request<BackupResult>('/backups', {
    method: 'POST',
  });
}

export function restoreBackup(objectName: string) {
  const params = new URLSearchParams({ objectName });

  return request<void>(`/backups/restore?${params.toString()}`, {
    method: 'POST',
  });
}

export function deleteBackup(objectName: string) {
  const params = new URLSearchParams({ objectName });

  return request<void>(`/backups?${params.toString()}`, {
    method: 'DELETE',
  });
}
