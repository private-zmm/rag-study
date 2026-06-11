# 前端学习笔记

## 本次目标

先做一个可以运行的 React + Vite 前端项目，并实现第一版默认首页。

首页暂时不接后端，先用静态数据模拟 open-webui 类似的聊天体验。

## 当前技术栈

- React
- TypeScript
- Vite
- Ant Design
- Ant Design X
- lucide-react

项目整体已确定的技术方向：

- 前端使用 React + TypeScript + Vite
- 后端使用 Java + Spring Boot
- 主数据库使用 PostgreSQL
- 向量数据库使用 Qdrant
- 数据不做本地优先，笔记、知识库、对话和配置都保存到服务端相关数据库
- Markdown 笔记需要支持在线编辑

后续可以逐步接入：

- UI 组件库
- Markdown 编辑器
- 接口请求库
- 状态管理
- Tauri 跨端封装

## 关键文件

- `package.json`：项目依赖和启动命令
- `index.html`：浏览器打开的 HTML 入口
- `vite.config.ts`：Vite 配置
- `src/main.tsx`：React 应用入口
- `src/App.tsx`：当前首页组件
- `src/styles.css`：页面样式和响应式布局
- `src/layouts/AppLayout.tsx`：应用整体布局
- `src/pages/ChatPage.tsx`：大模型对话页
- `src/pages/NotesPage.tsx`：在线 Markdown 笔记页
- `src/pages/KnowledgePage.tsx`：知识库页
- `src/pages/ClipperPage.tsx`：网页剪藏页
- `src/pages/SettingsPage.tsx`：系统设置页

## 启动命令

安装依赖：

```bash
npm install
```

本地开发：

```bash
npm run dev
```

生产构建：

```bash
npm run build
```

## 本次学到的概念

React 前端项目最少需要几部分：

- 一个 HTML 挂载点
- 一个 React 入口文件
- 一个主组件
- 一份样式
- 一个构建工具

Vite 负责本地开发服务器和生产构建。

TypeScript 需要 React 的类型声明，所以项目里安装了：

- `@types/react`
- `@types/react-dom`

## 下一步建议

下一步可以做笔记模块的第一版页面：

- 左侧笔记列表
- 中间 Markdown 编辑区
- 右侧目录
- 保存按钮

先把在线 Markdown 编辑体验跑通，再接 Spring Boot 后端和 PostgreSQL 保存。

首版笔记模块需要遵守：

- 笔记在线编辑，不通过本地文件保存
- Markdown 原文保存到 PostgreSQL
- 笔记元数据保存到 PostgreSQL
- 笔记向量后续写入 Qdrant
- 前端只负责编辑体验和调用接口
- 后端负责数据持久化、权限和后续向量化

## 组件拆分原则

当前前端已经按功能区域拆分：

- `src/App.tsx`：只负责页面整体拼装
- `src/layouts/AppLayout.tsx`：应用外壳和页面切换
- `src/components/AppSidebar.tsx`：左侧导航、会话列表和用户区
- `src/components/ConversationGroup.tsx`：会话分组列表
- `src/components/WorkspaceTopbar.tsx`：主区域顶部模型栏和工具按钮
- `src/components/ChatStage.tsx`：默认聊天欢迎区域
- `src/components/PromptBox.tsx`：输入框、发送按钮和附件菜单
- `src/pages`：各个业务页面
- `src/data/mockData.ts`：页面模拟数据
- `src/types.ts`：前端共享类型

拆分组件的判断标准：

- 一个文件超过一屏，通常要考虑拆分
- 一个组件同时处理多个区域，通常要拆分
- 会被复用的结构，应该拆成独立组件
- 数据和视图尽量分开，静态数据先放到 `data` 目录
- 共享类型统一放到 `types.ts`，避免每个组件重复定义

这样做的好处是后续接后端接口、加路由、加状态管理时，每个文件的职责都比较清楚，不容易越写越乱。

## 代码风格约定

本项目的前端代码优先保证易读、易改、易扩展。

### 文件组织

- 页面整体入口保持轻量，`App.tsx` 只做页面拼装
- 可独立理解的界面区域放到 `src/components`
- 临时模拟数据放到 `src/data`
- 多个组件共享的 TypeScript 类型放到 `src/types.ts`
- 样式暂时统一放在 `src/styles.css`，后续页面变多后再按模块拆样式

### 组件写法

- 一个组件只负责一个明确区域
- 组件名称使用 PascalCase，例如 `Sidebar`、`PromptBox`
- 文件名和组件名保持一致
- 组件内部先写主结构，再写辅助渲染逻辑
- 能通过 props 传入的数据，不直接写死在组件内部
- 列表渲染统一使用 `.map`
- 列表项必须有稳定的 `key`
- 按钮统一写 `type="button"`，表单提交按钮才使用 `type="submit"`

### 类型约定

- 共享类型统一放到 `src/types.ts`
- 类型名称使用 PascalCase，例如 `Conversation`、`NavItem`
- 组件 props 可以在组件文件内定义，例如 `ConversationGroupProps`
- 图标类型使用 `LucideIcon`
- 不轻易使用 `any`

### 数据与视图分离

- 组件负责展示
- `data` 目录负责临时数据
- 后续接后端时，数据获取逻辑不要直接塞进大型组件
- 后续可以引入 hooks，例如 `useConversations`、`useModels`

### 样式约定

- className 使用语义化命名，例如 `sidebar`、`prompt-box`、`conversation-item`
- 不用无意义命名，例如 `box1`、`content2`
- 布局优先使用 CSS Grid 和 Flex
- 固定尺寸的 UI 元素要设置稳定宽高，避免 hover 或内容变化导致布局抖动
- 响应式样式放在文件底部的 media query 中
- 颜色、间距、圆角保持统一，不在每个组件里随意发明新风格

### 可维护性原则

- 当一个文件明显变长时，优先考虑拆分
- 当一个组件承担多个职责时，优先考虑拆分
- 当多个地方出现重复结构时，优先抽成组件
- 修改 UI 时尽量保持组件边界清楚，不顺手重构无关代码
- 每次改完都运行 `npm run build` 验证

## Ant Design 接入记录

当前已经引入：

- `antd`：用于布局、按钮、表单、卡片、表格、标签页等通用界面
- `@ant-design/x`：用于 AI 对话相关组件，例如 `Welcome`、`Bubble`、`Sender`、`Conversations`
- `lucide-react`：用于补充图标

当前已经实现的页面：

- 对话页：使用 Ant Design X 的欢迎区、消息气泡和输入框
- 笔记页：左侧笔记列表，中间 Markdown 编辑区，右侧目录
- 知识库页：知识库卡片和最近文档表格
- 网页剪藏页：URL 抓取表单
- 设置页：模型供应商和 S3 备份配置

构建命令调整为：

```bash
npm run build
```

内部执行：

```bash
tsc --noEmit && vite build
```

这样 TypeScript 只做类型检查，不会在项目根目录生成 `vite.config.js`、`vite.config.d.ts` 等编译产物。

当前构建会出现 Vite chunk 体积提示，这是因为 Ant Design 和 Ant Design X 体积较大。MVP 阶段可以接受，后续可以通过路由懒加载和代码分包优化。
