import { Empty, Input, List, Segmented, Space, Tag, Typography, message } from 'antd';
import { Database, FileText, MessageCircle, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { searchGlobal } from '../api/client';
import type { GlobalSearchResult } from '../types';

type SearchPageProps = {
  onConversationSelect: (conversationId: string) => void;
  onKnowledgeDocumentSelect: (knowledgeBaseId: string, documentId: string) => void;
  onNoteSelect: (noteId: string) => void;
};

type SearchResult = {
  id: string;
  title: string;
  description: string;
  scope: '笔记' | '知识库' | '会话' | '网页';
  icon: typeof FileText;
  onOpen: () => void;
};

const scopeOptions = ['全部', '笔记', '知识库', '会话', '网页'];

const searchScopeMap: Record<string, string> = {
  全部: 'ALL',
  笔记: 'NOTE',
  知识库: 'KNOWLEDGE',
  会话: 'CONVERSATION',
  网页: 'WEB',
};

function SearchPage({ onConversationSelect, onKnowledgeDocumentSelect, onNoteSelect }: SearchPageProps) {
  const [query, setQuery] = useState('');
  const [scope, setScope] = useState('全部');
  const [remoteResults, setRemoteResults] = useState<GlobalSearchResult[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const normalizedQuery = query.trim().toLowerCase();

  useEffect(() => {
    const nextQuery = query.trim();

    if (!nextQuery) {
      setRemoteResults([]);
      setSearchLoading(false);
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setSearchLoading(true);
      searchGlobal(
        {
          query: nextQuery,
          scope: searchScopeMap[scope] ?? 'ALL',
          limit: 50,
        },
        controller.signal,
      )
        .then((results) => {
          setRemoteResults(results);
        })
        .catch((error) => {
          if (controller.signal.aborted) {
            return;
          }

          setRemoteResults([]);
          message.error(error instanceof Error ? error.message : '搜索失败');
        })
        .finally(() => {
          if (!controller.signal.aborted) {
            setSearchLoading(false);
          }
        });
    }, 260);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query, scope]);

  const searchResults = useMemo<SearchResult[]>(() => {
    if (!normalizedQuery) {
      return [];
    }

    return remoteResults.map((result) => toSearchResult(result, onConversationSelect, onKnowledgeDocumentSelect, onNoteSelect));
  }, [normalizedQuery, onConversationSelect, onKnowledgeDocumentSelect, onNoteSelect, remoteResults]);

  return (
    <section className="search-page">
      <div className="search-page-body">
        <div className="search-hero">
          <Typography.Title level={2}>搜索你的知识</Typography.Title>
          <Typography.Text type="secondary">统一查找笔记、知识库、会话和网页剪藏内容</Typography.Text>
        </div>

        <Input
          className="global-search-input"
          size="large"
          prefix={<Search size={18} />}
          placeholder="输入关键词，搜索全部内容..."
          value={query}
          autoFocus
          onChange={(event) => setQuery(event.target.value)}
        />

        <Segmented
          className="search-scope"
          options={scopeOptions}
          value={scope}
          onChange={(value) => setScope(String(value))}
        />

        <div className="search-results-card">
          {searchResults.length === 0 ? (
            <Empty description={searchLoading ? '正在加载搜索数据' : normalizedQuery ? '暂无搜索结果' : '输入关键词开始搜索'} />
          ) : (
            <List
              split={false}
              dataSource={searchResults}
              renderItem={(result) => (
                <List.Item
                  className="search-result-item"
                  role="button"
                  tabIndex={0}
                  onClick={result.onOpen}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      result.onOpen();
                    }
                  }}
                >
                  <List.Item.Meta
                    avatar={
                      <span className="search-result-icon">
                        <result.icon size={18} strokeWidth={1.8} />
                      </span>
                    }
                    title={
                      <Space>
                        {result.title}
                        <Tag bordered={false}>{result.scope}</Tag>
                      </Space>
                    }
                    description={result.description}
                  />
                </List.Item>
              )}
            />
          )}
        </div>
      </div>
    </section>
  );
}

function toSearchResult(
  result: GlobalSearchResult,
  onConversationSelect: (conversationId: string) => void,
  onKnowledgeDocumentSelect: (knowledgeBaseId: string, documentId: string) => void,
  onNoteSelect: (noteId: string) => void,
): SearchResult {
  if (result.type === 'KNOWLEDGE') {
    return {
      id: result.id,
      title: result.title,
      description: result.description,
      scope: '知识库',
      icon: Database,
      onOpen: () => {
        if (result.knowledgeBaseId) {
          onKnowledgeDocumentSelect(result.knowledgeBaseId, result.targetId);
        }
      },
    };
  }

  if (result.type === 'CONVERSATION') {
    return {
      id: result.id,
      title: result.title,
      description: `更新时间：${formatSearchTime(result.updatedAt)}`,
      scope: '会话',
      icon: MessageCircle,
      onOpen: () => onConversationSelect(result.targetId),
    };
  }

  if (result.type === 'WEB') {
    return {
      id: result.id,
      title: result.title,
      description: result.description,
      scope: '网页',
      icon: Database,
      onOpen: () => {
        if (result.knowledgeBaseId) {
          onKnowledgeDocumentSelect(result.knowledgeBaseId, result.targetId);
        }
      },
    };
  }

  return {
    id: result.id,
    title: result.title,
    description: result.description,
    scope: '笔记',
    icon: FileText,
    onOpen: () => onNoteSelect(result.targetId),
  };
}

function formatSearchTime(updatedAt: string) {
  const date = parseSearchDate(updatedAt);

  if (Number.isNaN(date.getTime())) {
    return updatedAt || '-';
  }

  const pad = (value: number) => String(value).padStart(2, '0');

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}`;
}

function parseSearchDate(updatedAt: string) {
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

export default SearchPage;
