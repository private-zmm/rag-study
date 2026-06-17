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
} from 'lucide-react';
import type { ChatConversation, ChatReference, KnowledgeBase, Note } from '../../../types';
import type { ReferenceMenuView, ReferenceNoteTreeNode } from './referenceUtils';

const referenceActions = [
  { key: 'file', label: '上传文件', icon: Paperclip },
  { key: 'note', label: '引用笔记', icon: FileText, hasSubmenu: true },
  { key: 'knowledge', label: '引用知识库', icon: Database, hasSubmenu: true },
  { key: 'conversation', label: '引用其他对话', icon: MessageCircle, hasSubmenu: true },
];

type ReferenceMenuProps = {
  view: ReferenceMenuView;
  knowledgeBases: KnowledgeBase[];
  noteTree: ReferenceNoteTreeNode[];
  selectableConversations: ChatConversation[];
  selectedKnowledgeBaseId?: string;
  selectedReferences: ChatReference[];
  openNoteFolderKeys: Set<string>;
  onViewChange: (view: ReferenceMenuView) => void;
  onClose: () => void;
  onFileClick: () => void;
  onKnowledgeBaseChange: (knowledgeBaseId?: string) => void;
  onNoteSelect: (note: Note) => void;
  onConversationSelect: (conversation: ChatConversation) => void;
  onNoteFolderToggle: (folderKey: string) => void;
};

function ReferenceMenu({
  view,
  knowledgeBases,
  noteTree,
  selectableConversations,
  selectedKnowledgeBaseId,
  selectedReferences,
  openNoteFolderKeys,
  onViewChange,
  onClose,
  onFileClick,
  onKnowledgeBaseChange,
  onNoteSelect,
  onConversationSelect,
  onNoteFolderToggle,
}: ReferenceMenuProps) {
  const handleReferenceAction = (actionKey: string) => {
    if (actionKey === 'file') {
      onFileClick();
      onClose();
      return;
    }

    if (actionKey === 'note') {
      onViewChange('notes');
      return;
    }

    if (actionKey === 'knowledge') {
      onViewChange('knowledge');
      return;
    }

    if (actionKey === 'conversation') {
      onViewChange('conversations');
    }
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
              onClick={() => onNoteFolderToggle(node.key)}
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
          onClick={() => onNoteSelect(node.note)}
        >
          <FileText size={19} strokeWidth={1.8} />
          <span>{node.name}</span>
          {selected ? <Check size={18} strokeWidth={2} /> : null}
        </button>
      );
    });
  };

  return (
    <div className="reference-popover" aria-label="引用菜单">
      {view === 'main' ? (
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
      ) : view === 'knowledge' ? (
        <>
          <button className="reference-menu-item reference-menu-back" type="button" onClick={() => onViewChange('main')}>
            <ChevronLeft size={20} strokeWidth={1.8} />
            <span>引用知识库</span>
          </button>
          <button
            className={!selectedKnowledgeBaseId ? 'reference-menu-item selected' : 'reference-menu-item'}
            type="button"
            onClick={() => {
              onKnowledgeBaseChange(undefined);
              onClose();
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
                onKnowledgeBaseChange(kb.id);
                onClose();
              }}
            >
              <Database size={22} strokeWidth={1.8} />
              <span>{kb.name}</span>
              {selectedKnowledgeBaseId === kb.id ? <Check size={18} strokeWidth={2} /> : null}
            </button>
          ))}
          {knowledgeBases.length === 0 ? <div className="reference-menu-empty">暂无知识库</div> : null}
        </>
      ) : view === 'notes' ? (
        <>
          <button className="reference-menu-item reference-menu-back" type="button" onClick={() => onViewChange('main')}>
            <ChevronLeft size={20} strokeWidth={1.8} />
            <span>引用笔记</span>
          </button>
          <div className="reference-menu-list">
            {noteTree.length > 0 ? renderReferenceNoteTree(noteTree) : <div className="reference-menu-empty">暂无笔记</div>}
          </div>
        </>
      ) : (
        <>
          <button className="reference-menu-item reference-menu-back" type="button" onClick={() => onViewChange('main')}>
            <ChevronLeft size={20} strokeWidth={1.8} />
            <span>引用其他对话</span>
          </button>
          <div className="reference-menu-list">
            {selectableConversations.map((conversation) => {
              const selected = selectedReferences.some(
                (reference) => reference.type === 'conversation' && reference.id === conversation.id,
              );

              return (
                <button
                  className={selected ? 'reference-menu-item selected' : 'reference-menu-item'}
                  key={conversation.id}
                  type="button"
                  onClick={() => onConversationSelect(conversation)}
                >
                  <MessageCircle size={22} strokeWidth={1.8} />
                  <span>{conversation.title || '未命名对话'}</span>
                  {selected ? <Check size={18} strokeWidth={2} /> : null}
                </button>
              );
            })}
            {selectableConversations.length === 0 ? <div className="reference-menu-empty">暂无可引用对话</div> : null}
          </div>
        </>
      )}
    </div>
  );
}

export default ReferenceMenu;
