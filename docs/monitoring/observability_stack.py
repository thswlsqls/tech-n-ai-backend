"""로컬 관측 스택 다이어그램 생성 스크립트.

생성:  python observability_stack.py  ->  observability_stack.png
사전:  pip install diagrams  +  시스템에 Graphviz(`dot`) 설치

전용 로고 노드가 있는 컴포넌트(Prometheus·Grafana·Loki·Jaeger·Jenkins)는 로고로,
전용 노드가 없는 컴포넌트(Pushgateway·Alertmanager·Promtail·batch-source)는
로고 없는 Blank 노드에 라벨만 달아 표현한다.
"""
from diagrams import Diagram, Cluster, Edge
from diagrams.onprem.monitoring import Prometheus, Grafana
from diagrams.onprem.logging import Loki
from diagrams.onprem.tracing import Jaeger
from diagrams.onprem.ci import Jenkins
from diagrams.programming.framework import Spring
from diagrams.generic.blank import Blank
from diagrams.saas.chat import Slack

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
    pushgw = Blank("Pushgateway")
    alertmgr = Blank("Alertmanager")
    slack = Slack("Slack\ncritical / warning")

    apis >> Edge(label="scrape") >> prom
    jenkins >> Edge(label="scrape") >> prom
    batch >> Edge(label="push") >> pushgw >> Edge(label="scrape") >> prom
    prom >> Edge(label="alert rules") >> alertmgr >> slack

    # 로그 경로
    promtail = Blank("Promtail")
    loki = Loki("Loki")
    apis >> Edge(label="logs", style="dashed") >> promtail >> loki

    # 트레이스 경로
    jaeger = Jaeger("Jaeger")
    apis >> Edge(label="trace", style="dashed") >> jaeger

    # Grafana 통합 (데이터소스)
    grafana = Grafana("Grafana\n통합 대시보드")
    prom >> Edge(label="datasource") >> grafana
    loki >> Edge(label="datasource") >> grafana
    jaeger >> Edge(label="datasource") >> grafana
