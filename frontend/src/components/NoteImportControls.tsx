import { Modal, message } from 'antd';
import { FolderPlus } from 'lucide-react';
import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { bindNoteAssets, importNotes, uploadNoteAsset } from '../api/client';
import type { Note } from '../types';

const importableNoteExtensions = new Set(['.md', '.markdown', '.mdx', '.txt']);
const importableAssetExtensions = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg', '.bmp']);
const importBatchSize = 50;
const assetUploadTimeoutMs = 20000;

type FileSystemFileHandle = {
  kind: 'file';
  name: string;
  getFile: () => Promise<File>;
};

type FileSystemDirectoryHandle = {
  kind: 'directory';
  name: string;
  values: () => AsyncIterable<FileSystemFileHandle | FileSystemDirectoryHandle>;
};

type WindowWithDirectoryPicker = Window & {
  showDirectoryPicker?: () => Promise<FileSystemDirectoryHandle>;
};

type UploadedNoteAsset = {
  objectName: string;
  url: string;
};

export type NoteImportControlsHandle = {
  openFileImport: () => void;
  openFolderImport: () => Promise<void>;
};

type NoteImportControlsProps = {
  onImported: (importedNotes: Note[]) => void;
  onImportingChange: (importing: boolean) => void;
};

export const NoteImportControls = forwardRef<NoteImportControlsHandle, NoteImportControlsProps>(
  ({ onImported, onImportingChange }, ref) => {
    const fileImportInputRef = useRef<HTMLInputElement | null>(null);
    const folderImportInputRef = useRef<HTMLInputElement | null>(null);

    useEffect(() => {
      folderImportInputRef.current?.setAttribute('webkitdirectory', '');
      folderImportInputRef.current?.setAttribute('directory', '');
    }, []);

    const handleImportFiles = async (fileSource: FileList | File[] | null, mode: 'file' | 'folder') => {
      const files = Array.from(fileSource ?? []);

      if (files.length === 0) {
        return;
      }

      const readableFiles = files.filter((file) => importableNoteExtensions.has(getFileExtension(file.name)));
      const assetFiles = mode === 'folder' ? files.filter((file) => importableAssetExtensions.has(getFileExtension(file.name))) : [];

      if (readableFiles.length === 0) {
        message.warning('没有找到可导入的 Markdown 或文本文件');
        return;
      }

      onImportingChange(true);

      try {
        const referencedAssetPaths = await collectReferencedAssetPaths(readableFiles);
        const referencedAssetFiles = assetFiles.filter((file) =>
          referencedAssetPaths.has(normalizeImportPath(file.webkitRelativePath || file.name)),
        );
        const assetUploadResult = await uploadImportAssets(referencedAssetFiles);
        const assetMap = assetUploadResult.assetMap;
        const importedNotes: Note[] = [];

        for (let startIndex = 0; startIndex < readableFiles.length; startIndex += importBatchSize) {
          const batchFiles = readableFiles.slice(startIndex, startIndex + importBatchSize);
          const notePayloads = await Promise.all(
            batchFiles.map(async (file) => {
              const rawContent = sanitizeImportedText(await file.text());

              return {
                title: buildImportedNoteTitle(file, mode),
                content: rewriteMarkdownAssetUrls(rawContent, file, assetMap),
              };
            }),
          );
          const importedBatch = await importNotes(notePayloads);

          importedNotes.push(...importedBatch);
          await bindImportedNoteAssets(importedBatch, batchFiles, assetMap);
          message.loading({
            content: `正在导入 ${Math.min(startIndex + batchFiles.length, readableFiles.length)} / ${readableFiles.length}`,
            duration: 0.8,
            key: 'notes-import-progress',
          });
        }

        onImported(importedNotes);
        message.success({ content: `已导入 ${importedNotes.length} 篇笔记`, key: 'notes-import-progress' });

        if (files.length !== readableFiles.length) {
          message.info(`已处理 ${assetMap.size} 个图片资源，跳过 ${assetUploadResult.failedCount} 个上传失败图片`);
        }
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : '';
        message.error(errorMessage ? `导入失败：${errorMessage}` : '导入失败，先检查后端是否启动');
      } finally {
        onImportingChange(false);
      }
    };

    const openFileImport = () => {
      fileImportInputRef.current?.click();
    };

    const openFolderImport = async () => {
      const directoryPicker = (window as WindowWithDirectoryPicker).showDirectoryPicker;

      if (!directoryPicker) {
        folderImportInputRef.current?.click();
        return;
      }

      try {
        const directoryHandle = await directoryPicker();
        const files = await readDirectoryFiles(directoryHandle);
        const noteCount = files.filter((file) => importableNoteExtensions.has(getFileExtension(file.name))).length;
        const imageCount = files.filter((file) => importableAssetExtensions.has(getFileExtension(file.name))).length;

        if (noteCount === 0) {
          message.warning('没有找到可导入的 Markdown 或文本文件');
          return;
        }

        Modal.confirm({
          title: `导入 ${directoryHandle.name}`,
          content: `将导入 ${noteCount} 篇笔记，并处理 ${imageCount} 个图片资源。`,
          okText: '开始导入',
          cancelText: '取消',
          icon: <FolderPlus size={20} />,
          centered: true,
          onOk: async () => {
            await handleImportFiles(files, 'folder');
          },
        });
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }

        message.error('读取文件夹失败，将使用浏览器默认方式');
        folderImportInputRef.current?.click();
      }
    };

    useImperativeHandle(ref, () => ({
      openFileImport,
      openFolderImport,
    }));

    return (
      <>
        <input
          ref={fileImportInputRef}
          className="hidden-file-input"
          type="file"
          accept=".md,.markdown,.mdx,.txt,text/markdown,text/plain"
          multiple
          onChange={(event) => {
            void handleImportFiles(event.currentTarget.files, 'file');
            event.currentTarget.value = '';
          }}
        />
        <input
          ref={folderImportInputRef}
          className="hidden-file-input"
          type="file"
          accept=".md,.markdown,.mdx,.txt,text/markdown,text/plain"
          multiple
          onChange={(event) => {
            void handleImportFiles(event.currentTarget.files, 'folder');
            event.currentTarget.value = '';
          }}
        />
      </>
    );
  },
);

NoteImportControls.displayName = 'NoteImportControls';

function getFileExtension(fileName: string) {
  const dotIndex = fileName.lastIndexOf('.');
  return dotIndex >= 0 ? fileName.slice(dotIndex).toLowerCase() : '';
}

function buildImportedNoteTitle(file: File, mode: 'file' | 'folder') {
  const rawPath = mode === 'folder' && file.webkitRelativePath ? file.webkitRelativePath : file.name;
  const normalizedPath = rawPath.replace(/\\/g, '/');
  const dotIndex = normalizedPath.lastIndexOf('.');

  return dotIndex > 0 ? normalizedPath.slice(0, dotIndex) : normalizedPath;
}

function sanitizeImportedText(value: string) {
  return value.replace(/\u0000/g, '');
}

async function readDirectoryFiles(directoryHandle: FileSystemDirectoryHandle) {
  const files: File[] = [];

  await collectDirectoryFiles(directoryHandle, directoryHandle.name, files);

  return files;
}

async function collectDirectoryFiles(directoryHandle: FileSystemDirectoryHandle, currentPath: string, files: File[]) {
  for await (const handle of directoryHandle.values()) {
    const nextPath = `${currentPath}/${handle.name}`;

    if (handle.kind === 'directory') {
      await collectDirectoryFiles(handle, nextPath, files);
      continue;
    }

    const file = await handle.getFile();
    defineRelativePath(file, nextPath);
    files.push(file);
  }
}

function defineRelativePath(file: File, relativePath: string) {
  Object.defineProperty(file, 'webkitRelativePath', {
    configurable: true,
    value: normalizeImportPath(relativePath),
  });
}

async function collectReferencedAssetPaths(markdownFiles: File[]) {
  const assetPaths = new Set<string>();

  for (const markdownFile of markdownFiles) {
    const content = sanitizeImportedText(await markdownFile.text());

    extractMarkdownImageUrls(content).forEach((assetUrl) => {
      if (!isExternalUrl(assetUrl)) {
        assetPaths.add(resolveAssetPath(markdownFile, assetUrl));
      }
    });
  }

  return assetPaths;
}

async function uploadImportAssets(assetFiles: File[]) {
  const assetMap = new Map<string, UploadedNoteAsset>();
  let failedCount = 0;

  for (let index = 0; index < assetFiles.length; index += 1) {
    const assetFile = assetFiles[index];
    const relativePath = normalizeImportPath(assetFile.webkitRelativePath || assetFile.name);

    message.loading({
      content: `正在上传图片 ${index + 1} / ${assetFiles.length}`,
      duration: 1,
      key: 'notes-import-progress',
    });

    try {
      const uploadedAsset = await uploadNoteAssetWithTimeout(assetFile);
      assetMap.set(relativePath, {
        objectName: uploadedAsset.objectName,
        url: uploadedAsset.url,
      });
    } catch {
      failedCount += 1;
    }
  }

  return {
    assetMap,
    failedCount,
  };
}

async function uploadNoteAssetWithTimeout(file: File) {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), assetUploadTimeoutMs);

  try {
    return await uploadNoteAsset(file, controller.signal);
  } finally {
    window.clearTimeout(timeoutId);
  }
}

async function bindImportedNoteAssets(importedNotes: Note[], sourceFiles: File[], assetMap: Map<string, UploadedNoteAsset>) {
  await Promise.all(
    importedNotes.map(async (note, index) => {
      const sourceFile = sourceFiles[index];

      if (!sourceFile) {
        return;
      }

      const rawContent = sanitizeImportedText(await sourceFile.text());
      const objectNames = collectNoteAssetObjectNames(rawContent, sourceFile, assetMap);

      if (objectNames.length === 0) {
        return;
      }

      await bindNoteAssets(note.id, objectNames);
    }),
  );
}

function collectNoteAssetObjectNames(content: string, markdownFile: File, assetMap: Map<string, UploadedNoteAsset>) {
  return extractMarkdownImageUrls(content)
    .map((assetUrl) => {
      if (isExternalUrl(assetUrl)) {
        return undefined;
      }

      return assetMap.get(resolveAssetPath(markdownFile, assetUrl))?.objectName;
    })
    .filter((objectName): objectName is string => Boolean(objectName));
}

function rewriteMarkdownAssetUrls(content: string, markdownFile: File, assetMap: Map<string, UploadedNoteAsset>) {
  if (assetMap.size === 0) {
    return content;
  }

  return content.replace(/(!\[[^\]]*]\()([^)\s]+(?:\s+"[^"]*")?)(\))/g, (matchedValue, prefix: string, rawTarget: string, suffix: string) => {
    const parsedTarget = parseMarkdownImageTarget(rawTarget);

    if (!parsedTarget || isExternalUrl(parsedTarget.url)) {
      return matchedValue;
    }

    const resolvedPath = resolveAssetPath(markdownFile, parsedTarget.url);
    const uploadedAsset = assetMap.get(resolvedPath);

    if (!uploadedAsset) {
      return matchedValue;
    }

    return `${prefix}${uploadedAsset.url}${parsedTarget.title}${suffix}`;
  });
}

function extractMarkdownImageUrls(content: string) {
  const imageUrls: string[] = [];
  const imagePattern = /!\[[^\]]*]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;
  let match = imagePattern.exec(content);

  while (match) {
    imageUrls.push(match[1]);
    match = imagePattern.exec(content);
  }

  return imageUrls;
}

function parseMarkdownImageTarget(rawTarget: string) {
  const trimmedTarget = rawTarget.trim();
  const titleMatch = trimmedTarget.match(/^(\S+)(\s+"[^"]*")$/);

  if (titleMatch) {
    return {
      url: titleMatch[1],
      title: titleMatch[2],
    };
  }

  return {
    url: trimmedTarget,
    title: '',
  };
}

function resolveAssetPath(markdownFile: File, assetUrl: string) {
  const markdownPath = normalizeImportPath(markdownFile.webkitRelativePath || markdownFile.name);
  const markdownDirectory = markdownPath.includes('/') ? markdownPath.slice(0, markdownPath.lastIndexOf('/') + 1) : '';
  const decodedAssetUrl = decodeURIComponent(assetUrl);
  const baseUrl = `https://rag-study.local/${markdownDirectory}`;
  const resolvedUrl = new URL(decodedAssetUrl, baseUrl);

  return normalizeImportPath(resolvedUrl.pathname.replace(/^\//, ''));
}

function normalizeImportPath(path: string) {
  return path.replace(/\\/g, '/').replace(/^\/+/, '');
}

function isExternalUrl(url: string) {
  return /^(?:[a-z][a-z\d+.-]*:|\/\/|#)/i.test(url);
}
