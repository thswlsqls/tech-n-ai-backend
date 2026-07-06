# AWS 공식 문서로 확인한 사실

## 검증 완료
- CloudFront 관리형 캐시 정책 CachingOptimized 의 ID 는 `658327ea-f89d-4fab-a63d-7e88639e58f6` 이고 Default TTL 86,400초 — 근거: https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-cache-policies.html (검증일 2026-07-06, 대상: devops/terraform/modules/cloudfront-spa/main.tf:121)
- MSK Provisioned open monitoring 의 Prometheus 스크레이프 포트는 브로커 DNS 의 11001(JMX Exporter)·11002(Node Exporter) — 근거: https://docs.aws.amazon.com/msk/latest/developerguide/set-up-prometheus-host.html (검증일 2026-07-06, 대상: devops/terraform/modules/msk-provisioned/main.tf:90-96, outputs.tf)

## 거짓 양성 (다시 지적하지 말 것)
(없음)

## 미해결 (문서로 아직 판정 못 한 주장 — 다음 실행에서 확인)
- MSK Serverless 는 IAM 인증만 지원한다는 모듈 주석 (modules/msk-serverless/main.tf:2-3) — 탐색 에이전트는 일치로 판단했으나 공식 문서 URL 을 직접 확보하지 못함
- Amplify `_LIVE_UPDATES` 의 pkg `"next-version"` 이 유효한 패키지명인지 (modules/amplify-app/main.tf:131-133)
- ElastiCache AUTH 토큰 "영숫자만" 제약 (modules/elasticache-valkey/main.tf:78) — 코드 동작엔 문제없으나 근거 URL 미확보
- `data.aws_region.current.name` 이 provider 6.x 에서 `region` 으로 deprecated 라는 주장 (modules/ecs-service/main.tf 등) — 현행 핀 5.100 에서는 동작, 6.0 업그레이드 항목과 함께 볼 것
