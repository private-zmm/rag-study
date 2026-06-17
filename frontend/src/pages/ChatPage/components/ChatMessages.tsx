import type { ChatMessage, ChatReference } from '../../../types';
import MarkdownMessage from '../../../components/MarkdownMessage';
import VditorPreviewMessage from '../../../components/VditorPreviewMessage';
import ReferenceCards from './ReferenceCards';
import { getReferenceTypeByLabel } from './referenceUtils';

type UserChatMessageProps = {
  message: ChatMessage;
};

export function UserChatMessage({ message }: UserChatMessageProps) {
  const references = message.references?.length ? message.references : extractReferencesFromMessageContent(message.content);

  return (
    <div className="user-chat-message">
      <ReferenceCards knowledgeBase={message.knowledgeBase} references={references} />
      <MarkdownMessage content={getUserMessageDisplayContent(message.content)} />
    </div>
  );
}

type AssistantChatMessageProps = {
  content: string;
  streamStatus?: string;
  suggestedQuestions?: string[];
  onSuggestionClick?: (suggestion: string) => void;
};

export function AssistantChatMessage({
  content,
  streamStatus,
  suggestedQuestions,
  onSuggestionClick,
}: AssistantChatMessageProps) {
  return (
    <div className="assistant-chat-message">
      {content ? <VditorPreviewMessage content={content} /> : <WaitingMessage status={streamStatus} />}
      {content && streamStatus ? <div className="assistant-stream-status">{streamStatus}</div> : null}
      {suggestedQuestions?.length ? (
        <div className="follow-up-suggestions" aria-label="追问建议">
          <div className="follow-up-title">追问</div>
          {suggestedQuestions.map((suggestion) => (
            <button
              className="follow-up-suggestion"
              key={suggestion}
              type="button"
              onClick={() => onSuggestionClick?.(suggestion)}
            >
              {suggestion}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function WaitingMessage({ status }: { status?: string }) {
  return (
    <div className="assistant-waiting-message">
      <span className="assistant-waiting-dots" aria-hidden="true">
        <span />
        <span />
        <span />
      </span>
      <span>{status || '正在等待模型响应...'}</span>
    </div>
  );
}

function getUserMessageDisplayContent(content: string) {
  return content.split(/\n-{3,}\n以下是本次对话引用的上下文，请优先结合这些内容回答：/)[0].trim();
}

function extractReferencesFromMessageContent(content: string): ChatReference[] {
  const references: ChatReference[] = [];
  const referencePattern = /^\[引用\s+\d+：([^｜\]]+)｜([^\]]+)\]/gm;
  let match: RegExpExecArray | null;

  while ((match = referencePattern.exec(content)) !== null) {
    const type = getReferenceTypeByLabel(match[1].trim());

    if (!type) {
      continue;
    }

    references.push({
      id: `${type}-${references.length}-${match[2].trim()}`,
      type,
      title: match[2].trim(),
    });
  }

  return references;
}
