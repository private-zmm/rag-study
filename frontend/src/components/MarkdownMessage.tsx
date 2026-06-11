import ReactMarkdown from 'react-markdown';
import rehypeHighlight from 'rehype-highlight';
import remarkGfm from 'remark-gfm';
import { normalizeMarkdownContent } from '../utils/markdown';

type MarkdownMessageProps = {
  content: string;
};

function MarkdownMessage({ content }: MarkdownMessageProps) {
  return (
    <div className="markdown-message">
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
        {normalizeMarkdownContent(content)}
      </ReactMarkdown>
    </div>
  );
}

export default MarkdownMessage;
