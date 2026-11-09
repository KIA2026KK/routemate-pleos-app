# MVP 통화방·음성 통합 계약

앱과 백엔드가 공유하는 계약이다. 차량 VIN·설치 세션·목적지 등록은 [기존 API 계약](trip-api.md)을 따른다.
모든 API는 유효한 Bearer 및 연결된 차량이 필요하며 `Cache-Control: no-store`를 적용한다.
사용자·차량 ID와 방 중심 좌표는 클라이언트가 지정하지 않는다.

## 운행

- `GET /api/v1/trips/me`: 자신의 활성 운행 또는 빈 본문. 운행의 `id`, 자신의 `destination`, `revision` 등을 반환한다.
- `POST /api/v1/trips/me/end`: 본문 없이 종료, `{"ended":true}`. 반복 종료도 성공한다.
- 종료 시 현재 방에서 퇴장하며, 다음 목적지 등록은 새 운행 ID를 만든다.
- 목적지 수정은 기존 `POST /api/v1/trips`로 같은 운행을 갱신한다.
- 목적지 갱신·방 heartbeat 없이 24시간 경과한 운행은 만료한다.

## 방 생성·검색·참여

`POST /api/v1/rooms`

```json
{"name":"서울 방향 동행","isPublic":true,"capacity":4}
```

정원은 2~8명, 방 이름은 공백 제거 후 1~60자이며 입력 자체도 최대 60자이다.
활성 운행이 필요하다. 생성자는 자동 참여하며 중심 목적지는 생성 시점의 자신의 최종 목적지로 고정한다.
이후 목적지를 바꿔도 방 중심을 이동시키지 않는다. 방 유효기간은 생성부터 2시간이다.

```json
{"id":"uuid","name":"서울 방향 동행","isPublic":true,"capacity":4,"participants":1,"expiresAt":"2026-11-09T04:00:00Z"}
```

- `GET /api/v1/rooms/nearby?radiusKm=5`: 자신의 **최종 목적지**와 방 중심 사이의 PostGIS 지리 거리 검색.
  반경은 5 또는 10km. 공개·운영 중·여유 정원이 있는 방만 거리순으로 최대 50개 반환한다.
  응답은 `[{"room": <방 응답>, "distanceMeters":2000}]`이며 정확한 좌표·다른 차량 VIN을 포함하지 않는다.
  공개 거리는 최소 1km, 1km 단위 올림 근삿값이다. 검색 반경과 정렬에는 실제 거리를 사용한다.
  거리 정밀도 축소만으로 반복 검색을 통한 목적지 추정을 완전히 방지하지는 않는다.
- `GET /api/v1/rooms/me`: 현재 참여한 방 또는 빈 본문.
- `POST /api/v1/rooms/{id}/join`: 활성 운행으로 참여. 같은 방 재시도는 같은 참가 상태를 반환한다.
- `POST /api/v1/rooms/{id}/leave`: 본문 없이 퇴장, `{"left":true}`. 이미 퇴장한 요청도 성공한다.
- `POST /api/v1/rooms/{id}/heartbeat`: 참가 상태 갱신 및 최신 방 응답. 앱은 20초 간격으로 호출한다.

한 차량 계정은 동시에 하나의 방만 참여한다. 방 생성 재시도 시 이미 참여한 방의 이름·공개 여부·정원이
일치하면 기존 방을 반환하며, 설정이 다르면 `409 ALREADY_IN_ROOM`이다. 네트워크 응답 유실 후에는
`rooms/me`로 먼저 상태를 확인한다. 비공개 방은 검색되지 않으며, 공유받은 임의 UUID 방 ID로 참여한다.
이는 초대 ID 기반 접근이며 별도의 비밀번호·초대 승인 기능은 없다. 검색 반경은 방 검색 기준이며 초대 ID 참여를 제한하지 않는다.
Mock 차량과 실제 SDK 차량은 서로 방을 검색하거나 참여할 수 없다. 출처가 다른 방 ID도 `ROOM_UNAVAILABLE`로 거부한다.

heartbeat가 90초 동안 없으면 퇴장하고, 마지막 참가자가 나가면 방을 닫는다.
만료 검사는 요청 시점과 5초 간격 작업에서 수행한다. 데이터 행은 이력으로 남긴다.
PostgreSQL 트랜잭션 advisory lock과 참가 UNIQUE 인덱스로 동시 정원 초과·중복 참여를 막는다.
현재 잠금은 작은 MVP의 전체 방 변경을 직렬화한다. 대규모 서비스 전에는 방별 잠금·정리 배치 분리가 필요하다.
상태 변경은 계정 잠금 뒤 인증을 다시 확인해, 대기 중 복구로 폐기된 토큰의 쓰기를 차단한다.

## 음성 권한

`POST /api/v1/voice/token`

```json
{"roomId":"uuid"}
```

```json
{"url":"wss://voice.example","token":"short-lived-participant-jwt","expiresAt":"2026-11-09T02:01:00Z"}
```

현재 방 참가자만 토큰을 받는다. LiveKit 방은 `rmv-<방 UUID>`, participant identity는 참가 이력·설치 세션별
무작위 UUID이다. VIN·계정 원문을 미디어 참가자 이름으로 사용하지 않는다.
HS256 토큰은 60초 동안 입장 가능하며 해당 방의 마이크 발행·구독만 허용한다. 화면 공유·영상·데이터 발행·관리 권한은 없다.
LiveKit 정원도 백엔드 방 정원으로 설정한다. Secret은 서버 환경 변수에만 둔다.

```properties
LIVEKIT_SERVER_URL=https://voice.example
LIVEKIT_PUBLIC_URL=wss://voice.example
LIVEKIT_API_KEY=<server-only>
LIVEKIT_API_SECRET=<server-only>
```

`SERVER_URL`은 백엔드 관리 API 주소, `PUBLIC_URL`은 앱의 시그널링 주소다.
미설정·LiveKit 장애 시 토큰 API는 `503 VOICE_UNAVAILABLE`이며 임의 로컬 토큰으로 대체하지 않는다.
전용 LiveKit 인스턴스에서 `rmv-` 방들을 관리한다. 다른 서비스와 같은 접두어를 공유하지 않는다.

셀프호스팅 LiveKit에서 JWT 만료는 기존 연결의 종료를 보장하지 않는다.
백엔드는 5초 간격으로 실제 참가자와 유효한 DB 참가·설치 세션을 대조해 퇴장·만료·복구로 폐기된 연결을 제거하고,
허용 참가자가 없는 방을 삭제한다. 관리 API 장애 시 DB 만료는 유지하고 미디어 정리를 재시도한다.
따라서 강제 종료는 정상 관리 연결에서 주기·처리 시간만큼 지연될 수 있고, 서버 장애 중 즉시 차단을 보장하지 않는다.
이전 입장 토큰은 만료 전 재접속을 시도할 수 있으며, 재접속해도 다음 정리 시 제거된다.
토큰 준비와 최종 참가 권한 확인은 짧은 트랜잭션으로 나눈다. LiveKit 방 생성·목록 조회는 전역 잠금 밖에서
수행한다. 방 삭제·참가자 제거는 오래된 조회 결과로 새 참가자를 제거하지 않도록 직전에 DB 권한을 재확인한다.
이 파괴적 관리 요청 한 건 동안에는 잠금을 유지하며 HTTP 제한은 5초다. 음성 서버 지연이 방 변경을 잠시
막을 수 있으므로, 운영 규모 확장 전에는 방별 잠금·별도 작업 큐를 검토한다.

## 오류

| HTTP | code | 처리 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 방 설정·UUID·반경·JSON 확인 |
| 401 | UNAUTHORIZED | 세션 갱신/복구, 익명 계정 자동 대체 금지 |
| 403 | NOT_IN_ROOM | 참가 상태 재확인 |
| 404 | ROOM_UNAVAILABLE | 종료·만료된 방 재검색 |
| 409 | ROOM_FULL / ALREADY_IN_ROOM | 정원 및 기존 방 확인 |
| 409 | TRIP_CHANGED | 목적지 갱신 충돌, 최신 상태 확인 |
| 428 | TRIP_REQUIRED | 목적지 등록 필요 |
| 503 | VOICE_UNAVAILABLE | 음성 서버 확인 후 재시도 |

차량 연결·Mock 금지 오류는 기존 계약을 유지한다. 인증이 필요한 API가 실패하면 토큰·VIN·DB 오류 원문을 노출하지 않는다.

## 앱 동작

목적지 화면에서 방 검색·생성·초대 ID 참여·현재 방·참가 인원·음성 연결·마이크 토글·퇴장·운행 종료를 제공한다.
음성 연결 전 마이크 권한을 요청하며 최초 입장은 음소거다. LiveKit 재접속 상태와 HTTP 오류·재시도를 표시한다.
서버 상태 확인으로 응답 유실 후 참가 상태를 복구한다. 다른 요청 중 목적지가 바뀌면 처리 후 최신 목적지를 반영한다.
명시적인 목적지 제거 시 활성 운행을 종료한다. SDK 조회 오류·로딩은 목적지 제거로 간주하지 않는다.
화면 이탈 시 마이크·SDK 연결을 정리하고 서버 퇴장을 시도한다. 전송 불가 시 90초 참가 만료가 보완한다.
이 MVP는 화면이 보일 때 통화하며 백그라운드 음성 서비스는 포함하지 않는다.

## 근거

- [LiveKit RoomService API](https://docs.livekit.io/reference/other/roomservice-api/)
- [LiveKit 토큰·권한과 셀프호스팅 제약](https://docs.livekit.io/frontends/reference/tokens-grants/)
- [공식 RoomService 프로토콜](https://github.com/livekit/protocol/blob/main/protobufs/livekit_room.proto)
