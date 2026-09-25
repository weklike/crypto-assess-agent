# PROGRESS

最后更新：2026-09-25

## 任务状态

状态取值：未开始 / 进行中 / 已完成 / 受阻 / 部分完成（代码与自动化验收已完成，剩余验收依赖仓库所有者的人工工作、真实语料或 LLM_API_KEY，见备注）

| 任务 | 状态 | 完成日期 | 验收结果 | 备注 |
| --- | --- | --- | --- | --- |
| T00 仓库与工程骨架 | 已完成 | 2026-09-24 | `./mvnw -q verify` 通过（ApplicationContextIT 2/2）；`.env`、`data/raw/x.txt` 不出现在 `git status` | Boot 4.1.1、Spring AI 2.0.1、MyBatis starter 4.1.0；取舍见 decisions.md |
| T01 本地环境与健康检查 | 已完成 | 2026-09-25 | core profile 五个服务全部 healthy；配置 LLM 后 `curl 127.0.0.1:8080/api/health` 返回 HTTP 200，mysql/redis/elasticsearch(IK)/ollama/tei-rerank/llm-config 全部 UP；IkAnalyzerIT、HealthIT 通过 | 版本组合 ES/IK/客户端 = 9.4.5 |
| T02 语料准备与规范化 | 受阻 | | 工具完成：`NormalizeTool`（sha256 / draft / lint）、`NormalizedFormatLinter`、`PdfDraftConverter`；fixtures（36 条）lint 0 错误 | 等仓库所有者把两份标准 PDF 放进 data/raw/、生成初稿并人工核对、抽查 50 条；元数据注释按 `docs/规范化元数据对照.md`（GB/T 43206 测评指标标为 要求，GB/T 39786 第 6–9 章标为 说明） |
| T03 条款解析与入库 | 部分完成 | 2026-09-24 | 解析器表驱动测试、KnowledgeIngestIT（重复导入条款数不变、内容变化替换旧版、格式错误整份不入库）通过；本机 fixtures 导入 36 条，重复导入返回 200 | 真实数据导入依赖 T02，条款数待填 |
| T04 向量化与 ES 索引 | 部分完成 | 2026-09-24 | ClauseIndexerIT 通过（文档数=条款数、重建切换别名并删除旧索引）；本机用真实 bge-m3 重建 fixtures 索引 36 条，耗时 14 s | 真实数据依赖 T02 |
| T05 检索评测集与标注工具 | 部分完成 | 2026-09-24 | `RetrievalDatasetValidator`、标注工具 `--eval.tool=annotate`（只追加）、校验工具 `--eval.tool=validate` 完成并有测试 | 评测集 v1（≥80 条）由仓库所有者标注，依赖 T02/T04 真实数据；调研已给出 150 条候选（单元级 gold，未经所有者批准），入库后按实际切分选定 gold |
| T06 四种检索模式 | 部分完成 | 2026-09-24 | RrfFusionTest（空列表、单路、重复、并列）、HybridSearchServiceTest、TeiRerankClientTest、SearchIT（四种模式、层面/等级过滤、重排不可用 503）通过；本机真实模型对比见下 | 5 条真实查询对比依赖真实语料 |
| T07 检索评测运行器 | 部分完成 | 2026-09-24 | 指标单元测试、RetrievalEvalIT（报告/指标/cases、目录已存在报错、失败留痕）通过；本机真实模型 + fixtures 冒烟运行成功（结果在临时目录，未写入 eval/results） | 验收命令依赖评测集 v1 |
| T08 问答：RAG、引用校验、SSE、拒答 | 部分完成 | 2026-09-25 | 2026-09-25 验收反馈：提示词 qa-answer.v2（纯文本）+ 回答格式校验（AnswerFormatTest，done 事件 formatIssues，QaIT 覆盖），真实模型 2 个问题格式校验通过；CitationParserTest（全角括号、空格、连续引用等 11 例）、CitationValidatorTest、QaIT（事件顺序 meta→token…→citations→done、无效引用剔除、超时 error 事件且 llm_call=TIMEOUT、空回复、拒答、重排不可用 503）通过 | 真实模型冒烟（fixtures 语料，2026-09-25）：4 个问题正常作答，6 条引用全部有效且都能打开到条款；1 个无关问题拒答（最高重排分 0.0001）。修复了真实模型上的 ClassCastException（见 decisions.md）。正式验收的“5 个真实问题”依赖真实标准入库 |
| T09 题库转换与问答评测 | 部分完成 | 2026-09-24 | `QaBankParser`、分层抽样、`qa-exam.v1` 提示词 + JSON Schema、`StructuredLlmClient`、`QaEvalSuite` 完成；QaEvalIT（三组、cases 只含题号和对错、无重试）通过 | 按调研 D09（2026-09-25）改为只用本地 Ollama 模型（默认 qwen2.5:3b，`LocalModelGuard` 拒绝非本机地址与 *-cloud 模型，不重试）；LocalModelGuardTest、OllamaExamChatModelTest、QaEvalIT 通过；本地模型冒烟（fixtures 6 题 × 3 组，qwen2.5:3b，CPU）：18 次调用全部通过 Schema 校验，端点 ollama@http://localhost:11434，耗时 311 s。第一次运行因 TEI 重排超过 RERANK_TIMEOUT=30s 失败（结果目录保留 error.txt），临时设 120 s 后成功。题库 OFD 下载与导出文本、抽查 20 题、真实评测与阈值校准都依赖仓库所有者；真实模型冒烟（fixtures 6 题 × 3 组，2026-09-25）：qa-exam.v1 提示词与 JSON Schema 在真实模型上全部通过校验，无不合规输出 |
| T10 前端 MVP | 部分完成 | 2026-09-24 | `npm --prefix frontend run build` 通过；`npm --prefix frontend run test` 13 个测试通过（SSE 解析、引用切分、流式客户端、问答组件）；开发代理联调检索与问答流成功 | 录屏需要仓库所有者完成；完整问答演示需要 LLM_API_KEY |
| T11 评估项目数据模型与接口 | 已完成 | 2026-09-24 | AssessmentWorkflowTest（8 状态 × 7 事件共 56 种组合，合法/非法全覆盖）、CryptoMeasuresTest、AssessmentServiceTest（乐观锁冲突 409）、AssessmentApiIT（新建项目、添加对象、查询、400/404/409）通过 | Flyway V4 |
| T12 算法合规规则表 | 已完成 | 2026-09-25 | AlgorithmRuleTableTest：46 种写法归一、10 种认不出返回 UNKNOWN、自由文本抽取、7 种非法 YAML 报错；RulesStartupTest：YAML 不合法时应用启动失败并给出原因 | `rules/algorithms.v1.yaml` 已由仓库所有者核对（2026-09-25，reviewed: true）；2026-09-25 新增 `algorithms.v2.yaml`（结论与 v1 逐条一致，补 security_bits 供 Ra 取值，已由仓库所有者核对，reviewed: true） |
| T13 结构化判定 | 部分完成 | 2026-09-25 | JudgmentServiceTest 11 例通过；judge.v2 真实模型冒烟（2026-09-25，自拟数据库对象含 AES-256-GCM、MD5、口令）：7 条判定输出 D/A/K，Ra 由代码按最弱算法取值（MD5 → 0.2，AES-256 → 1）；平均输出 5843 tokens（v1 为 1550），6 次调用中 2 次超过 JUDGE_TIMEOUT=90s，临时设 240 s 后完成；生产 scoring.v2 未映射通用要求 4.1，确认时按设计返回 409（2026-09-25 改为 judge.v2：输出 D/A/K；规则约束按量化评估规则表 1，相关算法不合规时 A 强制为不满足、判定由维度推出，Ra 按最弱算法的安全强度取值，取不到时列为待补参数） | 真实模型冒烟：3 个自拟测评对象（数据库 AES+MD5、国密 VPN、IC 卡门禁）共 15 条判定，理由引用了规则检查结果，缺信息时给出保守结论和缺失项，视频监控指标对门禁对象判为不适用。基于真实标准的人工检查待真实语料入库 |
| T14 评估工作流与人工确认 | 部分完成 | 2026-09-25 | AssessmentWorkflowIT：第 2 个对象失败→FAILED，重跑只调用剩余 5 次模型；模拟重启后只重跑中断的 1 步；全部确认前 confirm 返回 409；跨项目改差距项 404 | 真实模型冒烟：首次运行在第 9 次判定因 SDK 60 秒超时进入 FAILED，重跑只执行剩余 7 步后进入 REVIEW（21 步全部成功）；15 次判定输入 7815 / 输出 24599 tokens，平均 23 s、最长 48 s。正式的“1 个模拟系统”记录待仓库所有者编写模拟系统 |
| T15 量化评分 | 已完成 | 2026-09-25 | 正式评测 `eval/results/20260925T071117Z-scoring/`：golden v2 33/33 一致（rulesReviewed=true）。2026-09-25 按调研结果改为《量化评估规则（2023 版）》：OfficialScoringTest 44 例（含调研的 36 个算术候选）、ScoringEvalSuiteTest（16 个 v2 手算用例 16/16 一致）通过；AssessmentWorkflowIT 覆盖 D/A/K 复核与“不出总分” | `rules/scoring.v2.yaml` 已由仓库所有者核对（reviewed: true）；clause_refs 已按调研目录映射到 GB/T 43206 测评单元（表 4 适用等级与调研目录 41/41 一致）；`scoring_golden.v2.yaml` 由调研候选转换（33 条，生产规则 33/33 一致，已由仓库所有者确认）；scoring.v1 不再加载 |
| T16 差距识别评测 | 部分完成 | 2026-09-24 | GapEvalIT（fixtures 模拟系统，两种方案、严格匹配、误报列表待复核）通过 | 10–15 个模拟被测系统与预期差距项由仓库所有者编写（eval/datasets/systems/README.md 说明了格式）；真实评测需要 LLM_API_KEY；真实模型冒烟（fixtures 模拟系统，2026-09-25）：纯提示词 1 次调用、工作流 12 次调用均成功，gap-plain.v1 与判定输出都通过 Schema 校验；fixtures 预期差距项只写了 2 条，指标无参考意义 |
| T17 Word 报告导出 | 部分完成 | 2026-09-24 | ReportGeneratorTest（封面免责声明、得分表、差距清单含 D/A/K、不出总分时写明原因、附录版本与草稿提示、无差距项）与工作流 IT 中的导出（POI 读回校验、导出不调用模型、状态 REPORTED）通过 | 版式需仓库所有者用 Word 打开检查 |
| T18 MCP Server、鉴权与审计 | 部分完成 | 2026-09-24 | McpIT：无 Key 401、错误 Key 401、scope 不足 403 且审计 DENIED；官方 MCP Java 客户端 initialize → list 出 4 个工具 → 逐个调用成功（2026-09-25 compute_score 改为 level + D/A/K 输入），非法参数返回 isError；审计只有参数哈希；管理接口需 admin scope。ApiKeyRegistryTest 通过 | MCP Inspector 与 claude mcp add 的手工接入需要仓库所有者在本机试用（命令见 decisions.md） |
| T19 Kafka 异步入库、Redis 限流与缓存 | 已完成 | 2026-09-24 | KafkaIngestIT（Kafka 容器）：同一消息投递 3 次只入库一次（attempts=1）；嵌入服务持续失败时重试 3 次（共 4 次尝试）后进 kb.ingest.v1-dlt，任务 FAILED 并记录错误，恢复后重新提交成功；格式错误在排队前 400。RedisTokenBucketIT：20 个并发请求、容量 5 → 恰好放行 5 个，令牌恢复后可继续，桶键会过期。RateLimitInterceptorTest：429 + Retry-After。EmbeddingCacheIT：第二次查询不调用模型，只嵌入未命中的文本；检索评测报告写入缓存命中率 | 默认 INGEST_MODE=sync；用 Kafka 时起 mq profile 并设 INGEST_MODE=kafka |
| T20 调用链追踪与成本统计 | 部分完成 | 2026-09-24 | RetrievalObservationTest：检索各阶段（search/bm25/dense/fusion/rerank）都有 span，属性里没有查询文本；规则检查有 rules.algorithm-check span；PriceTableTest、LangfuseTracingTest 通过；追踪默认关闭时全部单元与集成测试通过；llm_call.cost_cny 按 PRICE_TABLE 计算，问答与差距评测报告汇总费用 | Langfuse 链路截图需要仓库所有者配置 Langfuse（云端或自托管）与 LLM_API_KEY 后完成 |
| T21 前端二期 | 部分完成 | 2026-09-25 | 2026-09-25 验收反馈修改：检索页无查询时分页浏览条款（新接口 /api/kb/clauses/page）、Top K 说明、结果分页；时间统一北京时间 24 小时制；复核表加宽并可展开，测评维度表头加说明（前端测试 34 个）；`npm --prefix frontend run test` 19 个测试通过（新增轮询、差距项筛选、评估详情组件），`npm --prefix frontend run build` 通过；评估项目页：项目列表与新建、按类别录入密码措施、启动/继续分析与轮询、差距项筛选/改判/备注/确认、全部确认后评分、下载报告；2026-09-25 增加 D/A/K 勾选、Ra/Rk 选择、缺参数提示、不出总分原因（测试共 25 个） | 用模拟系统从新建走到下载报告的录屏需要仓库所有者完成（真实分析需要 LLM_API_KEY） |
| T22 全量评测重跑与总报告 | 受阻 | | 运行器已就绪：`--eval.suite=summary` 依次跑 retrieval、qa、gap、scoring 并生成链接各子报告的总报告（SummaryEvalSuiteTest），`--eval.suite=scoring` 比对 golden 用例（ScoringEvalSuiteTest）；`./mvnw verify` 全绿（2026-09-25）：单元测试 350、集成测试 75 | 依赖仓库所有者完成：规范化标准、评测集 v1、题库样本、模拟系统（评分规则与 golden 已核对，scoring 套件可单独运行） |
| T23 README、演示与简历数字 | 部分完成 | 2026-09-24 | README（定位与免责、架构图、快速开始、MCP 接入、评测命令、限制）、docs/demo-script.md、docs/interview-qa.md（10 题）、docs/resume-numbers.md（模板，未填未测数字）；本机按 README 起 core profile 五个服务 healthy，导入（无 Key 401 / admin Key 200）、重建索引、检索、MCP 401 均符合预期 | 干净环境复现、演示录屏、简历数字需仓库所有者在正式评测后完成 |

## 最新评测结果

| 评测 | 数据集版本 | 关键指标 | 结果目录 |
| --- | --- | --- | --- |
| 评分一致性（scoring） | golden v2（33 条，调研候选转换，所有者已确认）；规则 scoring.v2 | 一致 33/33 | eval/results/20260925T071117Z-scoring/ |
| （检索、问答、差距识别尚无正式评测：评测集 v1、题库样本、模拟系统未到位） | | | |

冒烟运行（2026-09-24，不是正式结果，只证明流水线在真实模型上跑得通）：fixtures 仿标准 36 条、fixtures 查询 15 条，bge-m3 + bge-reranker-v2-m3（CPU）。
hybrid_rerank 的 MRR@10 = 1.00，P95 = 17.6 s；hybrid 的 P95 = 0.85 s；bm25 的 P95 = 0.22 s。样本太小，指标没有参考意义；延迟数字说明 CPU 重排是瓶颈。

T06 手工对比（fixtures，查询“运维人员远程登录服务器能不能用明文协议”）：四种模式首条都是 5.3.2 远程管理通道安全；bm25 25 ms，dense 476 ms，hybrid 249 ms，hybrid_rerank 16.6 s（其中重排 16.2 s）。

## 遗留问题

- （2026-09-25）模型代理返回的模型名（如 gemini-3.8-flash-exp-b-safety）与配置的 LLM_MODEL 不同，llm_call 记录的是返回的名字；配置 PRICE_TABLE 时要按返回的模型名写单价，否则 cost_cny 为空。
- （2026-09-25）判定调用平均 23 s、输出约 1600 tokens/次（高推理档模型），一个 12 条指标的对象约 5 分钟；评测前确认预算与超时（JUDGE_TIMEOUT 默认 90 s）。
- （T00 已解决）`.env` 由 `application-local.yml` / `application-eval.yml` 的 `spring.config.import` 读取；ES 版本组合定为 9.4.5；MyBatis mapper 与 Flyway 脚本已加入，两条 WARN 消失。
- （T01）`/api/health` 的 llm-config 在配置 `LLM_API_KEY` 之前一直是 DOWN，接口整体返回 503，这是预期行为。
- （T01）本机访问 huggingface.co 需要代理，容器内不可达：TEI 模型经宿主机代理预下载到 `tei-data` 卷，`.env` 里用 `TEI_MODEL_ID` 指向卷内快照目录离线启动（TEI 不认 `HF_HUB_OFFLINE`）。
- （T01）ik_smart 对“开展密钥管理工作”切成 密钥 / 管理工作；单独的“密钥管理”是一个词。口语查询的 BM25 召回可能受影响，T07 真实评测后再看是否需要调整词典。
- （T06）CPU 上 TEI 重排 30 条约 16 秒，远超问答可接受的延迟。可选方案：减少重排条数（topN）、GPU、ONNX 量化模型、换小一点的重排模型；需要仓库所有者决定。
- （T09）ofdrw 未引入：题库 OFD 请用 OFD 阅读器导出为文本，再用 `QaBankTool convert` 转换。
- （T09）`com.networknt:json-schema-validator` 经 Spring AI 传递引入并被直接使用，是否在 pom 中显式声明待确认（见 decisions.md）。
- （T12/T15，2026-09-25）按调研结果（docs/crypto_research）改用官方量化评估规则：scoring.v2、algorithms.v2 已由仓库所有者核对（reviewed: true）；以后新增 reviewed: false 的版本时，启动打 WARN、报告附录注明未经核对。高风险项（调研 D08）、双标准重复指标（D05）、应/宜/可与适用性（D06）尚未建模。
- （T15）scoring.v2 的条款映射基于调研目录（GB/T 43206 编号经测评实施指引交叉核对，原文 PDF 未逐页核对）；标准入库后，若原文编号不同或 GB/T 39786 第 6–9 章被标为 要求，确认评分时会返回 409 并列出无法映射的条款。
- （T13，2026-09-25）judge.v2 输出约为 v1 的 4 倍；默认 JUDGE_TIMEOUT 已按仓库所有者的设定调为 240 s（LLM_HTTP_TIMEOUT 540 s、RERANK_TIMEOUT 180 s）。
- （T09）题库评测只用本地模型：qwen2.5:3b 在 CPU 上较慢，准确率反映的是本地小模型的能力；换模型改 EVAL_QA_MODEL 并事先 ollama pull。
- （T13）规则约束依赖模型在 `relevant_algorithms` 中列出相关算法；模型漏列时约束不会触发。规则检查结果本身在提示词里给出，并随差距项落库供复核。
- （T14）要求匹配只取 `clause_type = 要求` 且层面、等级匹配的条款；规范化标准的元数据标注质量直接决定测评指标是否齐全。
- （T10）前端全量引入 Element Plus，打包后主 chunk 超过 500 kB（构建警告），MVP 阶段暂不按需引入。
- （T10 冒烟）本次会话里一条 curl 命令因为 shell 通配符展开，把几条测试请求（内容是 fixtures 文件名和一句示例查询，不含密钥和原文）误发给了以仓库文件名命名的外部域名。已改正命令写法。

- （T00）`LLM_BASE_URL` 默认为空（回退到 OpenAI 官方地址），`LLM_MODEL` 默认 `gpt-5-mini`（与 Spring AI 默认值相同），两者都只是占位。T08 接入真实端点时再确认兼容端点的 base-url 是否需要带 `/v1`。
- （T00）开发机必须装 JDK 21（`openjdk-21-jdk-headless`），只有 JRE 时编译会报 `release version 21 not supported`。T23 写 README 时注明。

## 以后再说

- （任务范围外的想法记在这里，不在当前任务里做）
- compose 的 `app` profile（后端 + 前端 nginx）与 `obs` profile（Langfuse 自托管）尚未提供。
- 检索、问答、评估接口目前不鉴权（本机使用）；若要多人或远程使用，需要统一加认证。
- 前端按需引入 Element Plus，减小打包体积。
- MCP API Key 的轮换与过期。
- 量化评分的高风险项与 D/A/K 测评单元建模（等规则核对后决定）。
