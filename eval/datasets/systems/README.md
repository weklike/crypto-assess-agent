# 模拟被测系统（仓库所有者编写）

每个系统一个文件，文件名 `<系统编号>.v1.yaml`，格式与 `data/fixtures/systems/fixture-system.v1.yaml` 相同：

- `id`、`name`、`level`（1-4）、`scenario`（至少 3 个系统写“电力关基”）、`description`
- `objects`：测评对象列表，`key` 在本系统内唯一；`layer` 取八个安全层面之一；`measures` 与 `POST /api/assessments/{id}/objects` 的格式相同
- `expected`：预期差距项 `{object, clause_ref, judgment}`，judgment 为 不符合 或 部分符合

预期差距项只由仓库所有者填写，CC 不得新增或修改。修正时新建 v2 文件，并在 `eval/datasets/CHANGELOG.md` 说明原因。

运行：`./mvnw spring-boot:run -Dspring-boot.run.profiles=eval -Dspring-boot.run.arguments="--eval.suite=gap --eval.dataset=v1"`
