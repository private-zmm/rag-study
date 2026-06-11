import { Button, Dropdown, Input, Modal, Select, Spin, Typography, message } from 'antd';
import type { MenuProps } from 'antd';
import {
  ChevronRight,
  Copy,
  Database,
  Trash2,
  Download,
  Eye,
  FilePlus2,
  Folder,
  FolderOpen,
  FolderPlus,
  LocateFixed,
  MoveRight,
  Pencil,
  Plus,
  Save,
  Upload,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type Vditor from 'vditor';
import 'vditor/dist/index.css';
import {
  createNote,
  createNoteFolder,
  deleteNote,
  deleteNoteFolder,
  fetchKnowledgeBases,
  fetchNoteFolders,
  fetchNotes,
  renameNote,
  saveNote,
  syncNotesToKnowledge,
} from '../api/client';
import { NoteImportControls, type NoteImportControlsHandle } from '../components/NoteImportControls';
import { useApiResource } from '../hooks/useApiResource';
import type { KnowledgeBase, Note, NoteFolder } from '../types';

const editorToolbar = [
  'headings',
  'bold',
  'italic',
  'strike',
  '|',
  'list',
  'ordered-list',
  'check',
  'outdent',
  'indent',
  '|',
  'quote',
  'line',
  'code',
  'inline-code',
  'table',
  'link',
  '|',
  'undo',
  'redo',
  '|',
  'fullscreen',
];

const noteFeedbackMessageKey = 'note-feedback';
const emptyNotes: Note[] = [];
const emptyFolders: NoteFolder[] = [];
const emptyKnowledgeBases: KnowledgeBase[] = [];

type NoteTreeNode = NoteFolderTreeNode | NoteFileTreeNode;

type NoteFolderTreeNode = {
  type: 'folder';
  key: string;
  folderId?: string;
  name: string;
  children: NoteTreeNode[];
};

type NoteFileTreeNode = {
  type: 'note';
  key: string;
  name: string;
  note: Note;
};

type MutableFolderTreeNode = NoteFolderTreeNode & {
  folderMap: Map<string, MutableFolderTreeNode>;
};

type NotesPageProps = {
  selectedNoteId?: string;
};

function NotesPage({ selectedNoteId }: NotesPageProps) {
  const { data: remoteNotes, loading: notesLoading } = useApiResource(fetchNotes, emptyNotes);
  const { data: remoteFolders, loading: foldersLoading } = useApiResource(fetchNoteFolders, emptyFolders);
  const { data: knowledgeBases } = useApiResource(fetchKnowledgeBases, emptyKnowledgeBases);
  const [notes, setNotes] = useState<Note[]>([]);
  const [folders, setFolders] = useState<NoteFolder[]>([]);
  const [activeNoteId, setActiveNoteId] = useState<string>();
  const [openFolderKeys, setOpenFolderKeys] = useState<Set<string>>(new Set());
  const [editingNoteId, setEditingNoteId] = useState<string>();
  const [draftNoteTitle, setDraftNoteTitle] = useState('');
  const [renameInputPlacement, setRenameInputPlacement] = useState<'tree' | 'title'>('tree');
  const [saving, setSaving] = useState(false);
  const [importing, setImporting] = useState(false);
  const [syncingKnowledge, setSyncingKnowledge] = useState(false);
  const [editorReady, setEditorReady] = useState(false);
  const editorRef = useRef<Vditor | null>(null);
  const noteImportControlsRef = useRef<NoteImportControlsHandle | null>(null);
  const activeNoteIdRef = useRef<string | undefined>(undefined);
  const knownFolderKeysRef = useRef<Set<string>>(new Set());
  const noteTree = useMemo(() => buildNoteTree(notes, folders), [notes, folders]);
  const allFolderKeys = useMemo(() => collectFolderKeys(noteTree), [noteTree]);
  const activeNote = useMemo<Note | undefined>(() => {
    if (notesLoading || foldersLoading) {
      return undefined;
    }

    return notes.find((note) => note.id === activeNoteId) ?? notes[0];
  }, [activeNoteId, foldersLoading, notes, notesLoading]);
  const loadingInitialNotes = notesLoading || foldersLoading;

  useEffect(() => {
    activeNoteIdRef.current = activeNote?.id;
  }, [activeNote?.id]);

  useEffect(() => {
    setNotes(remoteNotes);
    setActiveNoteId((currentId) => {
      if (selectedNoteId && remoteNotes.some((note) => note.id === selectedNoteId)) {
        return selectedNoteId;
      }

      if (currentId && remoteNotes.some((note) => note.id === currentId)) {
        return currentId;
      }

      return remoteNotes[0]?.id;
    });
  }, [remoteNotes, selectedNoteId]);

  useEffect(() => {
    if (selectedNoteId && notes.some((note) => note.id === selectedNoteId)) {
      setActiveNoteId(selectedNoteId);
    }
  }, [notes, selectedNoteId]);

  useEffect(() => {
    setFolders(remoteFolders);
  }, [remoteFolders]);

  useEffect(() => {
    setOpenFolderKeys((currentKeys) => {
      const nextKeys = new Set(currentKeys);
      let changed = false;

      allFolderKeys.forEach((folderKey) => {
        if (!knownFolderKeysRef.current.has(folderKey)) {
          knownFolderKeysRef.current.add(folderKey);
          nextKeys.add(folderKey);
          changed = true;
        }
      });

      return changed ? nextKeys : currentKeys;
    });
  }, [allFolderKeys]);

  useEffect(() => {
    let disposed = false;

    if (!activeNote) {
      setEditorReady(false);
      editorRef.current?.destroy();
      editorRef.current = null;
      return;
    }

    import('vditor')
      .then(({ default: VditorEditor }) => {
        if (disposed || editorRef.current || !document.getElementById('notes-vditor-editor')) {
          return;
        }

        const editor = new VditorEditor('notes-vditor-editor', {
          cache: { enable: false },
          counter: { enable: true },
          height: '100%',
          mode: 'wysiwyg',
          placeholder: '输入 Markdown 内容，支持标题、表格、代码块、引用和任务列表',
          toolbar: editorToolbar,
          value: activeNote?.content ?? '',
          after: () => {
            editorRef.current = editor;
            setEditorReady(true);
          },
          input: (value) => {
            const targetNoteId = activeNoteIdRef.current;

            if (!targetNoteId) {
              return;
            }

            setNotes((currentNotes) =>
              currentNotes.map((note) => (note.id === targetNoteId ? { ...note, content: value } : note)),
            );
          },
        });
      })
      .catch(() => {
        message.error('Markdown 编辑器加载失败');
      });

    return () => {
      disposed = true;
      setEditorReady(false);
      editorRef.current?.destroy();
      editorRef.current = null;
    };
  }, [activeNote?.id]);

  useEffect(() => {
    const editor = editorRef.current;

    if (!editorReady || !editor || !activeNote) {
      return;
    }

    if (editor.getValue() !== activeNote.content) {
      editor.setValue(activeNote.content);
    }
  }, [activeNote, editorReady]);

  const handleSave = async () => {
    if (!activeNote) {
      return;
    }

    setSaving(true);

    try {
      const savedNote = await saveNote(activeNote);
      setNotes((currentNotes) => currentNotes.map((note) => (note.id === savedNote.id ? savedNote : note)));
      message.success({ content: '笔记已保存到后端', key: noteFeedbackMessageKey });
    } catch {
      message.error('保存失败，先检查后端是否启动');
    } finally {
      setSaving(false);
    }
  };

  const handleCreateNote = async () => {
    setSaving(true);

    try {
      const createdNote = await createNote({
        title: '未命名笔记',
        content: '# 未命名笔记\n\n开始记录你的想法。',
      });

      setNotes((currentNotes) => [createdNote, ...currentNotes]);
      setActiveNoteId(createdNote.id);
      message.success('已新建笔记');
    } catch {
      message.error('新建失败，先检查后端是否启动');
    } finally {
      setSaving(false);
    }
  };

  const handleCreateFolder = async () => {
    let folderName = '';

    Modal.confirm({
      title: '新建文件夹',
      content: (
        <Input
          autoFocus
          placeholder="文件夹名称"
          onChange={(event) => {
            folderName = event.target.value;
          }}
          onPressEnter={(event) => {
            event.currentTarget.blur();
          }}
        />
      ),
      okText: '创建',
      cancelText: '取消',
      centered: true,
      icon: <FolderPlus size={20} />,
      onOk: async () => {
        const nextFolderName = folderName.trim();

        if (!nextFolderName) {
          message.warning('文件夹名称不能为空');
          throw new Error('文件夹名称不能为空');
        }

        const createdFolder = await createNoteFolder(nextFolderName);
        setFolders((currentFolders) => [createdFolder, ...currentFolders]);
        setOpenFolderKeys((currentKeys) => new Set(currentKeys).add(`root/${createdFolder.path}`));
        message.success('文件夹已创建');
      },
    });
  };

  const openFileImport = () => {
    noteImportControlsRef.current?.openFileImport();
  };

  const openFolderImport = async () => {
    await noteImportControlsRef.current?.openFolderImport();
  };

  const handleImportedNotes = (importedNotes: Note[]) => {
    setNotes((currentNotes) => [...importedNotes, ...currentNotes]);
    setActiveNoteId(importedNotes[0]?.id ?? activeNoteIdRef.current);
  };

  const headings = activeNote ? extractMarkdownHeadings(activeNote.content) : [];

  const handleTocClick = (headingIndex: number) => {
    const headingElements = document.querySelectorAll<HTMLHeadingElement>(
      '.markdown-editor-frame .vditor-reset h1, .markdown-editor-frame .vditor-reset h2, .markdown-editor-frame .vditor-reset h3, .markdown-editor-frame .vditor-reset h4, .markdown-editor-frame .vditor-reset h5, .markdown-editor-frame .vditor-reset h6',
    );
    const targetHeading = headingElements[headingIndex];

    targetHeading?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  };

  const locateActiveNote = () => {
    if (!activeNote) {
      return;
    }

    const folderPathParts = splitNotePath(activeNote.title).slice(0, -1);
    const folderKeys = folderPathParts.reduce<string[]>((keys, _part, index) => {
      keys.push(`root/${folderPathParts.slice(0, index + 1).join('/')}`);
      return keys;
    }, []);

    setActiveNoteId(activeNote.id);
    setOpenFolderKeys((currentKeys) => new Set([...currentKeys, ...folderKeys]));

    window.requestAnimationFrame(() => {
      document
        .querySelector<HTMLElement>(`[data-note-id="${activeNote.id}"]`)
        ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  };

  const startNoteRename = (note: Note, placement: 'tree' | 'title' = 'tree') => {
    setActiveNoteId(note.id);
    setEditingNoteId(note.id);
    setDraftNoteTitle(getNoteDisplayName(note));
    setRenameInputPlacement(placement);
  };

  const cancelNoteRename = () => {
    setEditingNoteId(undefined);
    setDraftNoteTitle('');
  };

  const finishNoteRename = async (note: Note) => {
    if (editingNoteId !== note.id) {
      return;
    }

    const nextTitle = draftNoteTitle.trim();
    setEditingNoteId(undefined);

    if (!nextTitle || nextTitle === note.title) {
      setDraftNoteTitle('');
      return;
    }

    const nextStoredTitle = buildRenamedNoteTitle(note.title, nextTitle);

    if (nextStoredTitle === note.title) {
      setDraftNoteTitle('');
      return;
    }

    const renamedNote = { ...note, title: nextStoredTitle };
    setNotes((currentNotes) => currentNotes.map((currentNote) => (currentNote.id === note.id ? renamedNote : currentNote)));

    try {
      const savedNote = await renameNote(renamedNote);
      setNotes((currentNotes) => currentNotes.map((currentNote) => (currentNote.id === savedNote.id ? savedNote : currentNote)));
      message.success({ content: '笔记已重命名', key: noteFeedbackMessageKey });
    } catch {
      setNotes((currentNotes) => currentNotes.map((currentNote) => (currentNote.id === note.id ? note : currentNote)));
      message.error('重命名失败，先检查后端是否启动');
    } finally {
      setDraftNoteTitle('');
    }
  };

  const openSyncToKnowledgeModal = (targetNotes: Note[], defaultTitle: string) => {
    if (targetNotes.length === 0) {
      message.info('没有可同步的笔记');
      return;
    }

    if (knowledgeBases.length === 0) {
      message.info('请先在知识库页面创建一个知识库');
      return;
    }

    let selectedKnowledgeBaseId = knowledgeBases[0]?.id;

    Modal.confirm({
      title: '同步到知识库',
      content: (
        <div className="note-sync-modal-content">
          <Typography.Paragraph type="secondary">
            将「{defaultTitle}」同步为知识库文档，并自动向量化。
          </Typography.Paragraph>
          <Select
            style={{ width: '100%' }}
            value={selectedKnowledgeBaseId}
            options={knowledgeBases.map((knowledgeBase) => ({
              label: knowledgeBase.name,
              value: knowledgeBase.id,
            }))}
            onChange={(value) => {
              selectedKnowledgeBaseId = value;
            }}
          />
        </div>
      ),
      okText: '开始同步',
      cancelText: '取消',
      centered: true,
      icon: <Database size={20} />,
      onOk: async () => {
        if (!selectedKnowledgeBaseId) {
          message.warning('请选择知识库');
          throw new Error('请选择知识库');
        }

        await syncNotesToKnowledgeBase(selectedKnowledgeBaseId, targetNotes);
      },
    });
  };

  const syncNotesToKnowledgeBase = async (knowledgeBaseId: string, targetNotes: Note[]) => {
    setSyncingKnowledge(true);

    try {
      const result = await syncNotesToKnowledge({
        knowledgeBaseId,
        noteIds: targetNotes.map((note) => note.id),
      });

      message.success(`已同步 ${result.syncedNotes} 篇笔记，向量化 ${result.indexedChunks} 个分块`);
    } catch {
      message.error('同步到知识库失败，请检查知识库和向量服务配置');
    } finally {
      setSyncingKnowledge(false);
    }
  };

  const deleteNotesByIds = async (noteIds: string[], successMessage: string) => {
    if (noteIds.length === 0) {
      return;
    }

    setSaving(true);

    try {
      await Promise.all(noteIds.map((noteId) => deleteNote(noteId)));
      setNotes((currentNotes) => {
        const deletedIds = new Set(noteIds);
        const nextNotes = currentNotes.filter((note) => !deletedIds.has(note.id));

        setActiveNoteId((currentActiveNoteId) => {
          if (currentActiveNoteId && !deletedIds.has(currentActiveNoteId)) {
            return currentActiveNoteId;
          }

          return nextNotes[0]?.id;
        });

        return nextNotes;
      });
      message.success(successMessage);
    } catch {
      message.error('删除失败，先检查后端是否启动');
    } finally {
      setSaving(false);
    }
  };

  const confirmDeleteNote = (note: Note) => {
    Modal.confirm({
      title: '删除笔记',
      content: `确定删除「${getNoteDisplayName(note)}」吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      centered: true,
      icon: <Trash2 size={20} />,
      onOk: async () => {
        await deleteNotesByIds([note.id], '笔记已删除');
      },
    });
  };

  const confirmDeleteFolder = (folder: NoteFolderTreeNode) => {
    const folderNotes = collectNotesFromTree(folder.children);

    Modal.confirm({
      title: '删除文件夹',
      content: `确定删除「${folder.name}」及其下面的 ${folderNotes.length} 篇笔记吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      centered: true,
      icon: <Trash2 size={20} />,
      onOk: async () => {
        if (folderNotes.length > 0) {
          await deleteNotesByIds(
            folderNotes.map((note) => note.id),
            '文件夹已删除',
          );
        }

        if (folder.folderId) {
          await deleteNoteFolder(folder.folderId);
          setFolders((currentFolders) => currentFolders.filter((currentFolder) => currentFolder.id !== folder.folderId));
        }
      },
    });
  };

  const noteActionItems: MenuProps['items'] = [
    { key: 'new-file', icon: <FilePlus2 size={15} />, label: '新建文件' },
    { key: 'new-folder', icon: <FolderPlus size={15} />, label: '新建文件夹' },
    { key: 'rename', icon: <Pencil size={15} />, label: '重命名' },
    { key: 'move', icon: <MoveRight size={15} />, label: '移动' },
    { key: 'copy', icon: <Copy size={15} />, label: '复制' },
    { type: 'divider' },
    { key: 'import-file', icon: <Upload size={15} />, label: '导入文件' },
    { key: 'import-folder', icon: <FolderPlus size={15} />, label: '导入文件夹' },
    { key: 'sync-knowledge', icon: <Database size={15} />, label: '同步到知识库' },
    { type: 'divider' },
    { key: 'delete', icon: <Trash2 size={15} />, label: '删除', danger: true },
    { key: 'export', icon: <Download size={15} />, label: '导出' },
  ];

  const handleNoteActionClick: MenuProps['onClick'] = ({ domEvent, key }) => {
    domEvent.stopPropagation();

    if (key === 'import-file') {
      openFileImport();
    }

    if (key === 'import-folder') {
      void openFolderImport();
    }

    if (key === 'new-file') {
      void handleCreateNote();
    }

    if (key === 'new-folder') {
      void handleCreateFolder();
    }
  };

  const toggleFolderNode = (folderKey: string) => {
    setOpenFolderKeys((currentKeys) => {
      if (currentKeys.has(folderKey)) {
        const nextKeys = new Set(currentKeys);

        nextKeys.delete(folderKey);
        return nextKeys;
      }

      return new Set(getFolderAncestorKeys(folderKey));
    });
  };

  const renderTreeNodes = (treeNodes: NoteTreeNode[]): ReactNode => {
    return treeNodes.map((node) => {
      if (node.type === 'folder') {
        const isOpen = openFolderKeys.has(node.key);

        return (
          <div className="note-tree-node" key={node.key}>
            <div
              className={isOpen ? 'note-folder-item open' : 'note-folder-item'}
              role="treeitem"
              tabIndex={0}
              aria-expanded={isOpen}
              onClick={() => toggleFolderNode(node.key)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  toggleFolderNode(node.key);
                }
              }}
            >
              <ChevronRight className="note-folder-caret" size={13} />
              {isOpen ? (
                <FolderOpen className="folder-icon" size={14} />
              ) : (
                <Folder className="folder-icon" size={14} />
              )}
              <span className="note-folder-title">{node.name}</span>
              <Dropdown
                classNames={{ root: 'note-actions-dropdown' }}
                menu={{
                  items: noteActionItems,
                  onClick: (info) => {
                    handleNoteActionClick(info);

                    if (info.key === 'delete') {
                      confirmDeleteFolder(node);
                    }

                    if (info.key === 'sync-knowledge') {
                      openSyncToKnowledgeModal(collectNotesFromTree(node.children), node.name);
                    }
                  },
                }}
                trigger={['click']}
              >
                <Button
                  className="row-add-button"
                  type="text"
                  shape="circle"
                  icon={<Plus size={14} />}
                  loading={importing || syncingKnowledge}
                  onClick={(event) => event.stopPropagation()}
                />
              </Dropdown>
            </div>
            {isOpen && <div className="note-tree-branch">{renderTreeNodes(node.children)}</div>}
          </div>
        );
      }

      return (
        <div
          key={node.key}
          className={node.note.id === activeNoteId ? 'note-tree-item active' : 'note-tree-item'}
          data-note-id={node.note.id}
          role="treeitem"
          tabIndex={0}
          aria-selected={node.note.id === activeNoteId}
          onClick={() => setActiveNoteId(node.note.id)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              setActiveNoteId(node.note.id);
            }
          }}
        >
          <span className="note-tree-content">
            {editingNoteId === node.note.id && renameInputPlacement === 'tree' ? (
              <Input
                autoFocus
                className="note-rename-input"
                value={draftNoteTitle}
                variant="borderless"
                onBlur={() => void finishNoteRename(node.note)}
                onChange={(event) => setDraftNoteTitle(event.target.value)}
                onClick={(event) => event.stopPropagation()}
                onKeyDown={(event) => {
                  event.stopPropagation();

                  if (event.key === 'Enter') {
                    event.currentTarget.blur();
                  }

                  if (event.key === 'Escape') {
                    cancelNoteRename();
                  }
                }}
              />
            ) : (
              <span className="note-tree-title">{node.name}</span>
            )}
          </span>
          <Button className="row-eye-button" type="text" shape="circle" icon={<Eye size={13} />} />
          <Dropdown
            classNames={{ root: 'note-actions-dropdown' }}
            menu={{
              items: noteActionItems,
              onClick: (info) => {
                handleNoteActionClick(info);

                if (info.key === 'rename') {
                  startNoteRename(node.note, 'tree');
                }

                if (info.key === 'delete') {
                  confirmDeleteNote(node.note);
                }

                if (info.key === 'sync-knowledge') {
                  openSyncToKnowledgeModal([node.note], getNoteDisplayName(node.note));
                }
              },
            }}
            trigger={['click']}
          >
            <Button
              className="row-add-button"
              type="text"
              shape="circle"
              icon={<Plus size={14} />}
              loading={importing || syncingKnowledge}
              onClick={(event) => event.stopPropagation()}
            />
          </Dropdown>
        </div>
      );
    });
  };

  return (
    <section className="notes-page">
      <NoteImportControls ref={noteImportControlsRef} onImported={handleImportedNotes} onImportingChange={setImporting} />
      {loadingInitialNotes && (
        <div className="notes-loading-mask">
          <Spin tip="正在加载笔记数据" />
        </div>
      )}
      <div className="notes-layout">
        <aside className="notes-list-panel">
          <div className="notes-panel-topbar">
            <Typography.Text>目录</Typography.Text>
            <span className="notes-panel-spacer" />
            <Button type="text" size="small" icon={<LocateFixed size={15} />} onClick={locateActiveNote} />
            <Dropdown
              classNames={{ root: 'note-actions-dropdown' }}
              menu={{ items: noteActionItems, onClick: handleNoteActionClick }}
              trigger={['click']}
            >
              <Button type="text" size="small" icon={<Plus size={14} />} loading={importing || syncingKnowledge} />
            </Dropdown>
          </div>
          <div className="notes-tree" aria-label="笔记目录" role="tree">
            {noteTree.length > 0 ? (
              renderTreeNodes(noteTree)
            ) : (
              <div className="notes-empty-tree">
                <Typography.Text type="secondary">暂无目录或笔记</Typography.Text>
                <Button size="small" type="link" onClick={() => void handleCreateFolder()}>
                  新建文件夹
                </Button>
              </div>
            )}
          </div>
        </aside>

        <main className="markdown-editor-panel">
          {!activeNote ? (
            <div className="notes-empty-main">
              <Typography.Title level={2}>还没有笔记</Typography.Title>
              <Typography.Paragraph type="secondary">
                可以新建一篇笔记，也可以直接导入本地 Markdown 文件夹。
              </Typography.Paragraph>
              <div className="notes-empty-actions">
                <Button type="primary" icon={<FilePlus2 size={15} />} loading={saving} onClick={() => void handleCreateNote()}>
                  新建笔记
                </Button>
                <Button icon={<Upload size={15} />} loading={importing || syncingKnowledge} onClick={() => void openFolderImport()}>
                  导入文件夹
                </Button>
              </div>
            </div>
          ) : (
            <div className="note-editor-shell">
              <div className="note-editor-header">
                {editingNoteId === activeNote.id && renameInputPlacement === 'title' ? (
                  <Input
                    autoFocus
                    className="note-title-rename-input"
                    value={draftNoteTitle}
                    variant="borderless"
                    onBlur={() => void finishNoteRename(activeNote)}
                    onChange={(event) => setDraftNoteTitle(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.currentTarget.blur();
                      }

                      if (event.key === 'Escape') {
                        cancelNoteRename();
                      }
                    }}
                  />
                ) : (
                  <Typography.Title
                    className="note-title-edit-trigger"
                    level={1}
                    onClick={() => startNoteRename(activeNote, 'title')}
                  >
                    {getNoteDisplayName(activeNote)}
                  </Typography.Title>
                )}
                <Button className="editor-save-button" type="primary" icon={<Save size={15} />} loading={saving} onClick={handleSave}>
                  保存
                </Button>
              </div>
              <div className="markdown-editor-frame">
                <div id="notes-vditor-editor" />
              </div>
            </div>
          )}
        </main>

        <aside className="toc-panel">
          <Typography.Text className="toc-title">大纲</Typography.Text>
          <ul>
            {headings.map((heading, index) => (
              <li className={`toc-item toc-level-${heading.level}`} key={heading.id}>
                <button type="button" onClick={() => handleTocClick(index)}>
                  <span className="toc-marker" aria-hidden="true" />
                  <span className="toc-text">{heading.title}</span>
                </button>
              </li>
            ))}
          </ul>
        </aside>
      </div>
    </section>
  );
}

function getNoteDisplayName(note: Note) {
  const pathParts = splitNotePath(note.title);
  return pathParts[pathParts.length - 1] ?? note.title;
}

function buildRenamedNoteTitle(currentTitle: string, nextDisplayName: string) {
  const pathParts = splitNotePath(currentTitle);

  if (pathParts.length <= 1) {
    return nextDisplayName;
  }

  return [...pathParts.slice(0, -1), nextDisplayName].join('/');
}

function extractMarkdownHeadings(content: string) {
  const headings: { id: string; level: number; title: string }[] = [];
  let fenceMarker: string | undefined;

  content.split('\n').forEach((line, index) => {
    const fenceMatch = line.match(/^ {0,3}(`{3,}|~{3,})/);

    if (fenceMatch) {
      const marker = fenceMatch[1][0];

      if (!fenceMarker) {
        fenceMarker = marker;
        return;
      }

      if (marker === fenceMarker) {
        fenceMarker = undefined;
      }

      return;
    }

    if (fenceMarker) {
      return;
    }

    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);

    if (!headingMatch) {
      return;
    }

    headings.push({
      id: `heading-${index}`,
      level: headingMatch[1].length,
      title: headingMatch[2].trim(),
    });
  });

  return headings;
}

function buildNoteTree(notes: Note[], folders: NoteFolder[]): NoteTreeNode[] {
  const root: MutableFolderTreeNode = {
    type: 'folder',
    key: 'root',
    name: 'root',
    children: [],
    folderMap: new Map(),
  };

  folders.forEach((folder) => {
    ensureFolderPath(root, splitNotePath(folder.path), folder.id);
  });

  notes.forEach((note) => {
    const pathParts = splitNotePath(note.title);
    const fileName = pathParts[pathParts.length - 1] ?? note.title;
    const currentFolder = ensureFolderPath(root, pathParts.slice(0, -1));

    currentFolder.children.push({
      type: 'note',
      key: note.id,
      name: fileName,
      note,
    });
  });

  return sortTreeNodes(stripMutableFolderState(root.children));
}

function ensureFolderPath(root: MutableFolderTreeNode, pathParts: string[], folderId?: string) {
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

function splitNotePath(title: string) {
  return title
    .replace(/\\/g, '/')
    .split('/')
    .map((part) => part.trim())
    .filter(Boolean);
}

function stripMutableFolderState(nodes: NoteTreeNode[]): NoteTreeNode[] {
  return nodes.map((node) => {
    if (node.type === 'note') {
      return node;
    }

    return {
      type: 'folder',
      key: node.key,
      folderId: node.folderId,
      name: node.name,
      children: stripMutableFolderState(node.children),
    };
  });
}

function sortTreeNodes(nodes: NoteTreeNode[]): NoteTreeNode[] {
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
        children: sortTreeNodes(node.children),
      };
    });
}

function collectFolderKeys(nodes: NoteTreeNode[]) {
  const folderKeys: string[] = [];

  nodes.forEach((node) => {
    if (node.type === 'folder') {
      folderKeys.push(node.key, ...collectFolderKeys(node.children));
    }
  });

  return folderKeys;
}

function getFolderAncestorKeys(folderKey: string) {
  const pathParts = folderKey.split('/');

  return pathParts.reduce<string[]>((ancestorKeys, _pathPart, index) => {
    if (index > 0) {
      ancestorKeys.push(pathParts.slice(0, index + 1).join('/'));
    }

    return ancestorKeys;
  }, []);
}

function collectNotesFromTree(nodes: NoteTreeNode[]) {
  const collectedNotes: Note[] = [];

  nodes.forEach((node) => {
    if (node.type === 'note') {
      collectedNotes.push(node.note);
      return;
    }

    collectedNotes.push(...collectNotesFromTree(node.children));
  });

  return collectedNotes;
}

export default NotesPage;
