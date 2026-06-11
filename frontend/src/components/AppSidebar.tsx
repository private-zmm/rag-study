import { Avatar, Button, Divider, Dropdown, Input, Layout, Modal, Typography, message } from 'antd';
import type { MenuProps } from 'antd';
import { Archive, Ellipsis, LogOut, PanelLeftClose, Pencil, Settings, Trash2 } from 'lucide-react';
import { Fragment, useEffect, useMemo, useState } from 'react';
import {
  archiveChatConversation,
  deleteChatConversation,
  fetchChatConversations,
  renameChatConversation,
} from '../api/client';
import { navItems } from '../data/mockData';
import type { ChatConversation, PageKey, User } from '../types';

type AppSidebarProps = {
  activePage: PageKey;
  conversationRefreshVersion: number;
  onConversationArchived: (conversationId: string) => void;
  onConversationDeleted: (conversationId: string) => void;
  onConversationSelect: (conversationId: string) => void;
  onLogout: () => void;
  onPageChange: (page: PageKey) => void;
  selectedConversationId?: string;
  user: User;
};

function AppSidebar({
  activePage,
  conversationRefreshVersion,
  onConversationArchived,
  onConversationDeleted,
  onConversationSelect,
  onLogout,
  onPageChange,
  selectedConversationId,
  user,
}: AppSidebarProps) {
  const displayName = user.nickname || user.username;
  const avatarText = displayName.slice(0, 1).toUpperCase();
  const [conversations, setConversations] = useState<ChatConversation[]>([]);
  const [editingConversationId, setEditingConversationId] = useState<string>();
  const [draftTitle, setDraftTitle] = useState('');
  const groupedConversations = useMemo(() => groupConversationsByTime(conversations), [conversations]);

  useEffect(() => {
    fetchChatConversations()
      .then((activeConversationList) => {
        setConversations(activeConversationList);
      })
      .catch(() => message.error('历史会话加载失败'));
  }, [conversationRefreshVersion]);

  const startRename = (conversation: ChatConversation) => {
    setEditingConversationId(conversation.id);
    setDraftTitle(conversation.title);
  };

  const finishRename = async (conversation: ChatConversation) => {
    if (editingConversationId !== conversation.id) {
      return;
    }

    const nextTitle = draftTitle.trim();
    setEditingConversationId(undefined);

    if (!nextTitle || nextTitle === conversation.title) {
      setDraftTitle('');
      return;
    }

    try {
      const renamedConversation = await renameChatConversation(conversation.id, nextTitle);
      const updateRenamedConversation = (currentConversations: ChatConversation[]) =>
        currentConversations.map((item) => (item.id === conversation.id ? renamedConversation : item));

      setConversations(updateRenamedConversation);

      message.success('会话已重命名');
    } catch {
      message.error('会话重命名失败');
    } finally {
      setDraftTitle('');
    }
  };

  const handleArchive = async (conversationId: string) => {
    try {
      await archiveChatConversation(conversationId);
      setConversations((currentConversations) => currentConversations.filter((item) => item.id !== conversationId));
      onConversationArchived(conversationId);
      message.success('会话已归档');
    } catch {
      message.error('会话归档失败');
    }
  };

  const handleDelete = async (conversationId: string) => {
    try {
      await deleteChatConversation(conversationId);
      setConversations((currentConversations) => currentConversations.filter((item) => item.id !== conversationId));
      onConversationDeleted(conversationId);
      message.success('会话已删除');
    } catch {
      message.error('会话删除失败');
    }
  };

  const confirmDelete = (conversation: ChatConversation) => {
    Modal.confirm({
      title: '删除这个会话？',
      content: '删除后无法恢复。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleDelete(conversation.id),
    });
  };

  const getConversationMenuItems = (conversation: ChatConversation): MenuProps['items'] => [
    { key: 'rename', icon: <Pencil size={17} />, label: '重命名' },
    { key: 'archive', icon: <Archive size={17} />, label: '归档' },
    { key: 'delete', icon: <Trash2 size={17} />, label: <span className="conversation-menu-danger">删除</span> },
  ];

  const handleConversationMenuClick = (conversation: ChatConversation, key: string) => {
    if (key === 'rename') {
      startRename(conversation);
      return;
    }

    if (key === 'archive') {
      void handleArchive(conversation.id);
      return;
    }

    if (key === 'delete') {
      confirmDelete(conversation);
    }
  };

  const renderConversationLabel = (conversation: ChatConversation, showTime = false) => (
    <span className={showTime ? 'conversation-row with-time' : 'conversation-row'} onClick={(event) => event.stopPropagation()}>
      {editingConversationId === conversation.id ? (
        <Input
          autoFocus
          className="conversation-rename-input"
          value={draftTitle}
          variant="borderless"
          onBlur={() => void finishRename(conversation)}
          onChange={(event) => setDraftTitle(event.target.value)}
          onKeyDown={(event) => {
            event.stopPropagation();

            if (event.key === 'Enter') {
              event.currentTarget.blur();
            }

            if (event.key === 'Escape') {
              setEditingConversationId(undefined);
              setDraftTitle('');
            }
          }}
        />
      ) : (
        <button className="conversation-title-button" type="button" onClick={() => onConversationSelect(conversation.id)}>
          {conversation.title}
        </button>
      )}
      {showTime ? <span className="conversation-relative-time">{formatRelativeConversationTime(conversation.updatedAt)}</span> : null}
      <Dropdown
        menu={{
          items: getConversationMenuItems(conversation),
          onClick: ({ key, domEvent }) => {
            domEvent.stopPropagation();
            handleConversationMenuClick(conversation, key);
          },
        }}
        classNames={{ root: 'conversation-actions-dropdown' }}
        placement="bottomRight"
        trigger={['click']}
      >
        <Button
          className="conversation-more-button"
          type="text"
          shape="circle"
          icon={<Ellipsis size={16} />}
          onClick={(event) => {
            event.stopPropagation();
          }}
        />
      </Dropdown>
    </span>
  );

  return (
    <Layout.Sider className="app-sidebar" width={232}>
      <div className="sidebar-brand">
        <span className="brand-badge">R</span>
        <Typography.Text strong>RAG Study</Typography.Text>
        <Button className="sidebar-icon-button" type="text" icon={<PanelLeftClose size={17} />} />
      </div>

      <nav className="app-nav" aria-label="主导航">
        {navItems.map((item) => (
          <button
            className={activePage === item.key ? 'active' : ''}
            key={item.key}
            type="button"
            onClick={() => onPageChange(item.key)}
          >
          <item.icon size={15} strokeWidth={1.8} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>

      <Divider className="sidebar-divider" />

      <div className="sidebar-conversation-groups">
        {groupedConversations.map((group) => (
          <Fragment key={group.label}>
            <div className="sidebar-section-title conversation-time-group-title">{group.label}</div>
            <div className="sidebar-conversation-group">
              {group.items.map((conversation) => (
                <button
                  className={
                    selectedConversationId === conversation.id
                      ? 'sidebar-conversation-item active'
                      : 'sidebar-conversation-item'
                  }
                  key={conversation.id}
                  type="button"
                  onClick={() => onConversationSelect(conversation.id)}
                >
                  {renderConversationLabel(conversation, true)}
                </button>
              ))}
            </div>
          </Fragment>
        ))}
      </div>

      <Dropdown
        menu={{
          items: [
            { key: 'settings', icon: <Settings size={15} />, label: '系统设置' },
            { key: 'logout', icon: <LogOut size={15} />, label: '退出登录' },
          ],
          onClick: ({ key }) => {
            if (key === 'settings') {
              onPageChange('settings');
            }

            if (key === 'logout') {
              onLogout();
            }
          },
        }}
        trigger={['click']}
      >
        <button className="sidebar-user" type="button">
          <Avatar style={{ background: '#1677ff' }}>{avatarText}</Avatar>
          <div>
            <Typography.Text>{displayName}</Typography.Text>
            <Typography.Paragraph type="secondary">{user.email}</Typography.Paragraph>
          </div>
        </button>
      </Dropdown>
    </Layout.Sider>
  );
}

type ConversationTimeGroup = {
  label: string;
  items: ChatConversation[];
};

function groupConversationsByTime(conversations: ChatConversation[]): ConversationTimeGroup[] {
  const groups: ConversationTimeGroup[] = [
    { label: '今天', items: [] },
    { label: '过去 7 天', items: [] },
    { label: '过去 30 天', items: [] },
    { label: '更早', items: [] },
  ];

  conversations.forEach((conversation) => {
    const days = getDaysSince(conversation.updatedAt);

    if (days <= 0) {
      groups[0].items.push(conversation);
      return;
    }

    if (days <= 7) {
      groups[1].items.push(conversation);
      return;
    }

    if (days <= 30) {
      groups[2].items.push(conversation);
      return;
    }

    groups[3].items.push(conversation);
  });

  return groups.filter((group) => group.items.length > 0);
}

function formatRelativeConversationTime(updatedAt: string) {
  const updatedDate = parseConversationDate(updatedAt);

  if (Number.isNaN(updatedDate.getTime())) {
    return '';
  }

  const seconds = Math.max(0, Math.floor((Date.now() - updatedDate.getTime()) / 1000));

  if (seconds < 60) {
    return '刚刚';
  }

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}分钟前`;
  }

  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}小时前`;
  }

  const days = Math.floor(hours / 24);
  if (days < 7) {
    return `${days}天前`;
  }

  const weeks = Math.floor(days / 7);
  if (weeks < 5) {
    return `${weeks}周前`;
  }

  const months = Math.floor(days / 30);
  if (months < 12) {
    return `${months}个月前`;
  }

  return `${Math.floor(days / 365)}年前`;
}

function getDaysSince(updatedAt: string) {
  const updatedDate = parseConversationDate(updatedAt);

  if (Number.isNaN(updatedDate.getTime())) {
    return Number.POSITIVE_INFINITY;
  }

  const today = new Date();
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  const updatedStart = new Date(updatedDate.getFullYear(), updatedDate.getMonth(), updatedDate.getDate()).getTime();

  return Math.floor((todayStart - updatedStart) / 86_400_000);
}

function parseConversationDate(updatedAt: string) {
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

export default AppSidebar;
