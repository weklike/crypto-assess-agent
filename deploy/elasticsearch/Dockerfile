# Elasticsearch + IK 分词插件。compose 与 Testcontainers 共用这一份。
# ES 服务器、IK 插件、Java 客户端（Spring Boot 管理的 elasticsearch-java）三者版本必须一致，见 docs/decisions.md。
ARG ES_VERSION=9.4.5
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}
ARG ES_VERSION
# 插件包的 SHA256 固定下来：下载源被替换或传输损坏时构建直接失败
ARG IK_SHA256=a9f6693759b71310f0b375e370d7da07d9e7a894259c81042b19fcd3a567956c
ARG IK_URL=https://get.infini.cloud/elasticsearch/analysis-ik/${ES_VERSION}

RUN curl -fsSL --retry 3 --max-time 600 -o /tmp/analysis-ik.zip "${IK_URL}" \
 && echo "${IK_SHA256}  /tmp/analysis-ik.zip" | sha256sum -c - \
 && bin/elasticsearch-plugin install --batch file:///tmp/analysis-ik.zip \
 && rm -f /tmp/analysis-ik.zip

# 覆盖插件自带的 IKAnalyzer.cfg.xml，挂上自定义术语词典
COPY --chown=elasticsearch:root analysis-ik/IKAnalyzer.cfg.xml analysis-ik/crypto_assess.dic /usr/share/elasticsearch/config/analysis-ik/
