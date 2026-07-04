# 공연 장소 이미지 업로드 API 명세서

## 1. 개요

공연 장소 이미지는 대량 업로드를 고려해 백엔드 서버를 경유하지 않고 업로드한다.

- 로컬 환경: 백엔드가 발급한 로컬 업로드 URL로 파일을 PUT하면 로컬 폴더에 저장한다.
- 운영 환경: 백엔드가 S3 Presigned URL을 발급하고, 프론트가 S3에 직접 PUT 업로드한다.
- 백엔드 역할: 업로드 세션 생성, 파일 메타데이터 검증, 업로드 URL 발급, 업로드 상태 관리, 처리 요청, 상태 조회, 엑셀 업로드 시 READY 이미지 매칭.

현재 WebP 변환과 실제 이미지 검증은 미구현이다. 현재 process API는 raw 이미지를 processed 위치로 복사하고 READY 상태로 전환한다.

## 2. 공통 정보

### Base URL

```text
Local: http://localhost:8080/api
Prod:  https://unibusk.site/api
```

### Content-Type

```text
JSON API: application/json
엑셀 업로드: multipart/form-data
이미지 PUT: image/jpeg, image/png, image/webp 등 실제 파일 Content-Type
```

### 세션 상태

| 상태 | 설명 |
| --- | --- |
| CREATED | 세션 생성됨 |
| UPLOADING | 업로드 URL 발급 후 업로드 진행 중 |
| UPLOADED | 모든 등록 item이 UPLOADED 또는 UPLOAD_FAILED 도달 |
| PROCESSING | process API 호출로 처리 시작 |
| COMPLETED | 모든 item이 종료 상태 도달 |
| FAILED | 세션 단위 치명적 실패 |
| EXPIRED | 세션 만료 |

### 이미지 item 상태

| 상태 | 설명 |
| --- | --- |
| PENDING | 업로드 URL 발급 전 |
| UPLOADING | 업로드 URL 발급됨 |
| UPLOAD_FAILED | 업로드 실패 |
| UPLOADED | 업로드 완료 |
| PROCESSING | 처리 중 |
| READY | 처리 완료, 엑셀 매칭 가능 |
| VALIDATION_FAILED | 이미지 검증 실패 |
| OPTIMIZE_FAILED | 이미지 처리 실패 |
| CONFIRMED | 엑셀 업로드에서 최종 확정됨 |
| FINALIZE_FAILED | final 복사 실패 |

## 3. 업로드 세션 생성

프론트가 업로드할 이미지 파일 목록의 메타데이터를 등록한다. 이미지 바이트는 보내지 않는다.

```http
POST /performance-locations/image-upload-sessions
```

### Request Body

```json
{
  "files": [
    {
      "originalFileName": "hongdae.jpg",
      "contentType": "image/jpeg",
      "fileSize": 123456
    },
    {
      "originalFileName": "park.png",
      "contentType": "image/png",
      "fileSize": 987654
    }
  ]
}
```

### 검증 정책

| 항목 | 정책 |
| --- | --- |
| 허용 확장자 | jpg, jpeg, png, webp |
| 허용 Content-Type | image/jpeg, image/png, image/webp |
| 단일 파일 크기 | 최대 20MB |
| 요청당 파일 수 | 최대 1,000개 |
| 세션 총 용량 | 최대 100GB |
| 파일명 길이 | 최대 255자 |
| 파일명 중복 | 같은 요청 내 중복 금지, 대소문자 무시 |

### Response

```http
201 Created
```

```json
{
  "uploadSessionId": 1,
  "status": "CREATED",
  "registeredCount": 2,
  "rejectedCount": 0,
  "items": [
    {
      "itemId": 1,
      "originalFileName": "hongdae.jpg",
      "objectKey": "temp/performance-location-imports/1/raw/550e8400-e29b-41d4-a716-446655440000.jpg",
      "status": "PENDING"
    },
    {
      "itemId": 2,
      "originalFileName": "park.png",
      "objectKey": "temp/performance-location-imports/1/raw/550e8400-e29b-41d4-a716-446655440001.png",
      "status": "PENDING"
    }
  ],
  "rejectedFiles": []
}
```

### rejectedFiles 예시

```json
{
  "uploadSessionId": 1,
  "status": "CREATED",
  "registeredCount": 1,
  "rejectedCount": 1,
  "items": [
    {
      "itemId": 1,
      "originalFileName": "hongdae.jpg",
      "objectKey": "temp/performance-location-imports/1/raw/550e8400-e29b-41d4-a716-446655440000.jpg",
      "status": "PENDING"
    }
  ],
  "rejectedFiles": [
    {
      "originalFileName": "memo.txt",
      "reason": "허용하지 않는 확장자입니다."
    }
  ]
}
```

## 4. 업로드 URL 발급

실제 업로드 직전에 필요한 item만 선택해 업로드 URL을 발급한다.

```http
POST /performance-locations/image-upload-sessions/{sessionId}/upload-urls
```

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId | Long | 업로드 세션 ID |

### Request Body

```json
{
  "itemIds": [1, 2, 3]
}
```

### Response

```http
200 OK
```

```json
{
  "uploadSessionId": 1,
  "uploadUrls": [
    {
      "itemId": 1,
      "originalFileName": "hongdae.jpg",
      "objectKey": "temp/performance-location-imports/1/raw/550e8400-e29b-41d4-a716-446655440000.jpg",
      "uploadUrl": "http://localhost:8080/api/performance-locations/image-upload-sessions/1/items/1/local-upload",
      "expiresAt": "2026-07-03T10:30:00",
      "status": "UPLOADING"
    }
  ]
}
```

### 모드별 uploadUrl

| 모드 | uploadUrl |
| --- | --- |
| local | 백엔드 local-upload URL |
| s3 | S3 Presigned PUT URL |

### 재발급 정책

- item별 Presigned URL 발급은 최대 3회까지 가능하다.
- 3회를 초과해 발급 요청된 item은 `UPLOAD_FAILED`로 확정하며, 응답의 `uploadUrls`에는 포함하지 않는다.
- 프론트는 업로드 실패 시 아래 업로드 실패 알림 API를 호출해 item 상태를 `UPLOAD_FAILED`로 기록해야 한다.

## 5. 로컬 테스트 이미지 업로드

로컬 모드 전용 API다. 업로드 URL 발급 API에서 받은 `uploadUrl`로 이미지 바이트를 PUT한다.

```http
PUT /performance-locations/image-upload-sessions/{sessionId}/items/{itemId}/local-upload
```

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId | Long | 업로드 세션 ID |
| itemId | Long | 이미지 item ID |

### Request

```text
Body: image binary
Content-Type: image/jpeg 또는 image/png 또는 image/webp
```

### Response

```http
204 No Content
```

### 로컬 저장 위치

```text
./build/local-image-uploads/temp/performance-location-imports/{sessionId}/raw/{uuid}.{ext}
```

## 6. S3 업로드 완료 알림

S3 모드에서 프론트가 Presigned URL로 S3에 직접 업로드한 뒤, 백엔드에 업로드 완료를 알린다.

로컬 모드에서는 local-upload 성공 시 item이 자동으로 UPLOADED 상태가 되므로 보통 호출하지 않아도 된다.

백엔드는 완료 알림을 그대로 믿지 않고 raw object를 검증한 뒤 UPLOADED 상태로 변경한다.

- S3 모드: S3 HeadObject로 object 존재 여부, Content-Length, Content-Type을 확인한다.
- Local 모드: 로컬 raw 파일 존재 여부와 파일 크기를 확인한다.
- 검증 실패 시 item은 UPLOADED로 변경되지 않는다.
- item 상태가 UPLOADING이면 UPLOADED로 변경한다.
- item 상태가 이미 UPLOADED이면 재시도 요청으로 보고 성공 처리한다.
- PENDING, PROCESSING, READY 등 다른 상태에서는 400 오류로 실패한다.

```http
POST /performance-locations/image-upload-sessions/{sessionId}/uploaded
```

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId | Long | 업로드 세션 ID |

### Request Body

```json
{
  "itemIds": [1, 2, 3]
}
```

### Response

```http
204 No Content
```

## 7. 업로드 실패 알림

프론트가 S3 Presigned URL PUT 업로드에 실패했거나 로컬 업로드 요청이 실패한 경우, 백엔드에 실패 상태를 알린다.

```http
POST /performance-locations/image-upload-sessions/{sessionId}/upload-failed
```

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId | Long | 업로드 세션 ID |

### Request Body

```json
{
  "itemIds": [1, 2, 3],
  "reason": "S3 PUT upload failed"
}
```

`reason`은 생략하거나 빈 값이면 기본 실패 사유로 저장한다. 최대 500자까지만 저장한다.

### 상태 전환 규칙

- PENDING, UPLOADING, UPLOAD_FAILED 상태의 item만 UPLOAD_FAILED로 변경할 수 있다.
- UPLOADED, PROCESSING, READY 등 이미 다음 단계로 넘어간 item은 실패 처리할 수 없으며 400 오류로 실패한다.
- 모든 item이 UPLOADED 또는 UPLOAD_FAILED에 도달하면 세션은 UPLOADED 상태가 될 수 있다.

### Response

```http
204 No Content
```

## 8. 이미지 처리 요청

업로드 완료된 item을 처리 대상으로 넘긴다.

현재 구현은 raw 이미지를 processed 위치로 복사하고 item 상태를 READY로 변경한다. WebP 변환, 이미지 검증, 최적화는 추후 구현 대상이다.

```http
POST /performance-locations/image-upload-sessions/{sessionId}/process
```

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId | Long | 업로드 세션 ID |

### Request Body

```json
{
  "itemIds": [1, 2, 3]
}
```

`itemIds`를 생략하거나 빈 값이면 해당 세션의 UPLOADED item 전체를 처리한다.

동시에 같은 item에 대해 process 요청이 들어오더라도 DB에서 `UPLOADED -> PROCESSING` 조건부 업데이트에 성공한 요청만 실제 처리한다.
이미 PROCESSING, READY, OPTIMIZE_FAILED 등으로 바뀐 item은 중복 처리하지 않고 건너뛴다.

### Response

```http
200 OK
```

```json
{
  "uploadSessionId": 1,
  "status": "READY",
  "totalCount": 3,
  "pendingCount": 0,
  "uploadingCount": 0,
  "uploadedCount": 0,
  "processingCount": 0,
  "readyCount": 3,
  "validationFailedCount": 0,
  "optimizeFailedCount": 0,
  "uploadFailedCount": 0,
  "confirmedCount": 0,
  "finalizeFailedCount": 0
}
```

### 처리 후 저장 위치

Local:

```text
./build/local-image-uploads/temp/performance-location-imports/{sessionId}/processed/{itemId}.{ext}
```

S3:

```text
temp/performance-location-imports/{sessionId}/processed/{itemId}.{ext}
```

## 9. 업로드 세션 상태 조회

업로드/처리 진행률을 조회한다.

```http
GET /performance-locations/image-upload-sessions/{sessionId}
```

### Path Variables

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId | Long | 업로드 세션 ID |

### Response

```http
200 OK
```

```json
{
  "uploadSessionId": 1,
  "status": "PROCESSING",
  "totalCount": 100,
  "pendingCount": 0,
  "uploadingCount": 0,
  "uploadedCount": 20,
  "processingCount": 0,
  "readyCount": 80,
  "validationFailedCount": 0,
  "optimizeFailedCount": 0,
  "uploadFailedCount": 0,
  "confirmedCount": 0,
  "finalizeFailedCount": 0
}
```

## 10. 로컬 이미지 조회

로컬 테스트 모드에서 저장된 이미지를 확인한다.

```http
GET /local-image-files/{objectKey}
```

### 예시

```http
GET /local-image-files/performanceLocations/1.jpg
```

### Response

```http
200 OK
Content-Type: image/*
```

```text
이미지 파일
```

## 11. 엑셀 업로드

공연 장소 엑셀 파일을 업로드한다. 기존처럼 이미지 파일을 함께 보내지 않는다.

백엔드는 `uploadSessionId`에 속한 READY 상태 이미지 중 엑셀의 이미지 파일명 컬럼과 `originalFileName`이 매칭되는 item을 사용한다.
이미지 process가 끝난 세션은 READY 상태이며, 엑셀 업로드에서 모든 READY 이미지가 CONFIRMED 또는 FINALIZE_FAILED 등 최종 상태로 바뀌면 세션은 COMPLETED 상태가 된다.

```http
POST /performance-locations/excels
```

### Request

```text
Content-Type: multipart/form-data
```

### Form Data

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| file | File | Y | `.xlsx` 엑셀 파일 |
| uploadSessionId | Long | Y | 이미지 업로드 세션 ID |

### Response

```http
200 OK
```

```json
{
  "successCount": 10,
  "failCount": 0,
  "failedLogList": []
}
```

### 이미지 매칭 정책

| 항목 | 정책 |
| --- | --- |
| 매칭 기준 | 엑셀 이미지 파일명 컬럼 = ImageUploadItem.originalFileName |
| 매칭 대상 | READY 상태 item만 사용 |
| 매칭 성공 | 장소 DB 저장 커밋 후 processed object를 final object로 복사 |
| 매칭 후 상태 | CONFIRMED |
| 매칭 실패 | 해당 엑셀 row 실패 로그에 기록 |

final copy는 엑셀 row의 DB 저장 트랜잭션이 커밋된 뒤 실행한다.
DB 저장이 실패하면 final object를 만들지 않는다.
final copy 이후 후속 DB 갱신이 실패하면 생성된 final object를 삭제하고 item을 FINALIZE_FAILED로 변경한다.

### final 저장 위치

Local:

```text
./build/local-image-uploads/performanceLocations/{itemId}.{ext}
```

S3:

```text
performanceLocations/{itemId}.{ext}
```

## 12. 프론트 권장 플로우

### Local 모드

1. 사용자가 이미지 폴더를 선택한다.
2. 파일 목록에서 `originalFileName`, `contentType`, `fileSize`를 추출한다.
3. `POST /performance-locations/image-upload-sessions`를 호출한다.
4. 응답의 `items`에서 `itemId`와 파일명을 매핑한다.
5. 10~50개 단위로 `POST /performance-locations/image-upload-sessions/{sessionId}/upload-urls`를 호출한다.
6. 각 응답의 `uploadUrl`로 이미지 바이트를 PUT한다.
7. 배치 업로드가 끝날 때마다 `POST /performance-locations/image-upload-sessions/{sessionId}/process`를 호출한다.
8. `GET /performance-locations/image-upload-sessions/{sessionId}`로 READY 개수를 확인한다.
9. 필요한 이미지가 READY가 되면 `POST /performance-locations/excels`를 호출한다.

### S3 모드

1. 사용자가 이미지 폴더를 선택한다.
2. 파일 목록에서 `originalFileName`, `contentType`, `fileSize`를 추출한다.
3. `POST /performance-locations/image-upload-sessions`를 호출한다.
4. 응답의 `items`에서 `itemId`, `objectKey`, 파일명을 매핑한다.
5. 10~50개 단위로 `POST /performance-locations/image-upload-sessions/{sessionId}/upload-urls`를 호출한다.
6. 각 응답의 S3 Presigned `uploadUrl`로 이미지 바이트를 PUT한다.
7. S3 업로드 성공 후 `POST /performance-locations/image-upload-sessions/{sessionId}/uploaded`를 호출한다.
8. 배치 업로드가 끝날 때마다 `POST /performance-locations/image-upload-sessions/{sessionId}/process`를 호출한다.
9. `GET /performance-locations/image-upload-sessions/{sessionId}`로 READY 개수를 확인한다.
10. 필요한 이미지가 READY가 되면 `POST /performance-locations/excels`를 호출한다.

## 13. 참고 사항

- local 모드에서 `uploaded` API는 필수가 아니다.
- S3 모드에서는 `uploaded` API 호출이 필요하다.
- process API는 멱등하게 사용할 수 있다. 이미 READY인 item은 다시 처리하지 않는다.
- 현재 `process`는 WebP 변환을 수행하지 않는다.
- 실제 이미지 검증, WebP 변환, EXIF 처리, 메타데이터 제거는 추후 구현 대상이다.
