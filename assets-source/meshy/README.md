# Meshy 원본 자산 보관소

이 폴더는 Meshy에서 생성·다운로드한 원본 GLB와 미리보기, 라이선스 메타데이터를 보관한다. 게임 JAR이나 기존 리소스팩에 자동으로 포함되지 않는다.

생성한 자산마다 아래 파일을 남긴다.

- `model.glb`: 다운로드한 원본
- `preview.png`: Meshy 미리보기(제공되는 경우)
- `asset-metadata.json`: Meshy task ID, SHA-256, 라이선스 확인 상태

`asset-metadata.json`의 `license_status`는 모델을 배포하거나 클라이언트에 넣기 전에 실제 Meshy 요금제와 라이선스 조건으로 검토해야 한다. API 키, 계정 정보, 서명된 다운로드 URL은 이 폴더에 저장하지 않는다.
