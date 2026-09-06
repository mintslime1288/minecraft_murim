# 런처 배포 전 확인

현재 EXE는 내부 테스트용 IExpress 패키지다. 공개 배포 전에 반드시 HTTPS 릴리스 호스트, manifest의 실제 다운로드 URL, 각 릴리스 SHA-256, 바이러스 검사, 코드서명 인증서, 업데이트 정책을 준비한다.

`REPLACE_WITH_HTTPS_RELEASE_URL` 값이 남아 있는 동안 런처는 다운로드를 시도하지 않는다. Microsoft 계정 비밀번호·토큰·RCON 비밀번호를 manifest·로그·배포물에 넣지 않는다.
