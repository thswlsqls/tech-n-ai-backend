# 004 - Agent 메시지 조회 시 페이지네이션 size 불일치로 빈 결과 반환

## 기본 정보
- **발생일**: 2026-03-20
- **모듈**: `admin` (프론트엔드), `api/agent`, `common/conversation`
- **심각도**: High (세션 대화 내용 조회 완전 불가)
- **상태**: 해결 완료

## 증상
- Admin 페이지(`/agent`)에서 세션 클릭 시 대화 내용이 표시되지 않음
- 백엔드 로그에서 첫 번째 요청(probe)은 MongoDB에서 메시지를 찾지만, 두 번째 요청(actual)은 MongoDB에서 못 찾고 Aurora로 fallback
- Aurora fallback에서도 offset 초과로 빈 결과 반환

## 근본 원인

### 프론트엔드 Two-Request 전략의 page size 불일치

프론트엔드는 최신 메시지를 먼저 보여주기 위해 2단계 요청 전략을 사용:

1. **Probe 요청**: `page=1, size=1` → `totalPageNumber` 획득
2. **Actual 요청**: `page=totalPageNumber, size=50` → 실제 메시지 로드

문제: `totalPageNumber`은 `size=1` 기준으로 계산된 값. 메시지가 2개일 때:
- Probe(`size=1`): `totalPageNumber = 2` (2개 메시지 / 페이지당 1개 = 2페이지)
- Actual(`size=50`): `page=2`로 요청 → `Pageable(page=1, size=50)` → offset=50

offset 50에서 시작하지만 메시지는 2개뿐이므로 모든 데이터소스에서 빈 결과 반환.

### 백엔드 로그 증거
```
Fetching messages: page=0, size=1 → Found 2 messages from MongoDB
Fetching messages: page=1, size=50 → No messages found in MongoDB, falling back to Aurora
                                   → Found 2 messages from Aurora (전체 조회)
                                   → But offset(50) >= size(2) → empty PageImpl returned
```

## 수정 내용

### 파일: `admin/src/app/agent/page.tsx`

```typescript
// Before: probe의 totalPageNumber을 그대로 사용 (size=1 기준)
targetPage = meta.data.totalPageNumber;

// After: totalSize와 실제 PAGE_SIZE로 마지막 페이지 재계산
const PAGE_SIZE = 50;
targetPage = Math.max(1, Math.ceil(meta.data.totalSize / PAGE_SIZE));
```

`totalSize`(전체 메시지 수)는 page size에 무관한 절대값이므로, 실제 사용할 `PAGE_SIZE`로 나누어 올바른 마지막 페이지 번호를 계산.

## 교훈
- 페이지네이션 probe 요청과 actual 요청의 page size가 다를 경우, `totalPageNumber`은 직접 사용할 수 없음
- `totalSize`(전체 건수)를 기반으로 실제 page size에 맞게 재계산해야 함
