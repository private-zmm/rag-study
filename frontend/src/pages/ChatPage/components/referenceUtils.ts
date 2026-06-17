import type { ChatReference, Note, NoteFolder } from '../../../types';

export const MAX_REFERENCE_CHARS = 8000;
export const MAX_FILE_CHARS = 20000;

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

export type ReferenceMenuView = 'main' | 'knowledge' | 'notes' | 'conversations';

export type ReferenceNoteTreeNode = ReferenceNoteFolderTreeNode | ReferenceNoteFileTreeNode;

export type ReferenceNoteFolderTreeNode = {
  type: 'folder';
  key: string;
  folderId?: string;
  name: string;
  children: ReferenceNoteTreeNode[];
};

export type ReferenceNoteFileTreeNode = {
  type: 'note';
  key: string;
  name: string;
  note: Note;
};

type MutableReferenceNoteFolderTreeNode = ReferenceNoteFolderTreeNode & {
  folderMap: Map<string, MutableReferenceNoteFolderTreeNode>;
};

export function buildMessageWithReferences(content: string, references: ChatReference[]) {
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

export function truncateReferenceContent(content: string, limit = MAX_REFERENCE_CHARS) {
  if (content.length <= limit) {
    return content;
  }

  return `${content.slice(0, limit)}\n\n[内容已截断]`;
}

export function isReadableTextFile(file: File) {
  if (file.type.startsWith('text/') || file.type === 'application/json' || file.type.includes('xml')) {
    return true;
  }

  const extension = file.name.split('.').pop()?.toLowerCase();
  return extension ? TEXT_FILE_EXTENSIONS.has(extension) : false;
}

export function getReferenceTypeLabel(type: ChatReference['type']) {
  if (type === 'file') {
    return '文件';
  }

  if (type === 'note') {
    return '笔记';
  }

  return '对话';
}

export function getReferenceTypeByLabel(label: string): ChatReference['type'] | undefined {
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

export function getReferenceNoteDisplayName(note: Note) {
  const pathParts = splitReferenceNotePath(note.title);
  return pathParts[pathParts.length - 1] || note.title || '未命名笔记';
}

export function buildReferenceNoteTree(notes: Note[], folders: NoteFolder[]): ReferenceNoteTreeNode[] {
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

export function collectReferenceNoteFolderKeys(nodes: ReferenceNoteTreeNode[]) {
  const folderKeys: string[] = [];

  nodes.forEach((node) => {
    if (node.type === 'folder') {
      folderKeys.push(node.key, ...collectReferenceNoteFolderKeys(node.children));
    }
  });

  return folderKeys;
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
