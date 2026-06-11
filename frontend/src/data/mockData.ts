import {
  Blocks,
  Camera,
  Database,
  FilePlus2,
  FileText,
  Globe,
  MessageCircle,
  Paperclip,
  Search,
  SquarePen,
} from 'lucide-react';
import type {
  AttachmentAction,
  ChatMessage,
  Conversation,
  KnowledgeBase,
  ModelItem,
  NavItem,
  Note,
} from '../types';

export const navItems: NavItem[] = [
  { key: 'chat', icon: SquarePen, label: '新建对话' },
  { key: 'search', icon: Search, label: '搜索' },
  { key: 'notes', icon: FileText, label: '笔记' },
  { key: 'knowledge', icon: Blocks, label: '知识库' },
  { key: 'clipper', icon: Globe, label: '网页剪藏' },
];

export const models: ModelItem[] = [{ name: '5.2-chat' }, { name: '5.5-thinking' }];

export const recentConversations: Conversation[] = [
  { id: 1, title: 'openwebui 有桌面端吗？', time: '2天前' },
  { id: 2, title: 'Friendly Chat Greeting', time: '2天前', marker: '👋' },
  { id: 3, title: '开源语雀替代推荐', time: '3天前', marker: '📚' },
  { id: 4, title: 'Research Topic Plan', time: '6天前', marker: '🎯' },
  { id: 5, title: 'New Chat', time: '6天前' },
];

export const olderConversations: Conversation[] = [
  { id: 6, title: '英语词汇学习计划', time: '1周前', marker: '📘' },
  { id: 7, title: 'Intel i5-105 CPU', time: '1周前', marker: '💻' },
  { id: 8, title: '饮食营养点评', time: '1周前', marker: '🥗' },
  { id: 9, title: 'Research Request Prompt', time: '1周前', marker: '👋' },
  { id: 10, title: '费率换算与成本', time: '1周前', marker: '💱' },
  { id: 11, title: '海外服务器合规风险', time: '2周前', marker: '🌐' },
  { id: 12, title: 'Initial Chat Greeting', time: '2周前', marker: '👋' },
  { id: 13, title: '粉发少女写真', time: '2周前', marker: '🌺' },
  { id: 14, title: 'SPA 88号好评', time: '2周前', marker: '🧘' },
];

export const attachmentActions: AttachmentAction[] = [
  { label: '上传文件', icon: Paperclip },
  { label: '截图', icon: Camera },
  { label: '引用网页', icon: Globe, hasSubmenu: true },
  { label: '添加文件', icon: FilePlus2, hasSubmenu: true },
  { label: '引用笔记', icon: FileText, hasSubmenu: true },
  { label: '引用知识库', icon: Database, hasSubmenu: true },
  { label: '引用其他对话', icon: MessageCircle, hasSubmenu: true },
];

export const chatMessages: ChatMessage[] = [
  {
    id: 'm1',
    role: 'ai',
    content: '你好，我是你的知识库助手。你可以直接提问，也可以引用笔记、知识库或网页内容。',
  },
  {
    id: 'm2',
    role: 'user',
    content: '帮我总结一下这个项目当前的技术方向。',
  },
  {
    id: 'm3',
    role: 'ai',
    content:
      '当前方向是 React + TypeScript + Vite 做前端，Ant Design 和 Ant Design X 做界面，Java + Spring Boot 做后端，PostgreSQL 存业务数据，Qdrant 做向量检索。',
  },
];

export const notes: Note[] = [
  {
    id: 'note-1',
    title: '项目技术栈',
    updatedAt: '今天 13:20',
    tags: ['架构', '前端', '后端'],
    content:
      '# 项目技术栈\n\n## 前端\n\n- React\n- TypeScript\n- Vite\n- Ant Design\n- Ant Design X\n\n## 后端\n\n- Java\n- Spring Boot\n- PostgreSQL\n- Qdrant\n',
  },
  {
    id: 'note-2',
    title: 'Markdown 在线编辑能力',
    updatedAt: '昨天 22:10',
    tags: ['笔记', 'Markdown'],
    content:
      '# Markdown 在线编辑能力\n\n## 首版目标\n\n- 正文编辑\n- 实时预览\n- 文档目录\n- 保存到 PostgreSQL\n\n## 后续能力\n\n- 评论\n- 协同编辑\n- 版本历史\n',
  },
  {
    id: 'note-3',
    title: '网页剪藏流程',
    updatedAt: '周二 18:45',
    tags: ['知识库', '网页'],
    content:
      '# 网页剪藏流程\n\n1. 用户输入 URL\n2. 后端抓取网页\n3. 解析正文\n4. 保存到笔记或知识库\n5. 分块并向量化\n',
  },
];

export const knowledgeBases: KnowledgeBase[] = [
  {
    id: 'kb-1',
    name: '默认知识库',
    description: '自动收集笔记、网页剪藏和上传资料。',
    documentCount: 18,
    chunkCount: 426,
    vectorStatus: 'ready',
  },
  {
    id: 'kb-2',
    name: '项目笔记',
    description: '当前项目规划、学习笔记和技术选型。',
    documentCount: 7,
    chunkCount: 132,
    vectorStatus: 'indexing',
  },
  {
    id: 'kb-3',
    name: '网页剪藏',
    description: '通过应用内 URL 抓取保存的网页资料。',
    documentCount: 0,
    chunkCount: 0,
    vectorStatus: 'empty',
  },
];
