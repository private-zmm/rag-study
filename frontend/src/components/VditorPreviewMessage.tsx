import { useEffect, useRef } from 'react';
import 'vditor/dist/index.css';
import { normalizeMarkdownContent } from '../utils/markdown';

type VditorPreviewMessageProps = {
  content: string;
};

let vditorModulePromise: Promise<typeof import('vditor')> | undefined;

function VditorPreviewMessage({ content }: VditorPreviewMessageProps) {
  const previewRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previewElement = previewRef.current;

    if (!previewElement) {
      return;
    }

    if (!content.trim()) {
      previewElement.innerHTML = '';
      return;
    }

    let disposed = false;
    const renderTimer = window.setTimeout(() => {
      loadVditorPreview()
        .then(({ default: Vditor }) => {
          if (disposed || !previewRef.current) {
            return;
          }

          const preview = Vditor as unknown as {
            preview: (element: HTMLElement, markdown: string, options?: Record<string, unknown>) => void;
          };

          preview.preview(previewRef.current, normalizeMarkdownContent(content), {
            anchor: 0,
            hljs: {
              enable: true,
              lineNumber: false,
              style: 'github',
            },
            mode: 'light',
          });
        })
        .catch(() => {
          if (previewRef.current) {
            previewRef.current.textContent = content;
          }
        });
    }, 60);

    return () => {
      disposed = true;
      window.clearTimeout(renderTimer);
    };
  }, [content]);

  return (
    <div className="vditor-preview-message">
      <div ref={previewRef} className="vditor-reset vditor-preview-message-body" />
    </div>
  );
}

function loadVditorPreview() {
  vditorModulePromise ??= import('vditor');
  return vditorModulePromise;
}

export default VditorPreviewMessage;
