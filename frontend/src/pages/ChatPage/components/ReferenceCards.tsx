import { Button } from 'antd';
import { Database, FileText, MessageCircle, X } from 'lucide-react';
import type { ChatMessage, ChatReference, KnowledgeBase } from '../../../types';
import { getReferenceTypeLabel } from './referenceUtils';

type ReferenceCardsProps = {
  compact?: boolean;
  knowledgeBase?: KnowledgeBase | ChatMessage['knowledgeBase'];
  references?: ChatReference[];
  onRemoveKnowledgeBase?: () => void;
  onRemoveReference?: (reference: ChatReference) => void;
};

function ReferenceCards({
  compact = false,
  knowledgeBase,
  references,
  onRemoveKnowledgeBase,
  onRemoveReference,
}: ReferenceCardsProps) {
  if (!knowledgeBase && (!references || references.length === 0)) {
    return null;
  }

  return (
    <div className={compact ? 'chat-reference-stack compact' : 'chat-reference-stack'}>
      {knowledgeBase ? (
        <div className={compact ? 'chat-reference-card compact' : 'chat-reference-card'}>
          <Database size={compact ? 16 : 17} strokeWidth={1.8} />
          <span className="chat-reference-title">{knowledgeBase.name}</span>
          <span className="chat-reference-type">知识库</span>
          {onRemoveKnowledgeBase ? (
            <Button
              aria-label="取消引用知识库"
              className="chat-reference-remove"
              type="text"
              shape="circle"
              icon={<X size={14} />}
              onClick={onRemoveKnowledgeBase}
            />
          ) : (
            <span />
          )}
        </div>
      ) : null}
      {references?.map((reference) => {
        const Icon = getReferenceIcon(reference.type);

        return (
          <div className={compact ? 'chat-reference-card compact' : 'chat-reference-card'} key={`${reference.type}-${reference.id}`}>
            <Icon size={compact ? 16 : 17} strokeWidth={1.8} />
            <span className="chat-reference-title">{reference.title}</span>
            <span className="chat-reference-type">{getReferenceTypeLabel(reference.type)}</span>
            {onRemoveReference ? (
              <Button
                aria-label={`取消引用${reference.title}`}
                className="chat-reference-remove"
                type="text"
                shape="circle"
                icon={<X size={14} />}
                onClick={() => onRemoveReference(reference)}
              />
            ) : (
              <span />
            )}
          </div>
        );
      })}
    </div>
  );
}

function getReferenceIcon(type: ChatReference['type']) {
  if (type === 'file' || type === 'note') {
    return FileText;
  }

  return MessageCircle;
}

export default ReferenceCards;
