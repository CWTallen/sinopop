# 华语志 (sinopop-archive)

收集、整理、检索华语歌坛专辑与曲目信息的个人项目。单机 VPS 部署，Java Spring Boot + PostgreSQL + Angular + Docker + Nginx。

## 项目定位

个人技术作品集项目，不是对外运营的商业产品。目标是完整展示一套从数据模型、后端服务、前端到部署运维的工程实现，规模按"几百位华语歌手"设计，不追求真实商用的数据完整度。

## 技术栈

按单机 VPS 场景做过一次取舍——完整的多云/多集群/微服务版本对这个数据规模是过度设计，实际落地保留的是：

| 层 | 技术 | 角色 |
|---|---|---|
| 后端 | Java Spring Boot | 模块化单体：Catalog / Lyrics / User 按包隔离，对外统一 REST |
| 前端 | Angular + TypeScript | 公开浏览站 + 管理员后台 CMS |
| 数据库 | PostgreSQL | 唯一数据库，歌手/专辑/曲目/账号全部在里面，歌词多语言版本用 `JSONB`/独立表处理 |
| 认证 | JWT（自签发） | 管理员登录用，公开浏览接口不需要认证 |
| 容器化 | Docker + docker-compose | 单节点编排，替代 K8s/Helm |
| 反向代理 | Nginx | 反代 + 静态资源 + TLS |
| CI/CD | GitHub Actions | build → test → 打镜像 → 推 GHCR → SSH 部署到 VPS |
| 监控（可选） | Prometheus + Grafana | 轻量单实例，走 `--profile monitoring` 按需启动 |
| 抓取（可选） | 独立 gRPC 服务 | 唯一保留 gRPC 的场景——周期性抓取任务，跟主服务解耦 |

被去掉的部分（Kubernetes/Helm、AWS+Azure 双云、MongoDB、GitLab+GitHub+Jenkins 三线并行、Azure Entra ID、自建 SonarQube）在这个数据规模下没有对应的真实需求，详见下方"架构取舍"。

## 架构取舍

最初设计过一版对齐求职技术栈的完整企业级架构（多云 K8s 集群、微服务 + gRPC、SonarQube/Prometheus/Grafana 全链路监控）。但按几百歌手 / 几万首曲目的真实数据量估算：

- 歌词文本总量约 100~200MB，封面图约 1~2.5GB，总数据量在 5GB 以内
- 真实访问并发大概率是个位数到几十，不存在需要横向扩容的负载

单机 2 vCPU / 4GB 内存的 VPS 即可稳定支撑（Spring Boot 单体约 350~500MB，PostgreSQL 约 200~400MB，Nginx 约 10~20MB，可选监控约 250~400MB）。因此最终架构改为单机模块化单体 + docker-compose，只在"周期性数据抓取"这一个天然需要解耦的场景保留独立服务 + gRPC。

## 目录结构

```
docker-compose.yml
.env.example
app/                    # Spring Boot 单体，Dockerfile 在这里
frontend/
  dist/                 # ng build 产物，由 nginx 挂载托管
nginx/
  conf.d/                # nginx location 配置
  certs/                 # Let's Encrypt 证书（certbot 单独申请/续期）
ingestion/              # 可选，周期性抓取服务，配合宿主机 crontab 触发
monitoring/
  prometheus.yml
seed-data/
  sun-yanzi.json        # 种子数据示例（见下）
```

## 部署

```bash
cp .env.example .env
# 填 POSTGRES_PASSWORD / JWT_SECRET（openssl rand -base64 48 生成）/ GRAFANA_ADMIN_PASSWORD

docker compose up -d                        # 只启动核心三件套：postgres + app + nginx
docker compose --profile monitoring up -d   # 额外带上 prometheus + grafana
docker compose run --rm ingestion           # 手动触发一次抓取（配合 crontab 定时跑，不常驻）
```

`postgres`/`prometheus`/`grafana` 只绑定 `127.0.0.1`，不对公网暴露；本地需要连库时用 SSH 隧道转发对应端口。

## 种子数据

`seed-data/sun-yanzi.json` 是一份示例种子数据：孙燕姿 2000~2017 年全部 11 张录音室专辑、60 首曲目的元数据（歌手/专辑/曲目名/发行年份/曲序），对应 Artist / Album / Song 三张表，字段和引用关系（`artist_id`/`album_id`）已用脚本校验过完整性。专辑与曲目名核对自维基百科专辑词条、豆瓣音乐等公开资料，交叉核实过。

## 歌词版权政策

**歌词正文不收录、不抓取、不展示**——这是产品设计上的硬性约束，不是临时的技术限制。原因很直接：歌词是词作者/音乐出版方持有版权的作品，未经授权大规模复制展示是版权问题，跟数据规模、是否商用无关，片段引用也不例外。

种子数据里每首曲目都带 `lyrics_status: "not_included_copyright"` 字段，明确标注这一点。产品里"歌词"相关的功能有两种可行方向，二选一或都做：

1. **外链模式**：数据库只存曲目名，页面提供跳转到网易云音乐 / QQ音乐 / Musixmatch 等授权平台的链接，完整歌词由用户自己在授权平台上查看
2. **原创描述模式**：给每首曲目配一段自己写的主题/意象文字（不是对原文的转述或改写，是独立原创内容），比如围绕歌曲的情绪、创作背景做简短介绍——这部分内容完全归项目自己所有，不涉及版权问题

如果未来想做成真正对外运营的产品并展示完整歌词，需要单独走 Musixmatch 等平台的商业授权或用户投稿+版权投诉下架机制，这是产品化阶段的决策，不影响当前架构和数据模型的设计。

## 开发路线

1. **打通闭环**：4 张核心表（Artist/Album/Song/Lyrics-metadata）+ 3 个只读接口 + 2 个前端页面，本地跑通后部署上线
2. **内容管理**：管理员登录（JWT）+ 曲目/专辑维护接口和后台表单
3. **检索体验**：PostgreSQL 全文索引做歌手/曲目搜索、分页排序
4. **工程化收尾**：GitHub Actions CI/CD、SonarCloud 质量检查、Prometheus+Grafana 监控——放在系统有实际内容之后，不是起点
5. **规模化（可选）**：Ingestion/ETL 抓取服务批量补充数据，Android 客户端复用现成 REST API
