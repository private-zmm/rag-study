import { Layout } from 'antd';
import { useState } from 'react';
import AppSidebar from '../components/AppSidebar';
import ChatPage from '../pages/ChatPage';
import ClipperPage from '../pages/ClipperPage';
import KnowledgePage from '../pages/KnowledgePage';
import NotesPage from '../pages/NotesPage';
import SearchPage from '../pages/SearchPage';
import SettingsPage from '../pages/SettingsPage';
import type { PageKey, User } from '../types';

type AppLayoutProps = {
  activePage: PageKey;
  onLogout: () => void;
  onPageChange: (page: PageKey) => void;
  user: User;
};

function AppLayout({ activePage, onLogout, onPageChange, user }: AppLayoutProps) {
  const [newChatVersion, setNewChatVersion] = useState(0);
  const [selectedConversationId, setSelectedConversationId] = useState<string>();
  const [conversationRefreshVersion, setConversationRefreshVersion] = useState(0);
  const [selectedNoteId, setSelectedNoteId] = useState<string>();
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<string>();
  const [selectedKnowledgeDocumentId, setSelectedKnowledgeDocumentId] = useState<string>();

  const handlePageChange = (page: PageKey) => {
    if (page === 'chat') {
      setSelectedConversationId(undefined);
      setNewChatVersion((version) => version + 1);
    }

    onPageChange(page);
  };

  const handleConversationSelect = (conversationId: string) => {
    setSelectedConversationId(conversationId);
    onPageChange('chat');
  };

  const handleNoteSelect = (noteId: string) => {
    setSelectedNoteId(noteId);
    onPageChange('notes');
  };

  const handleKnowledgeDocumentSelect = (knowledgeBaseId: string, documentId: string) => {
    setSelectedKnowledgeBaseId(knowledgeBaseId);
    setSelectedKnowledgeDocumentId(documentId);
    onPageChange('knowledge');
  };

  const handleConversationCreated = (conversationId: string) => {
    setSelectedConversationId(conversationId);
    setConversationRefreshVersion((version) => version + 1);
  };

  const handleConversationDeleted = (conversationId: string) => {
    setConversationRefreshVersion((version) => version + 1);

    if (selectedConversationId === conversationId) {
      setSelectedConversationId(undefined);
      setNewChatVersion((version) => version + 1);
      onPageChange('chat');
    }
  };

  const handleConversationArchived = (conversationId: string) => {
    setConversationRefreshVersion((version) => version + 1);

    if (selectedConversationId === conversationId) {
      setSelectedConversationId(undefined);
      setNewChatVersion((version) => version + 1);
      onPageChange('chat');
    }
  };

  const pageMap: Record<PageKey, React.ReactNode> = {
    chat: (
      <ChatPage
        newChatVersion={newChatVersion}
        selectedConversationId={selectedConversationId}
        onConversationCreated={handleConversationCreated}
      />
    ),
    search: (
      <SearchPage
        onConversationSelect={handleConversationSelect}
        onKnowledgeDocumentSelect={handleKnowledgeDocumentSelect}
        onNoteSelect={handleNoteSelect}
      />
    ),
    notes: <NotesPage selectedNoteId={selectedNoteId} />,
    knowledge: (
      <KnowledgePage
        selectedDocumentId={selectedKnowledgeDocumentId}
        selectedKnowledgeBaseId={selectedKnowledgeBaseId}
      />
    ),
    clipper: <ClipperPage />,
    settings: <SettingsPage onConversationsChanged={() => setConversationRefreshVersion((version) => version + 1)} />,
  };

  return (
    <Layout className="app-shell">
      <AppSidebar
        activePage={activePage}
        conversationRefreshVersion={conversationRefreshVersion}
        onConversationSelect={handleConversationSelect}
        onConversationArchived={handleConversationArchived}
        onConversationDeleted={handleConversationDeleted}
        onLogout={onLogout}
        onPageChange={handlePageChange}
        selectedConversationId={selectedConversationId}
        user={user}
      />
      <Layout.Content className="app-content">{pageMap[activePage]}</Layout.Content>
    </Layout>
  );
}

export default AppLayout;
