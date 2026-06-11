# 前端规划

## 1. 前端目标

前端负责提供跨端用户界面，让用户可以进行大模型对话、在线编辑 Markdown 笔记、管理知识库、抓取网页内容和配置系统。

前端不负责本地持久化核心数据。笔记、知识库、对话、系统配置等核心数据都通过后端 API 保存到相关数据库中。

## 2. 技术方向

当前前端技术栈：

- React
- TypeScript
- Vite
- Ant Design
- Ant Design X
- lucide-react

UI 组件库选择：

- Ant Design 用于基础产品界面：布局、按钮、表单、弹窗、表格、选择器、上传、设置页等
- Ant Design X 用于 AI 对话界面：聊天消息、输入框、模型对话相关组件等
- lucide-react 用于补充图标

不优先选择 shadcn/ui 的原因：

- 当前项目以边学习边开发为主，Ant Design 上手更快
- 知识库、设置页、备份配置等后台型界面更适合 Ant Design
- Ant Design X 和 AI 对话场景更贴合
- 暂时避免引入 Tailwind 和大量自定义组件维护成本

后续跨端方向：

- Web 版优先
- 后续通过 Tauri 封装桌面端和移动端
- 目标兼容 Windows、macOS、iOS、Android、Web

## 3. 首页设计

打开应用后的默认主页是与大模型对话。

页面风格参考 open-webui：

- 左侧为会话列表或导航栏
- 中间为主要聊天区域
- 支持新建对话
- 支持选择模型
- 支持选择知识库
- 支持在对话中引用知识库内容
- 支持展示引用来源
- 支持历史会话管理

首页不是传统仪表盘，而是直接进入「和大模型对话」的工作流，让用户打开应用后可以马上提问、检索自己的知识库、继续已有会话。

## 4. 笔记前端能力

笔记前端需要支持在线 Markdown 编辑，体验参考语雀。

首版能力：

- 在线编辑 Markdown
- Markdown 内容保存
- Markdown 预览
- 常用 Markdown 语法输入
- 文档目录
- 正文编辑

后续可以扩展：

- 协同编辑
- 评论
- 版本历史
- 更丰富的块编辑体验

## 5. 知识库前端能力

知识库前端需要支持：

- 查看知识库列表
- 查看知识库文档
- 上传或添加资料
- 应用内 URL 抓取网页
- 将网页内容保存到笔记或知识库
- 在对话中选择知识库
- 展示大模型回答引用来源

## 6. 数据原则

前端只负责展示、编辑和调用接口。

前端不把笔记、知识库、对话、系统配置等核心数据保存到本地文件。

首版数据流：

- 用户在前端在线编辑 Markdown
- 前端调用 Spring Boot 后端 API 保存内容
- Markdown 原文保存到 PostgreSQL
- 笔记元数据保存到 PostgreSQL
- 后端后续触发向量化任务
- 向量数据写入 Qdrant

## 7. 当前组件结构

当前首页已经按功能区域拆分：

- `src/App.tsx`：页面整体拼装
- `src/components/Sidebar.tsx`：左侧导航、模型列表、会话列表和用户区
- `src/components/ConversationGroup.tsx`：会话分组列表
- `src/components/WorkspaceTopbar.tsx`：主区域顶部模型栏和工具按钮
- `src/components/ChatStage.tsx`：默认聊天欢迎区域
- `src/components/PromptBox.tsx`：输入框、发送按钮和附件菜单
- `src/data/mockData.ts`：页面模拟数据
- `src/types.ts`：前端共享类型

## 8. 前端代码风格

- 页面入口保持轻量
- 组件按职责拆分
- 文件名和组件名保持一致
- 共享类型放到 `src/types.ts`
- 临时数据放到 `src/data`
- 组件负责展示，数据获取后续通过 hooks 或接口层处理
- 通用控件优先使用 Ant Design
- AI 对话控件优先使用 Ant Design X
- 不重复手写 Ant Design 已经提供的基础组件
- 每次修改后运行 `npm run build` 验证

## 9. 前端后续任务

- 引入 Ant Design 和 Ant Design X
- 逐步迁移当前自写 UI 到组件库
- 完善 open-webui 风格首页
- 增加路由
- 做笔记列表页
- 做在线 Markdown 编辑页
- 做知识库管理页
- 做网页 URL 抓取入口
- 接入 Spring Boot 后端 API
- 接入模型选择和知识库选择
