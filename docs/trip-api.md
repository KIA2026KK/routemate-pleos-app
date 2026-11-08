# 목적지 등록 API 계약

앱과 백엔드는 같은 계약을 사용한다. 사용자 ID를 요청 본문에서 받아 신뢰하지 않는다.

## 익명 세션 발급

`POST /api/v1/auth/anonymous` — 요청 본문 없음, 응답 `201 Created`, `Cache-Control: no-store`.

```json
{
  "userId": "uuid",
  "accessToken": "opaque-bearer-token",
  "expiresAt": "2026-12-08T00:00:00Z"
}
```

32바이트 무작위 토큰을 발급하고 DB에는 SHA-256 해시만 저장한다. 유효기간은 30일이다.
앱은 서버별로 토큰을 분리하고 Android Keystore로 암호화해 보관한다.
외부 서버 연결에는 HTTPS가 필요하며 로컬 디버그 앱에서만 사설 IP의 HTTP를 허용한다.
세션 만료·손상 시 `401`을 표시하며 다른 사용자로 자동 전환하지 않는다.
익명 사용자 복구·세션 갱신·로그아웃 API는 후속 범위이다. 앱 재설치 시 기존 익명 사용자 복구는 지원하지 않는다.

## 목적지 등록 및 갱신

`POST /api/v1/trips`

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "destination": {
    "name": "서울역",
    "latitude": 37.5547,
    "longitude": 126.9706
  }
}
```

- 이름: 필수, 공백 제거 후 1~120자. 입력 이름 자체도 최대 120자.
- 위도: 필수, 유한수, -90~90. 경도: 필수, 유한수, -180~180.
- 알 수 없는 요청 필드와 누락·null·잘못된 JSON은 `400`.
- PostGIS `geography(Point, 4326)`에 경도 X / 위도 Y 순서로 저장.
- 최초 등록과 갱신 모두 `200 OK`, `Cache-Control: no-store`.
- 사용자마다 진행 중인 운행은 하나. 재시도·동시 요청은 중복 운행을 만들지 않는다.
- 목적지를 바꿔 등록하면 동일 운행 ID와 시작 시각을 유지하며 목적지와 갱신 시각을 변경한다.
- 동시 목적지 변경은 마지막으로 DB 갱신된 요청을 반영한다.

```json
{
  "tripId": "uuid",
  "destination": {"name": "서울역", "latitude": 37.5547, "longitude": 126.9706},
  "status": "ACTIVE",
  "startedAt": "2026-11-08T00:00:00Z",
  "updatedAt": "2026-11-08T00:00:00Z"
}
```

## 오류

RFC Problem Detail 형식에 `code`를 추가한다. 요청 값·인증 토큰·DB 오류를 응답에 노출하지 않는다.

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 목적지 입력 또는 JSON 오류 |
| 401 | UNAUTHORIZED | 토큰 누락·위조·만료, `WWW-Authenticate: Bearer` |

앱은 네트워크·시간 초과·5xx 응답을 구분해 재시도를 제공한다.
아직 운행 종료·목적지 취소 전송·통화방 검색·방 참여·음성 토큰 발급은 포함하지 않는다.
앱의 목적지 변경·제거는 미완료 요청과 화면의 기존 등록 상태를 취소하지만 서버 운행을 종료하지 않는다.
수신이 불확실한 재시도는 같은 사용자의 운행 갱신으로 처리한다.

DB 구현 참고: [PostgreSQL UPSERT](https://www.postgresql.org/docs/17/sql-insert.html),
[PostGIS 좌표 순서](https://postgis.net/docs/ST_MakePoint.html).
