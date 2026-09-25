# AGENTS.md — 密评智能助手（crypto-assess-agent）

面向商用密码应用安全性评估（密评）的 RAG + Agent 辅助系统：条款级检索问答、被测系统差距分析、量化评分和报告草稿。定位是“辅助自查”，不出具正式测评结论。

开发由仓库所有者手动驱动：每次只做 `docs/实施计划.md` 中指定的一个任务（`### Txx` 小节），设计以 `docs/开发计划.md` 为准。读任务卡时只读对应小节，不必读完整个文件。

## 工作方式（每个任务都照做）

1. 先读本文件、`docs/PROGRESS.md` 和任务卡，给出计划：要改的文件、关键类与方法签名、测试清单、需要我确认的问题。我回复“开始”后再改代码。
2. 先写测试并运行一次，确认它失败，再写实现。
3. 完成后运行任务卡里的验收命令，贴出关键输出。失败就如实报告，不要为了通过去改测试、数据集或阈值。
4. 更新 `docs/PROGRESS.md`（任务状态、验收结果、遗留问题）；有技术取舍时追加到 `docs/decisions.md`。
5. 最后用中文写“讲解”（10 行以内）：做了什么、为什么这样设计、有哪些替代方案、面试可能被问什么。
6. 不执行 `git commit`、`git push` 或改写历史；由我审查 diff 后自己提交。
7. 以下情况先问我：超出任务卡范围的改动、新增依赖、改变公共接口、任务卡没写清的设计决定。
8. 不确定的 API（尤其 Spring AI 2.0、Spring Boot 4、Elasticsearch 9 客户端），先查官方文档或 `~/.m2` 里的 sources jar，不要凭记忆写旧版本 API。

## 技术栈（版本以根目录 `pom.xml` 为准）

- Java 21，Maven Wrapper（`./mvnw`），项目根目录就是 Maven 工程
- Spring Boot 4.1.x；Spring AI 2.0.x（由 `spring-ai-bom` 管理）
- MyBatis（`mybatis-spring-boot-starter` 4.1.x）+ MySQL 8.4 + Flyway
- Redis 7（Spring Data Redis）
- Elasticsearch 9.x + IK 分词插件；服务器、插件、Java 客户端版本号必须一致
- Embedding：Ollama `bge-m3`（1024 维）；重排：TEI + `BAAI/bge-reranker-v2-m3`
- 大模型：OpenAI 兼容接口（`spring-ai-starter-model-openai`，`spring.ai.openai.base-url` 可配置）
- 二期：Kafka（spring-kafka）、MCP Server（`spring-ai-starter-mcp-server-webmvc`）、Apache POI、Micrometer Tracing → OTLP → Langfuse
- 前端：Vue 3 + TypeScript + Vite + Element Plus
- 测试：JUnit 5、AssertJ、Mockito、Testcontainers、Spring Boot Test、Vitest

已知的坑：

- Spring Boot 4 默认使用 Jackson 3，包名是 `tools.jackson.*`（注解仍是 `com.fasterxml.jackson.annotation`）。不要混用 Jackson 2 的 `ObjectMapper`。
- Boot 4 的 starter 名称与 Boot 3 教程不同，以 start.spring.io 生成的结果和官方文档为准。
- 不引入第二套 AI 框架（LangChain4j、Spring AI Alibaba 等），不用 Lombok（DTO 用 record），不引入未确认兼容 Boot 4 的第三方 starter。

## 目录结构

```text
pom.xml  mvnw
src/main/java/com/cryptoassess/
  common/       配置、错误、审计、限流、模型调用记录
  knowledge/    规范化文本解析、条款切分、入库、索引
  retrieval/    BM25、向量、RRF 融合、重排
  qa/           问答、引用解析与校验、SSE
  assessment/   评估项目、测评对象、工作流状态机、差距项
  rules/        算法合规规则、量化评分（纯函数）
  report/       Word 报告
  mcp/          MCP 工具与安全配置（二期）
  eval/         评测运行器（profile=eval）
src/main/resources/
  db/migration/ Flyway 脚本（只新增，不修改已执行的脚本）
  prompts/      提示词模板，文件名带版本号，如 qa-answer.v1.st
  rules/        规则表 YAML（带版本号）
  es/           ES 索引映射 JSON
frontend/       Vue 3 前端
deploy/         compose.yaml（profiles：core / mq / obs / app）、elasticsearch/Dockerfile
eval/datasets/  自写评测集（可提交）；eval/results/<UTC时间戳>-<suite>/ 评测输出
data/raw/  data/private/  data/normalized/   标准原文、题库及派生数据（git 忽略）
data/fixtures/  自己编写的“仿标准”样例，测试只用这里的数据
docs/           开发计划、实施计划、PROGRESS、decisions
```

## 常用命令

```bash
cp -n .env.example .env                                         # 本地配置，不提交
docker compose --env-file .env -f deploy/compose.yaml --profile core up -d   # MySQL、Redis、ES、Ollama、TEI
docker compose --env-file .env -f deploy/compose.yaml --profile mq up -d     # Kafka（二期）
./mvnw -q verify                                                # 单元测试 + 集成测试（Testcontainers）
./mvnw -q test -Dtest='RrfFusionTest'                           # 运行单个测试类
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
./mvnw spring-boot:run -Dspring-boot.run.profiles=eval -Dspring-boot.run.arguments="--eval.suite=retrieval"
npm --prefix frontend ci && npm --prefix frontend run dev
npm --prefix frontend run build && npm --prefix frontend run test
```

## 编码规范

- 按功能分包。Controller 只做参数校验和转换，业务逻辑放 Service；ES、TEI、Ollama、大模型等外部依赖放在 `*Client` 接口后面，方便替换成测试替身。
- 配置用 `@ConfigurationProperties`，都能用环境变量覆盖；代码里不写地址和密钥。新增配置项同步更新 `.env.example`。
- 错误统一返回 `ProblemDetail`；错误类型集中定义在 `common.error`。
- MyBatis 映射统一用 XML；写操作显式声明事务；大模型调用不放在数据库事务里。
- 日志用 SLF4J；禁止记录密钥、提示词全文、标准原文和用户上传的原文。
- 注释只写不明显的设计意图，用中文；标识符、日志用英文。

## 大模型与检索规则

- 算法合规检查和量化评分由 `rules` 包里的确定性代码完成；大模型只负责理解描述、抽取信息和组织文字。规则判为不合规的项，模型不能改判为“符合”。
- 回答和报告中的每条引用必须是本次检索结果里存在的条款（格式 `GB/T 39786-2021#<条款号>`），由 `CitationValidator` 校验；校验失败的引用不能显示为有效来源。
- 大模型调用必须设超时，不做隐藏重试；失败写入 `llm_call` 并返回明确错误。外部依赖不可用时返回 503，不静默降级。
- 结构化输出必须经过 JSON Schema 校验，校验失败按错误处理，不静默兜底。
- 提示词放 `src/main/resources/prompts/`；修改提示词时新建版本文件，并在 decisions.md 写明原因。
- 测试不调用真实模型，用 `FakeChatModel`、`FakeEmbeddingModel`、`FakeRerankClient`；真实模型只在 `eval` profile 下运行。

## 数据与合规

- `data/raw/`、`data/private/`、`data/normalized/` 已被 git 忽略。标准原文、考核题库及其派生数据（包括抽样题目）禁止提交，禁止写进测试、代码注释和文档示例。
- 测试只使用 `data/fixtures/` 下自己编写的“仿标准”文本。
- 页面、报告、README 不得出现“自动出具测评结论”之类的说法；报告标注“辅助自查草稿，需测评人员确认”。
- `.env` 不提交；compose 文件和配置里不写真实密钥。

## 评测纪律

- 评测集放 `eval/datasets/`，文件名带版本号。不为提分修改题目、标注或阈值；确需修正时新建版本，并在 `eval/datasets/CHANGELOG.md` 写明原因。
- 每次评测输出到新目录 `eval/results/<UTC时间戳>-<suite>/`，目录已存在就报错；保留失败的结果，不覆盖、不删除。
- 报告写明：git commit、模型、提示词版本、数据集版本、检索模式、样本数、运行时间。题库相关的输出只记录题号和对错。
- 模拟被测系统的预期结果（`eval/datasets/systems/`）只由仓库所有者填写，不得修改。

## 完成标准

- [ ] 任务卡的验收命令全部通过，并贴出了输出
- [ ] 新逻辑有单元测试；涉及 MySQL、Redis、ES、Kafka 的逻辑有 Testcontainers 集成测试
- [ ] 没有提交禁止的数据，没有未经同意的新依赖
- [ ] `docs/PROGRESS.md` 已更新；有取舍时 `docs/decisions.md` 已追加
- [ ] 写了中文“讲解”

## 领域术语（代码、提示词和页面统一使用）

- 密评：商用密码应用安全性评估。等级：第一级至第四级，示例默认第三级。
- 安全层面（技术）：物理和环境、网络和通信、设备和计算、应用和数据；（管理）：管理制度、人员管理、建设运行、应急处置。
- 测评对象：被测系统中承载密码应用的具体对象，如 Web 服务器、数据库、VPN 网关。
- 判定结果：符合、部分符合、不符合、不适用。
- 条款引用：`标准号#条款号`，例如 `GB/T 39786-2021#6.2.1`（编号仅为格式示例）。
