# 강호이문록 플레이어 동작 영상

18초, 24 fps, 960×540 콘셉트 영상: [murim-player-motion-concept.mp4](../../../docs/media/murim-player-motion-concept.mp4)

| 시간 | 장면 |
| --- | --- |
| 0–3초 | 대기 자세 |
| 3–6초 | 걷기 |
| 6–9초 | 달리기 |
| 9–12초 | 점프 |
| 12–15초 | 웅크리기 |
| 15–18초 | 맨손 타격 |

프로젝트의 편집 가능한 `murim_player_rig_v1_editable.blend`를 읽고 영상용
외형을 구성한다. 팔과 다리는 팔꿈치/무릎을 가로지르는 단일 사각 단면 메시이며,
인접 본의 가중치를 섞어서 움직일 때 면이 끊어지지 않는다. 원본 Blender 파일,
게임 애니메이션 JSON, 스킨, 서버 파일은 변경하지 않는다.

이 결과물은 **영상 콘셉트**다. 실제 Minecraft 플레이어 렌더러에 적용된 모델이나
게임 동작 검증 결과가 아니다. 영상용 옷과 얼굴도 현재 계정 스킨을 뜻하지 않는다.

## 다시 만들기

Windows PowerShell에서 Blender 5.2+ 및 FFmpeg 실행 파일 경로를 넘긴다.

```powershell
.\assets-source\blender\murim-motion-showcase\make_video.ps1 `
  -Blender 'C:\path\to\blender.exe' `
  -Ffmpeg 'C:\path\to\ffmpeg.exe' `
  -Output (Join-Path (Get-Location) 'docs\media\murim-player-motion-concept.mp4')
```

렌더 프레임과 오버레이는 기본적으로 임시 폴더에 생성된다.
