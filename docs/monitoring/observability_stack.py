"""로컬 관측 스택 다이어그램 생성 스크립트.

생성:  python observability_stack.py  ->  observability_stack.png
사전:  pip install diagrams  +  시스템에 Graphviz(`dot`) 설치

로고 사용 원칙:
- 전용 공식 로고가 있는 컴포넌트(Prometheus·Grafana·Loki·Jaeger·Jenkins·Slack)는 해당 로고.
- OpenTelemetry는 diagrams 내장 노드가 없어 공식 로고 PNG를 Custom 노드로 사용.
- Pushgateway·Alertmanager는 독립 로고가 없는 Prometheus 컴포넌트라 Prometheus 로고를 공유한다.
- Promtail은 독립 로고가 없는 Loki 컴포넌트라 Loki 로고를 공유한다.
- batch-source는 제품이 아니므로 로고 없는 Blank 노드로 둔다.
"""
from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.monitoring import Prometheus, Grafana
from diagrams.onprem.logging import Loki
from diagrams.onprem.tracing import Jaeger
from diagrams.onprem.ci import Jenkins
from diagrams.programming.framework import Spring
from diagrams.generic.blank import Blank
from diagrams.saas.chat import Slack
from diagrams.custom import Custom

# 공식 OpenTelemetry 로고 (CNCF artwork: projects/opentelemetry/icon/color)
OTEL_LOGO = "assets/opentelemetry-icon-color.png"

graph_attr = {"fontsize": "20", "bgcolor": "white"}

with Diagram(
    "Tech-N-AI 로컬 관측 스택",
    filename="observability_stack",
    direction="LR",
    show=False,
    graph_attr=graph_attr,
):
    with Cluster("관측 대상 (호스트)"):
        apis = Spring("Spring Boot API 6종\n8081~8086")
        jenkins = Jenkins("Jenkins 8080")
        batch = Blank("batch-source")

    # 메트릭 경로
    prom = Prometheus("Prometheus")
    pushgw = Prometheus("Pushgateway")        # 독립 로고 없음 → Prometheus 로고 공유
    alertmgr = Prometheus("Alertmanager")     # 독립 로고 없음 → Prometheus 로고 공유
    slack = Slack("Slack\ncritical / warning")

    apis >> Edge(label="scrape /actuator/prometheus") >> prom
    jenkins >> Edge(label="scrape /prometheus/") >> prom
    batch >> Edge(label="push") >> pushgw >> Edge(label="scrape") >> prom
    prom >> Edge(label="alert rules") >> alertmgr >> slack

    # 로그 경로
    promtail = Loki("Promtail")               # 독립 로고 없음 → Loki 로고 공유
    loki = Loki("Loki")
    apis >> Edge(label="logs", style="dashed") >> promtail >> loki

    # 트레이스 경로 (OpenTelemetry / OTLP)
    otel = Custom("OpenTelemetry\n(micrometer-tracing-bridge-otel)", OTEL_LOGO)
    jaeger = Jaeger("Jaeger v2\n(OTLP 수신)")
    apis >> Edge(label="trace", style="dashed") >> otel
    otel >> Edge(label="OTLP gRPC 4317 / HTTP 4318", style="dashed") >> jaeger

    # Grafana 통합 (데이터소스)
    grafana = Grafana("Grafana\n통합 대시보드")
    prom >> Edge(label="datasource") >> grafana
    loki >> Edge(label="datasource") >> grafana
    jaeger >> Edge(label="datasource") >> grafana
