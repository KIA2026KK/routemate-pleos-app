# VIN 차량 계정 및 목적지 등록 API 계약

앱과 백엔드는 같은 계약을 사용한다. 사용자 식별의 비즈니스 키는 VIN이다.
`users.id`는 내부 관계키이며, 한 실제 VIN에는 한 차량 계정을 연결한다. 운전자 개인 신원은 이 모델의 대상이 아니다.
상세 필드 근거와 관계는 [VIN 설계](vin-vehicle-identity.md)를 따른다.

## 설치 세션 발급

`POST /api/v1/auth/device-sessions` — 요청 본문 없음, 응답 `201 Created`, `Cache-Control: no-store`.
기존 `/api/v1/auth/anonymous`는 같은 설치 세션 발급의 호환 경로이다. VIN 연결 없이 운행을 등록할 수는 없다.

```json
{
  "userId": "uuid",
  "accessToken": "opaque-bearer-token",
  "expiresAt": "2026-12-08T00:00:00Z"
}
```

32바이트 무작위 토큰을 발급하고 DB에는 SHA-256 해시만 저장한다. 유효기간은 30일이다.
앱은 정규화된 서버 origin·VIN·조회 출처별로 토큰을 분리하고 Android Keystore로 암호화해 보관한다.
외부 서버 연결에는 HTTPS가 필요하며 로컬 디버그 앱에서만 사설 IP의 HTTP를 허용한다.
세션 만료·손상 시 `401`을 표시하며 다른 사용자로 자동 전환하지 않는다.
VIN은 식별자이며 비밀번호나 소유권 증명이 아니다. VIN 문자열만으로 기존 계정의 세션을 발급하지 않는다.
계정 복구·세션 갱신은 아래 계약을 따른다. OEM 소유권 확인·차량 양도·로그아웃은 후속 범위이다.

## 세션 갱신과 계정 복구

모든 성공 응답은 `Cache-Control: no-store`이다. 아래 API의 차량은 VIN 연결이 완료된 계정의 차량이다.

### 세션 갱신

`POST /api/v1/auth/device-sessions/renew` — 유효한 Bearer 필수, 본문 없음, `200 OK`.

```json
{"vehicleId":"uuid","maskedVin":"*************0001","expiresAt":"2026-12-09T00:00:00Z"}
```

같은 토큰의 만료를 현재 시각부터 30일 이상으로 연장한다. 새 토큰을 반환하지 않으므로 응답 유실 시
기존 토큰으로 재시도할 수 있다. 만료·폐기된 세션은 갱신할 수 없다(`401`).
앱은 목적지 등록 시 VIN 연결 다음에 갱신하며, 계정 관리 화면에서도 수동 갱신한다.

### 복구키 발급·재발급

`POST /api/v1/auth/recovery-keys` — 유효한 Bearer 필수, 본문 없음, `201 Created`.

```json
{"vehicleId":"uuid","maskedVin":"*************0001","recoveryKey":"43-character-base64url-secret","expiresAt":"2027-11-09T00:00:00Z"}
```

32바이트 무작위 복구키를 생성하고 SHA-256 해시만 저장한다. 유효기간은 365일이며 계정당 한 키만
유효하다. 재발급 시 이전 키는 즉시 무효화된다. 원문은 발급 응답에서만 제공하므로 별도 보관한다.
키는 **만료 또는 재발급까지 재사용 가능**하다. 일회용 키가 아니며, 복구 응답 유실 후 재시도를 허용한다.
발급 응답을 잃어버린 경우 유효한 기존 세션으로 재발급한다.

### 새 설치·만료 세션 복구

`POST /api/v1/auth/device-sessions/recover` — Bearer 불필요, `200 OK`.

```json
{"vin":"KNA00000000000001","source":"PLEOS_SDK","recoveryKey":"43-character-base64url-secret"}
```

```json
{"userId":"uuid","vehicleId":"uuid","maskedVin":"*************0001","accessToken":"43-character-base64url-secret","expiresAt":"2026-12-09T00:00:00Z"}
```

현재 VIN·출처와 기존 계정에서 발급받은 복구키가 모두 일치해야 한다. 잘못되었거나 만료된 키,
다른 VIN·출처는 동일한 `401`로 거부한다. VIN 형식·JSON 오류는 `400`이다.
성공 시 기존 사용자·차량·운행 ID를 보존하고 기존 세션을 모두 폐기한 뒤 30일 토큰을 발급한다.
앱은 새 토큰을 해당 서버·VIN·출처의 Keystore 저장소에 보관한다. 응답 유실 시 같은 키로 다시 복구하면
직전에 발급된 세션도 폐기하고 새 토큰을 반환한다.

이 흐름은 기존 계정 접근권의 복구이다. VIN을 안다는 것만으로 로그인하거나 OEM 차량 소유권을
증명하지 않으며 `ownershipVerified: false`는 유지한다. 키까지 잃어버렸을 때의 수동 소유권 확인은 별도 범위다.
Mock 허용 정책은 갱신·키 발급·복구에도 동일하게 적용한다.
Flyway V4는 세션 폐기 시각과 복구키 해시 테이블을 추가한다. 기존 행은 삭제하지 않는다.
계정 행 잠금 후 세션·키를 재검증해 동시 갱신·복구가 폐기된 세션을 되살리지 않도록 한다.

## 현재 차량 VIN 연결

`POST /api/v1/vehicles/me` — 설치 세션의 `Authorization: Bearer <accessToken>` 필수.

```json
{"vin":"KNA00000000000001","source":"PLEOS_SDK","vinAccessType":"READ"}
```

- VIN은 앞뒤 공백 제거·대문자 정규화 후 17자리 영숫자(I/O/Q 제외), 전체 0 값 거부.
- `PLEOS_SDK`는 실제 SDK capability에서 확인한 `READ` 또는 `READ_WRITE`만 허용.
- `MOCK`는 `vinAccessType: null`이며 기본 거부. 개발 서버에서 `ALLOW_MOCK_VEHICLE=true`인 경우만 허용.
- Mock VIN과 실제 VIN은 별도 식별 공간이다. Mock 데이터가 실제 VIN을 선점할 수 없다.
- 동일 세션·동일 VIN의 재연결은 같은 차량 ID를 반환한다.
- 다른 계정이 연결한 동일 VIN, 기존 계정의 다른 VIN 변경은 `409`이며 자동 병합·자동 로그인하지 않는다.
- `source`와 capability는 클라이언트 신고 정보이다. SDK 콜백이 서명된 소유권 증명을 제공하는 것은 아니다.

```json
{
  "vehicleId":"uuid",
  "maskedVin":"*************0001",
  "source":"PLEOS_SDK",
  "vinAccessType":"READ",
  "ownershipVerified":false
}
```

응답은 `200 OK`, `Cache-Control: no-store`이며 원문 VIN·다른 계정 ID·토큰을 반환하지 않는다.

## 목적지 등록 및 갱신

`POST /api/v1/trips`

먼저 현재 세션에 차량을 연결해야 한다. 차량 ID와 사용자 ID는 서버가 세션에서 조회하며 요청 본문에 넣지 않는다.

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
  "vehicleId": "uuid",
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
| 403 | MOCK_VEHICLE_DISABLED | 개발용 Mock 차량이 금지된 서버 |
| 409 | VEHICLE_CONFLICT | VIN이 다른 계정에 연결되었거나 기존 계정의 VIN 변경 시도 |
| 428 | VEHICLE_REQUIRED | VIN 차량 연결 없이 운행 등록 시도 |

앱은 네트워크 오류·시간 초과를 연결 오류로 안내하고, 5xx 응답과 구분해 재시도를 제공한다.
운행 종료·목적지 제거·통화방·음성 토큰은 [MVP 통합 계약](mvp-room-calls.md)을 따른다.
앱은 활성 운행이 있으면 목적지 변경을 서버에 반영하고, 명시적인 목적지 제거 시 운행을 종료한다.
SDK 로딩·조회 오류는 목적지 제거로 간주하지 않는다.
수신이 불확실한 재시도는 같은 사용자의 운행 갱신으로 처리한다.
V3 이전 데이터는 삭제하지 않는다. VIN을 모르는 기존 운행은 자동 연결하지 않으며 `vehicle_id`가 NULL인 이력으로 보존한다.

DB 구현 참고: [PostgreSQL UPSERT](https://www.postgresql.org/docs/17/sql-insert.html),
[PostGIS 좌표 순서](https://postgis.net/docs/ST_MakePoint.html).
