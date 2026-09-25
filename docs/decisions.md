# 设计决策记录

新记录追加在最上面。每条写清：决定、备选方案、理由、影响。

## 待确认

- 问答拒答阈值（T09 用评测结果校准）：当前 `app.retrieval.refuse-threshold=0.1` 只是占位
- 直接使用 `com.networknt:json-schema-validator`（经 spring-ai-client-chat 传递引入，未在 pom 显式声明），是否改为显式依赖（T09）
- 一次性批量开发 T01–T23 时，按任务卡预先批准了卡中点名的依赖（见各条记录）；未点名的依赖均未引入
- 检索评测集：调研的 150 条候选（`docs/crypto_research/retrieval_queries.unit_candidates.v2.jsonl`）gold 是测评单元级编号（如 `#6.1.1`），检索指标按条款号精确匹配，命中其下级条款（`#6.1.1.1`）不算命中；标准入库后由仓库所有者按实际切分选定 gold，再转成 `eval/datasets/retrieval_queries.v1.jsonl`
- 调研清单中 D05（双标准重复指标）、D06（应/宜/可与适用性）、D08（高风险项）尚未建模；D10–D17 属于评测与运维事项，按调研建议在正式评测前处理

## 2026-09-25 / 验收反馈：回答格式校验、检索默认页、时间与复核表可读性

- 问题 1：问答回答里带 Markdown 标记（`**`、`#`、`- `），页面按纯文本渲染（出于安全不用 v-html），标记原样显示。
  - 决定：
    - 提示词新建 `qa-answer.v2.st`，明确只输出纯文本，分点用“1. ”编号，列出禁用的 Markdown 标记；v1 保留。
    - 后端 `AnswerFormat` 在整段回答生成后检查 7 类标记（heading、bold、bullet、code、quote、table、link），结果放在 done 事件的 `formatIssues` 中；条款引用的方括号不会误判为链接。
    - 页面显示时去掉这些标记，并在 `formatIssues` 非空时显示“格式校验未通过”标签，不静默处理。
  - 备选：引入 Markdown 渲染库 + DOMPurify（新增依赖，模型输出进入 HTML，XSS 面变大）；流式输出过程中拦截改写（改变模型原文）。
  - 验证：真实模型两个问题，v2 提示词下 `formatIssues` 均为空。
- 问题 2：检索调试页没有查询时是空白，`k` 含义不清，结果不分页。
  - 决定：新增只读接口 `GET /api/kb/clauses/page?page&size&layer&level`（size 1–50，越界 400），无查询时按原文顺序分页浏览条款，层面、等级筛选同样生效。
  - `k` 改名为“返回条数（Top K）”并加说明：它是一次检索返回的结果总数，不是页码；结果在前端每页显示 10 条。
- 问题 3：评估项目名称里是 UTC 的 ISO 时间。
  - 决定：页面上的时间统一按北京时间 24 小时制（`YYYY-MM-DD HH:mm:ss`）显示，旧名称中嵌入的 ISO 时间在显示时转换；差距评测新建项目时名称直接用北京时间。
  - 后端存储与接口仍用 UTC Instant。
- 问题 4：复核表“证据与理由”“整改建议”列太窄；“D / A / K”表头难懂。
  - 决定：两列设最小宽度、最多显示 4 行，行首可展开查看完整的证据、理由、缺少的信息和整改建议；项目列表栏收窄（5/19）。
  - 表头改为“测评维度 ⓘ”，悬停说明 D/A/K、Ra、Rk 的含义；复选框标为“D 有效性”“A 算法合规”“K 密钥管理”；管理层面显示“管理层面直接判定”。

## 2026-09-25 / scoring.v2、algorithms.v2 与评分 golden v2 核对完成（T12、T15）

- 决定：仓库所有者核对了 `rules/scoring.v2.yaml`（表 4 权重、Ra/Rk）、`rules/algorithms.v2.yaml`（security_bits）和 `scoring_golden.v2.yaml`，两份规则改为 `reviewed: true`，golden 改为 `owner_confirmed: true`；删除文件中“待核对”的说明。项目内的调研候选副本（`src/test/resources/scoring/research_arithmetic_candidates.yaml`）同样标为已确认；`docs/crypto_research/` 下的调研交付件保持原样（有 SHA256 清单）。
- 影响：启动不再打未核对 WARN，报告附录不再注明“未经核对”；此后修改数值一律新建 v3。正式评分评测 `eval/results/20260925T071117Z-scoring/`：33/33 一致（仓库尚无提交，gitCommit 记为 unknown）。

## 2026-09-25 / 测评单元映射、判定依据与评分 golden（调研补充 v2）

依据 `docs/crypto_research/` 新增的测评单元目录（`measurement_unit_catalog.v2.json`、`测评单元_标准编号对照_v2.csv`）。

- 决定 1：`scoring.v2.yaml` 的 41 个测评单元按层面内顺序与 GB/T 43206-2023 的测评单元（6.1.1–7.4.3）一一对应，`clause_refs` 写单元条款号。
  - 技术单元的标题完全一致。管理单元的名称沿用量化评估规则表 4 的写法，同时把 GB/T 43206 的标题加入 `titles`。
  - `OfficialScoringRules` 把映射条款的下级条款也归入同一单元（`6.1.2.1` 归入 `6.1.2`，精确到“编号 + .”，`6.1.20` 不算）。
  - `unscored_clause_refs` 写入 GB/T 43206 第 4 章、第 5 章、附录 A，以及 GB/T 39786 第 5 章、附录 B（含下级）。
- 核对：表 4 中各单元有权重的等级，与调研目录中 GB/T 39786 各等级是否列有对应要求，41 个单元全部一致。这证实了“哪些等级评价”，但不能替代对权重数值的核对。
- 决定 2（调研 D05）：判定以 GB/T 43206 的测评指标为准。
  - 规范化时只把 GB/T 43206 各单元的测评指标条款（X.1）标为 `要求`，其他下级条款标为 `测评`。
  - GB/T 39786 第 6–9 章标为 `说明`：保留层面和等级，供检索与问答使用。
  - 逐条的建议注释见 `docs/规范化元数据对照.md`。
- 备选：以 GB/T 39786 的要求为判定对象；或两份标准都判定。
- 理由：
  - GB/T 39786 第 6–9 章每个层面只有一个条款，要求写成 a) b) c) 列项，无法按条款号归入单个测评单元。
  - 两份标准都判定会重复计分。
  - GB/T 43206 本身就是按测评单元组织的。
- 影响：
  - 这是规范化约定，不改变解析器。
  - 若 GB/T 39786 的这几章仍标为 `要求`，产生的差距项会在确认评分时因无法映射返回 409。
- 决定 3：评分 golden `src/test/resources/scoring_golden.v2.yaml` 由调研算术候选转换而来，期望值照抄候选，不由本项目计算器生成。
  - 共 33 条；AR15–17 的“弥补”不在自动评分流水线中，未收录。
  - ScoringEvalSuiteTest 用生产规则跑出 33/33 一致。
  - 已由仓库所有者确认（2026-09-25，owner_confirmed: true）。
- 决定 4：超时默认值与仓库所有者在 `.env` 中的设定对齐：`JUDGE_TIMEOUT` 240 s、`LLM_HTTP_TIMEOUT` 540 s、`RERANK_TIMEOUT` 180 s（`application.yml` 与 `.env.example`）。
  - 原因：judge.v2 的输出约为 v1 的 4 倍，90 s 下约三分之一调用超时；题库评测的长查询在 CPU 上重排超过 30 s。
  - 影响：在线问答使用 hybrid_rerank 时，最长可能等待 180 s 才返回 503；调研 D14（重排 topN）在正式评测前仍需比较。
- 决定 5：HMAC-MD5 的 `security_bits` 保持为空。
  - 理由：NIST SP 800-57 / 800-107 没有给出基于 MD5 的 HMAC 的强度数值，不猜。
  - 影响：命中它时 Ra 列为待补参数，由测评人员在复核页面选择。

## 2026-09-25 / 按调研结果改为官方量化评估规则（scoring.v2、judge.v2、题库本地推理）

依据 `docs/crypto_research/`，并对照《商用密码应用安全性评估量化评估规则（2023 版）》原文（第 5 章、表 1–4）核实。仓库所有者确认了四个选项：完整实现官方规则、规则约束按表 1、标准可外发但题库只本地、零分母不出总分。

- 决定 1（调研 D01、D02、D03）：评分改为官方算法，新增 `OfficialScoringRules`、`OfficialScoring`、`OfficialScoringCalculator`，规则文件为 `rules/scoring.v2.yaml`。
  - 技术测评对象按 D/A/K 计分（表 1）。
  - 测评单元取对象的算术平均。
  - 安全层面按表 4 中随等级变化的单元权重加权；“/”表示该等级不评价。
  - 总分 = 70 × 技术四层面加权平均（10/20/10/30）+ 30 × 管理四层面加权平均（8/8/8/6）。
  - 对象、单元、层面保留 4 位小数，总分保留 2 位。
  - 条款到测评单元的映射：先看 `clause_refs`，再在同一层面内按标题完全一致匹配；通用要求、密码产品和密码服务指标不单独评价。
  - 已删除 v1 的 `ScoringRules`、`ScoringCalculator`。
- 备选：保留 v1（每个层面 7.5、部分符合统一 0.5），只修正层面权重。
- 理由：
  - 调研发现 v1 的管理层面权重和“部分符合 = 0.5”都与原文不符。
  - 只改权重会继续产出与官方口径不一致的分数。
- 影响：
  - 已有的 assess_score 行保留当时的 rule_version 和分数。
  - 要按 v1 重算，需要从 git 历史取回 v1 代码。`scoring.v1.yaml` 文件保留作记录，但不再被加载。
  - 评分 golden 用例改为 v2 格式（`scoring_golden.<版本>.yaml`：每个用例写 level，findings 写 d/a/k/ra/rk 或 judgment），正式用例需仓库所有者按新格式手算。
- 决定 2（D04）：规则约束改为按表 1。
  - 模型列出的相关算法被规则判为不合规时，由代码把 A 强制为“不满足”，判定由 D/A/K 推出。
    - D、K 满足时为“部分符合”，对象得分 0.5·Ra。
    - Ra 按不合规算法中最弱者的安全强度取值（表 2）。
    - 取不到安全强度时，Ra 记入 `pendingParameters`，由人工补填。
  - 原先的“符合改为不符合”规则取消。
  - 差距项来源仍记为 RULE，理由前加【规则检查】。
- 理由：表 1 注 1 规定 A 不满足时仍按 0.5·Ra 计分，直接判“不符合”会把分数压成 0，与原文矛盾。
- 影响：
  - 新增 Flyway `V6__finding_dak.sql`：assess_finding 增加 dim_d、dim_a、dim_k、ra、rk、module_level。
  - 提示词新建 `judge.v2.st` 与 `judge-output.v2.json`。原因：让模型分别给出 D/A/K、密码模块等级和是否满足其他密钥管理要求（用于 Rk），而不是只给一个总体判定。v1 文件保留。
  - 复核接口（PATCH findings）新增 d、a、k、ra、rk。
    - 技术层面只改判定时：符合 = D/A/K 都满足，不符合 = D 不满足。
    - “部分符合”必须给出维度，否则返回 400。
    - 管理层面不接受维度。
  - 前端详情页可逐项勾选 D/A/K、选择 Ra/Rk，缺少参数时显示“待填”。
- 决定 3（D07）：技术组或管理组整组不适用时（公式分母为 0），不出总分。
  - assess_score 的 total 行 score 为 NULL，detail_json.note 写明原因。
  - 报告和页面显示“不出总分：…”，转人工判断。
- 备选：按 100 分或只按另一组折算。
- 理由：原文没有规定这种情况，不能替测评人员做决定。
- 决定 4：`rules/algorithms.v2.yaml` 在 v1 结论不变的前提下，为各算法补 `security_bits`（按 NIST SP 800-57 Part 1 Rev.5 表 2 的对称等效强度，RSA/DSA/DH/ECC 按密钥长度），供 Ra 取值。
  - AlgorithmRuleTableTest 断言 v1 与 v2 的合规结论逐条一致。
  - 只给非国密算法补强度：Ra 只在 A 不满足（用了不合规算法）时使用，国密算法不需要。
- 决定 5（D09）：标准原文可以发往外部模型，考核题库及其派生数据只发往本地模型。
  - `QaEvalSuite` 不再使用 `spring.ai.openai` 的模型，改用 `ExamChatModel`：本地 Ollama，默认 `qwen2.5:3b`，环境变量 `EVAL_QA_MODEL`。
  - 读题库前由 `LocalModelGuard` 检查：
    - 服务主机名必须在 `eval.qa.allowed-hosts` 中（默认 localhost、127.0.0.1、::1、ollama）。
    - 拒绝 Ollama 的 `*-cloud` 模型（这类模型经本地服务转发到 ollama.com）。
  - 检查不通过时整次评测失败，不换用其他模型。
  - Spring AI 的 `OllamaChatModel` 默认带重试模板，这里换成不重试；也不自动拉取模型。
  - 报告的运行信息写明 examModel 和 examModelEndpoint。
- 影响：
  - 题库评测的准确率反映的是本地小模型的能力，和外部大模型的问答效果不能直接比较。
  - 要用更大的本地模型，修改 `EVAL_QA_MODEL` 并事先 `ollama pull`。
- 公共接口变更（按仓库所有者“完整实现官方规则”的决定一并完成）：
  - MCP 工具 `compute_score` 增加必填参数 `level`；每项改为 d/a/k/ra/rk（技术层面）或 judgment（管理层面），可选 clause_title。
  - 返回值增加 groups、units、totalNote。
  - `check_algorithm` 返回值增加 securityBits。

## 2026-09-25 / 真实模型联调修复

- 问题 1：问答在真实模型上全部失败（ClassCastException）。Spring AI 2.0 的 `OpenAiChatModel` 对提示词里自带的选项原样使用并强转成 `OpenAiChatOptions`，不会与默认选项合并；`QaService` 传的是通用 `ChatOptions`。单元测试的 FakeChatModel 不做这个强转，所以没发现。
  - 决定：从 `chatModel.getOptions().mutate()` 出发只改温度（与 ChatClient 内部的合并方式一致）；FakeChatModel 改为返回 `OpenAiChatOptions` 并对非该类型的选项抛同样的异常，QaIT 先复现失败再修复。
- 问题 2：判定在 60 秒时失败并记为 ERROR。OpenAI SDK 自身的 HTTP 超时（`spring.ai.openai.timeout` 60 s）比判定超时（90 s）先触发，异常被包成 `OpenAIIoException`。
  - 决定：SDK 的 HTTP 超时改为兜底上限 `LLM_HTTP_TIMEOUT`（默认 180 s）。注意 Spring AI 2.0.1 有两处超时：客户端的 `spring.ai.openai.timeout`，以及 `OpenAiChatOptions` 自带的每次请求超时（默认 60 s，会覆盖前者，而且 `toOptions()` 不复制 timeout，无法通过配置修改）。因此新增 `LlmRequestOptions`，所有模型请求都从模型默认选项出发、显式设置每次请求的超时与温度，由 ApplicationContextIT 和 StructuredLlmClientTest 断言。重试次数只在客户端上（`spring.ai.openai.max-retries: 0` 对 chat 生效），没有隐藏重试，业务超时以 `QA_TIMEOUT`、`JUDGE_TIMEOUT` 为准；`LlmErrors.isTimeout` 沿 cause 链识别 `InterruptedIOException`、`HttpTimeoutException`、`TimeoutException`，记为 TIMEOUT / 504。
- 问题 3：eval profile（不启动 Web）在 T18 之后无法启动：`SecurityConfig` 需要 `HttpSecurity`。
  - 决定：`SecurityConfig` 与限流配置加 `@ConditionalOnWebApplication(type = SERVLET)`；新增 `NonWebContextIT` 覆盖非 Web 上下文启动。
- 影响：测试替身要尽量模拟真实组件的约束，否则集成测试会漏掉只在真实环境出现的错误；每种运行方式（Web、eval）都要有启动测试。

## 2026-09-25 / 规则表与评分规则核对完成（T12、T15）

- 决定：`rules/algorithms.v1.yaml`、`rules/scoring.v1.yaml` 由仓库所有者核对完毕，version 定为 `v1`、`reviewed: true`，删除文件中的草稿说明和各条依据里的“草稿：…待核对”字样。此后修改结论或数值一律新建 v2 文件。
- 影响：启动时不再打草稿 WARN，报告附录不再标注“未经核对”；assess_score 与判定记录的规则版本为 `scoring.v1`、`algorithms.v1`。

## 2026-09-24 / 调用链追踪与成本统计（T20）

- 决定：
  - 引入 `spring-boot-starter-opentelemetry`（Micrometer Tracing + OTel 桥 + OTLP 导出器）。追踪总开关 `TRACING_ENABLED` 默认 false；配置 `LANGFUSE_OTLP_ENDPOINT` 等三个变量后，`LangfuseEnvironmentPostProcessor` 把它们转换成 Boot 的 `management.opentelemetry.tracing.export.otlp.*`（路径补全 `/v1/traces`、HTTP 传输、Basic 认证头），代码不依赖 OTel 的运行时类。关闭 OTLP 指标推送。
  - 自定义 span 用 Micrometer Observation API：`retrieval.search/bm25/dense/fusion/rerank`、`rules.algorithm-check`；只带模式、条数、规则版本等，不带查询文本。并行检索跑在虚拟线程上，显式传入父 observation。模型调用的 span 由 Spring AI 自带的 observation 产生（含 token 用量），`log-prompt`、`log-completion` 显式设为 false。
  - 费用：`PRICE_TABLE`（元/百万 tokens）在写 `llm_call` 时计算 `cost_cny`（6 位小数）；未配置单价的模型费用为空，报告显示“未配置单价”，不按 0 计。
- 理由：提示词和用户输入可能含敏感信息，一旦发到外部追踪服务就无法收回。

## 2026-09-24 / Kafka 异步入库、Redis 限流与缓存（T19）

- 决定：
  - 引入 `spring-boot-starter-kafka`（spring-kafka 4.1.1，Boot 管理）与 `testcontainers-kafka`（任务卡点名）。`app.ingest.mode` 默认 `sync`，设为 `kafka` 时导入接口先同步做格式校验，再按 (normalized_sha256, parser_version) 建 `ingest_job` 并发消息（key 为内容哈希，同一内容落在同一分区、顺序处理），返回 202。
  - 幂等：消费者先检查任务是否已成功，再用条件更新 claim（RUNNING、attempts+1）；已成功的重复消息直接确认。导入本身也按内容哈希幂等。
  - 重试：`DefaultErrorHandler` + `ExponentialBackOffWithMaxRetries(3)`（初始 1 秒、倍数 2），用尽后 `DeadLetterPublishingRecoverer` 写到 `kb.ingest.v1-dlt`（spring-kafka 4 默认后缀 `-dlt`，同分区号，所以死信 topic 分区数与原 topic 相同），并把任务标记 FAILED。格式错误、任务不存在、文件内容已变不重试。死信消息的处理：修复原因后重新调用导入接口，FAILED 任务会重新排队。
  - 限流：令牌桶放在 `redis/token_bucket.lua`，读余量、按 Redis 服务器时间补充、扣减、写回在一个脚本里原子完成；按 API Key 客户端或来源地址分桶；只限制 `/api/qa` 与 `/api/assessments/*/analyze`；默认容量 20、每秒补 0.2 个；Redis 不可用时 503。
  - 向量缓存：`CachingEmbeddingClient` 装饰 `EmbeddingClient`，键 `emb:<模型>:<文本 SHA256>`，值为 float 数组的 Base64，TTL 30 天；只把未命中的文本交给模型；Redis 不可用时 503（不绕过）。检索评测报告写入本次运行的命中率。
  - 测试默认关闭缓存与限流（`src/test/resources/config/application.properties`），专门的集成测试显式开启，避免所有集成测试都要起 Redis。
- 备选：限流用 Redisson / Bucket4j（未确认兼容 Boot 4，且约定自己实现）；Kafka 事务消息（单消费者场景收益小）。

## 2026-09-24 / MCP Server、鉴权与审计（T18）

- 决定：
  - 引入 `spring-ai-starter-mcp-server-webmvc`（Streamable HTTP，端点 `/mcp`，SYNC）与 `spring-boot-starter-security`（任务卡点名）。四个只读工具 `search_clauses`、`get_clause`、`check_algorithm`、`compute_score` 用 `@McpTool` 定义，名称、描述写死在注解里，并标注 readOnlyHint。
  - 鉴权：`X-API-Key`（或 `Authorization: Bearer`）对照 `MCP_API_KEYS`；注册表只保存密钥的 SHA-256，常数时间比较，配置格式错误启动失败且报错不含密钥。无密钥/错误密钥 401。
  - 授权：所有工具共用 `/mcp`，按 URL 无法区分，由 `McpScopeFilter` 读 JSON-RPC 请求体（上限 256 KB），对 `tools/call` 按工具名检查 scope，不足返回 403 ProblemDetail 并写审计 DENIED。
  - 审计：每次工具调用写 `audit_log`（actor=客户端 id、channel=mcp、action=工具名、args_sha256=键排序后的参数 JSON 的哈希、result=OK/ERROR/DENIED），不存参数原文。工具在请求线程执行（Spring AI 对 Servlet 同步服务设置了 `immediateExecution(true)`），所以能直接拿到认证信息。
  - 顺带落实开发计划的“管理接口需要鉴权”：`POST /api/kb/documents`、`POST /api/kb/reindex` 需要 admin scope；检索、问答、评估接口仍然开放（仅本机使用）。
- 备选：Spring Security OAuth2 Resource Server（需要授权服务器，单机项目过重）；在工具方法里检查 scope（只能返回 isError，拿不到 403）。
- OWASP MCP Top 10（2025）自查：
  - MCP01 Token 管理与密钥暴露：密钥只来自环境变量；内存里只存哈希；日志、审计、报错都不含密钥。未实现密钥轮换与过期，换密钥需要重启。
  - MCP02 Scope 蔓延导致提权：三个固定 scope；工具与 scope 的映射写死在 `McpToolScopes`；只有只读工具。
  - MCP03 工具投毒：工具描述写死在代码里，不从数据库或配置读取；不聚合第三方 MCP 服务器（`expose-mcp-client-tools` 保持关闭）。
  - MCP04 供应链：依赖版本由 BOM 与 pom 固定；IK 插件校验 SHA256。未做 SBOM 与依赖漏洞扫描。
  - MCP05 命令注入：工具不执行命令、不拼 SQL（MyBatis 参数绑定）、ES 查询用类型化客户端。
  - MCP06 通过上下文的提示词注入：工具本身不调用模型；返回的条款原文可能被调用方模型当作指令，这是调用方的风险，返回内容做了长度截断。
  - MCP07 认证授权不足：见上，401/403 有集成测试。
  - MCP08 缺少审计与遥测：每次调用写审计；T20 接入调用链追踪。
  - MCP09 影子 MCP 服务器：端口只绑定 127.0.0.1（compose 与本机运行）；只有一个服务器实例。
  - MCP10 上下文注入与过度共享：返回字段最小化（摘要截断 200 字、条款正文截断 4000 字、结果上限 16 000 字符、检索最多 10 条）；不返回内部 id 与向量。
- 接入方式：`npx @modelcontextprotocol/inspector` 选 Streamable HTTP，URL `http://127.0.0.1:8080/mcp`，请求头加 `X-API-Key`；Claude Code：`claude mcp add --transport http crypto-assess http://127.0.0.1:8080/mcp --header "X-API-Key: <密钥>"`。

## 2026-09-24 / 报告导出（T17）

- 决定：引入 Apache POI `poi-ooxml` 5.5.1（任务卡点名，版本固定在 pom 属性里）。报告内容全部来自数据库；整改建议在判定步骤落库；导出不调用模型；导出本身是 SCORED/REPORTED 下允许的状态转换，重复导出保持 REPORTED。
- 理由：导出时再调用模型会让同一份数据每次导出结果不同，也无法追溯。

## 2026-09-24 / 差距识别评测（T16）

- 决定：方案 A 一次性把系统描述和适用指标（与工作流相同的要求匹配结果，不含检索补充）交给模型；方案 B 通过 AssessmentService 建真实项目并同步跑完整工作流。按（对象, clause_ref, 判定）严格匹配；误报列表写进报告由仓库所有者复核；两种方案都用 `app.judge.temperature`（0）。
- 影响：评测会在数据库里留下名为 `eval-gap <时间>` 的评估项目，便于复查。

## 2026-09-24 / 量化评分（T15）

- 决定：评分器是纯函数，判定分值、指标权重、层面权重、不适用处理、同一指标多对象的汇总方式（mean/min）、舍入位数和方式全部来自 YAML；中间结果用 BigDecimal DECIMAL128 不舍入，只在输出时舍入一次（默认 2 位 HALF_UP）。confirm 与评分在同一事务里完成，assess_score 记录 rule_version。
- 备选：直接按某一版量化评估规则把公式写死在代码里。
- 理由：规则会更新，旧报告要能按当时的 rule_version 复现；具体数值属于领域判断，由仓库所有者核对。
- 影响：`scoring.v1.yaml` 已于 2026-09-25 核对；现行规则里的高风险项、D/A/K 测评单元若需要，要扩展计算器。

## 2026-09-24 / 评估工作流（T14）

- 决定：步骤粒度为 MATCH、RULE_CHECK（每对象一步）和 JUDGE（每条测评指标一步，step_key 为 clause_ref）；步骤结果存 output_json，再次分析时跳过已成功的步骤；模型调用在事务外，差距项与步骤成功在同一短事务里写入；分析在虚拟线程里执行，接口返回 202；启动时自动续跑停在 ANALYZING 的项目（`app.workflow.resume-on-startup`），遗留的 RUNNING 步骤先标记为失败。人工复核允许测评人员把判定改成任何值（包括规则判为不合规的项），来源记为 REVIEWER。
- 备选：@Async + 线程池；Spring Batch / 工作流引擎。
- 理由：任务卡要求自己实现；按指标粒度落库，失败重跑的成本最小。

## 2026-09-24 / 结构化判定的规则约束（T13）

- 决定：模型输出里增加 `relevant_algorithms`；其中任何算法被规则表判为 NOT_APPROVED/INSECURE 时，判定不能是“符合”，由代码改为“不符合”，理由前加【规则检查】说明，差距项来源记为 RULE，`rule_hits_json.ruleOverride = true`。`clause_refs` 超出输入条款按输出错误处理（LLM_INVALID_OUTPUT）。
- 备选：模型给出“符合”时直接报错；把对象的所有不合规算法都作用到每条指标上。
- 理由：前者会让工作流频繁失败；后者会把存储算法的问题误算到无关的指标上。改判是显式的、可见的，不属于静默兜底。
- 影响：约束依赖模型列出相关算法，模型漏列时不触发，评测时关注这类误判。

## 2026-09-24 / 规则表草稿（T12）

- 决定：代码不写死任何算法的判断，别名、类别、状态、依据、条款引用全部来自 YAML；归一化规则是“别名最长前缀 + 剩余部分只能是位数/模式/填充”，否则 UNKNOWN；YAML 在启动时校验，不合法则启动失败。正式规则表 `algorithms.v1.yaml` 由 CC 按公开常识起草，标为 `v1-draft`、`reviewed: false`，条款引用留空，等仓库所有者核对。测试使用单独的 `src/test/resources/rules/algorithms.test.yaml`，不依赖正式结论。

## 2026-09-24 / 结构化输出：advisor 不重试 + 显式 JSON Schema 校验（T09，T13 复用）

- 决定：`StructuredLlmClient` 用 `StructuredOutputValidationAdvisor`（`maxRepeatAttempts(0)`），再用同一份 `schemas/*.json` 对输出做显式校验，失败抛 `LLM_INVALID_OUTPUT` 并把 `llm_call.status` 记为 `INVALID_OUTPUT`。允许剥掉 Markdown 代码围栏，除此之外不做任何修补解析。
- 备选：advisor 默认 3 次重试；`BeanOutputConverter` 直接解析；引入别的校验库。
- 理由：advisor 默认会带着错误信息重新调用模型，属于约定禁止的隐藏重试；重试次数为 0 时它校验失败只打警告、照常返回，所以必须再加一层显式校验把不合规输出变成错误。networknt 校验器已经随 Spring AI 在类路径上，不新增依赖。
- 影响：评测中不合规输出按答错计、单独统计；业务接口里返回 502 ProblemDetail。

## 2026-09-24 / 问答评测的输出与题库转换（T09）

- 决定：
  - 不引入 ofdrw：题库 OFD 由仓库所有者用 OFD 阅读器导出文本，`QaBankParser` 解析文本；版式不符的题目按行号报错，不猜。
  - `cases.jsonl` 只写 `id`、`group`、`correct`；报告不含题目原文、选项和模型输出。
  - 评测时 hybrid_rerank 组即使低于拒答阈值也照常作答，报告给出阈值扫描表（各阈值下的拒答率、保留题与拒答题准确率），用于校准阈值。
  - 模型服务不可用时整次评测失败；输出不合规、超时按答错计并单独计数。
- 备选：引入 ofdrw-converter 直接读 OFD；低于阈值的题直接判错。
- 理由：没拿到 OFD 原件前无法验证 ofdrw 的抽取效果，先走人工导出，避免为不确定的收益加重依赖。阈值校准需要知道“如果拒答，损失的是答对的题还是答错的题”，所以必须全部作答。
- 影响：真实题库到位后，如果导出文本版式与解析器不符，需要调整 `QaBankParser` 或手工整理文本。

## 2026-09-24 / 检索评测运行器（T07）

- 决定：`eval` profile 不启动 Web；`EvalService` 用 `Files.createDirectory` 建结果目录，已存在即失败；失败的运行写 `error.txt` 并在 `eval_run` 记为 FAILED。指标（Recall@k、MRR@10、nDCG@10、最近秩法 P50/P95）自己实现。检索评测不加层面过滤，layer 只用于分组。
- 理由：结果目录不可覆盖，失败也要留痕，才能保证报告里的每个数字都能追溯。
- 影响：真实评测集 `eval/datasets/retrieval_queries.v1.jsonl` 由仓库所有者标注；未到位前只用 fixtures 做了冒烟运行，结果放在临时目录，没有写进 `eval/results/`。

## 2026-09-24 / 四种检索模式（T06）

- 决定：BM25 与向量各取 50 条，并行执行；RRF 在应用层实现，k=60，同分按 BM25 名次、再按向量名次、再按 clause_ref 排序；hybrid_rerank 只重排融合后前 30 条；重排文本 = 章节路径 + 标题 + 正文；TEI 用 `raw_scores=false`（sigmoid 后的 [0,1] 分数），拒答阈值基于这个尺度。过滤条件放进 bool filter 与 kNN filter，两路一致。
- 备选：ES 自带的 rrf retriever；重排失败时退回 hybrid。
- 理由：融合放在应用层才能拿到每一路的名次和分数给调试页与评测用；重排挂了静默退回会让评测和线上行为不一致。
- 影响：CPU 上的 TEI 重排 30 条约 16 秒（本机实测，见 PROGRESS），hybrid_rerank 的延迟主要来自这里。

## 2026-09-24 / 条款索引（T04）

- 决定：物理索引名 `kb_clause_v1_<UTC 时间戳>`，别名 `kb_clause`；先向量化再建索引，写入后校验文档数，原子切换别名后删除旧索引，任何一步失败都删掉半成品。所有条款（含只有标题的章节）都入索引，保证 ES 文档数与 MySQL 条款数一致。映射只从 `es/kb_clause_v1.json` 读取，`dynamic: strict`。
- 发现：ES 9 默认不在 `_source` 里返回 `dense_vector`（向量仍然被索引），测试改为用 kNN 查询验证向量存在。
- 理由：别名切换让重建期间检索不中断，也能回滚。
- 影响：映射变更时新建 `kb_clause_v2.json` 并改 `ClauseIndexer.MAPPING_VERSION`。

## 2026-09-24 / 条款解析与入库（T03）

- 决定：
  - 幂等键 `(doc_code, normalized_sha256)`，sha 基于统一为 LF 换行后的全文；同一标准号内容变化时，在同一事务里删除旧条款、旧文档标记 SUPERSEDED、写入新文档。
  - 批量插入用 MyBatis `<foreach>` 生成多行 VALUES，每批 200 条。
  - 实体用 record + `arg-name-based-constructor-auto-mapping`；自增主键通过单独的 `GeneratedKey` 对象回填。
  - 导入接口只接受规范化目录下的纯文件名，服务端再校验解析后的路径仍在目录内。
- 备选：`ExecutorType.BATCH`（需要 `rewriteBatchedStatements=true` 才有性能）；按条款逐条 upsert。
- 理由：多行 VALUES 一条语句往返少、不依赖驱动参数；条款号全局唯一（`clause_ref` 唯一键），整份替换比逐条比对简单且不会留下孤儿条款。
- 影响：标准修订后重新导入会整份替换，需要重建索引。

## 2026-09-24 / 语料规范化工具（T02）

- 决定：PDF 文本提取调用系统的 `pdftotext`（poppler-utils），不引入 PDFBox；工具是不依赖 Spring 的命令行类 `NormalizeTool`，用 `java -cp target/classes` 运行。linter 与解析器共用 `NormalizedFormat`。附录顶层标题写作 `# A 附录标题`；条款类型取值 要求 / 测评 / 说明 / 术语。
- 备选：PDFBox（任务卡已点名，可用）。
- 理由：开发机已装 poppler-utils，中文版面抽取效果稳定；少一个依赖。初稿只是起点，正确性靠人工核对 + linter。
- 影响：没有 pdftotext 的机器需要先安装 poppler-utils。

## 2026-09-24 / 本地环境与版本组合（T01）

- 决定：
  - Elasticsearch 服务器 9.4.5 + IK 9.4.5（`get.infini.cloud/elasticsearch/analysis-ik/9.4.5`，SHA256 固定在 Dockerfile 里）+ Boot 4.1.1 管理的 elasticsearch-java 9.4.5，三者一致，pom 不覆盖客户端版本。
  - ES 客户端用 `spring-boot-starter-elasticsearch`（Boot 4 的官方客户端 starter，基于 Rest5Client），不用 Spring Data Elasticsearch。
  - compose 只在 deploy/ 下，根目录 `.env` 通过 `--env-file .env` 传入（compose 只自动读取 compose 文件所在目录的 .env）。
  - TEI 默认 `--max-batch-tokens 4096 --auto-truncate`：默认 16384 时 CPU 预热在本机吃掉约 10.5 GB 内存；调小后约 3 GB。模型可以预先下载到 `tei-data` 卷，再用 `TEI_MODEL_ID` 指向卷内快照目录离线启动（TEI 不认 `HF_HUB_OFFLINE`，网络不通时会卡在下载）。健康检查接受这种快照路径。
  - `/api/health` 并行检查、每项限时 5 秒；ES 项用 ik_smart 分析一段中文，逐字切分即判为不可用；大模型只检查配置，不调用。
  - Ollama 镜像固定 0.34.4。
- 备选：ES 9.1.4（开发计划里确认过的 IK 版本）并在 pom 覆盖客户端版本；smartcn 分词器。
- 理由：IK 已有 9.4.5 构建，直接跟随 Boot 管理的客户端版本最省事，也避免覆盖 BOM。
- 发现：ik_smart 对“开展密钥管理工作”会切成 密钥 / 管理工作（主词典里的“管理工作”优先），单独分析“密钥管理”则是一个词。索引端用 ik_max_word 会产生“密钥管理”，影响有限，但口语查询的 BM25 召回可能受歧义切分影响。
- 影响：升级 Spring Boot 时要同步检查 IK 是否有对应版本；Testcontainers 从同一个 Dockerfile 构建镜像，标签带构建目录的内容哈希。

## 2026-09-24 / 工程骨架与模型接入配置（T00）

- 决定：
  - 用 start.spring.io 生成骨架：Spring Boot 4.1.1、Spring AI 2.0.1（`spring-ai-bom`）。
  - start.spring.io 只在 Boot 4.0.x 下提供 MyBatis，因此手动加入 `mybatis-spring-boot-starter` 4.1.0，版本号写在 pom 的 property 里。
  - 在 `application.yml` 中显式选择模型：`spring.ai.model.chat=openai`、`spring.ai.model.embedding=ollama`，图像、语音、审核设为 `none`。
  - `spring.ai.openai.max-retries=0`，`timeout=60s`。
  - `spring.ai.openai.api-key` 默认为空字符串。
  - 删除 start.spring.io 附带的 `spring-ai-spring-boot-testcontainers`、`testcontainers-ollama`；Testcontainers 只启动 `mysql:8.4`。
  - 在 pom 中声明 `maven-failsafe-plugin`，使 `*IT` 在 `verify` 阶段运行。
- 备选：
  - 两个 starter 都注册 ChatModel，再用 `@Primary` 选择。
  - api-key 不给默认值，缺失时启动即失败。
  - 集成测试直接用 Ollama 容器。
- 理由：
  - 两个 starter 的自动配置都用 `matchIfMissing = true`，不指定时会出现两个 `ChatModel`，`ChatClient.Builder` 无法注入；OpenAI 的图像、语音自动配置在没有凭据时直接启动失败。这是 T00 第一次运行 `ApplicationContextIT` 时的失败原因。
  - Spring AI 2.0 的 OpenAI 模块基于官方 openai-java SDK，SDK 默认重试 3 次，这属于约定禁止的隐藏重试。
  - api-key 为空字符串时 SDK 进入免鉴权模式，应用能启动，调用时由服务端显式报错。知识入库、检索等不依赖大模型的功能不应因为缺少密钥而无法启动。
  - 测试约定不调用真实模型，Ollama 镜像又大，放进常规集成测试不划算。
- 影响：
  - 以后新增 AI starter 时，要同步检查 `spring.ai.model.*` 的选择。
  - Spring AI 2.0 的 OpenAI 配置项和 1.x 教程不同：模型名是 `spring.ai.openai.chat.model`，不是 `chat.options.model`。
  - 本地开发如果不配 `LLM_API_KEY`，问答类功能会在调用时失败，而不是在启动时失败。

## 2026-09-24 / 数据边界

- 决定：标准原文、考核题库及其派生数据只在本地使用，不进仓库；测试使用自己编写的“仿标准”样例。
- 理由：标准可以公开查阅不等于可以再分发；考核题库的知识产权归国家密码管理局。
- 影响：仓库只放获取说明、转换脚本和聚合后的评测指标。

## 2026-09-24 / 技术栈

- 决定：Java 21 + Spring Boot 4.1 + Spring AI 2.0；MyBatis + MySQL 8.4 + Flyway；Elasticsearch + IK 同时承担 BM25 与向量检索，RRF 融合放在应用层；TEI 做重排；Ollama 提供 bge-m3 向量。
- 备选：Python + LangGraph + Milvus。
- 理由：衔接 3 年 Java 经验；目标雇主（电力、密码企业、央国企）以 Java 为主；与项目一（Python）形成互补；融合逻辑放在应用层便于评测和讲解。
- 影响：网上不少资料针对 Spring AI 1.x 和 Spring Boot 3，编码前需要核对 2.0 与 Boot 4 的官方文档。
