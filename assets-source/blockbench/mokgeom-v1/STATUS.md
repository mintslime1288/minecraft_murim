# 목검 검토본 v1 — 2026-10-17

현재 상태: 검토본 제작 완료 / Blockbench 수동 확인 대기 / 게임 미적용.

- 3,536개 큐브, 최대 42,432개 삼각형(내부 면 포함 상한).
- 칼날, 칼날 밑동, 코등이, 손잡이, 돌출 끈의 5개 편집 그룹.
- 제공 PNG를 바이트 변경 없이 UV 텍스처로 사용. bbmodel에 텍스처 내장.
- 뒷면은 참고 이미지에 없으므로 앞면 재질을 기반으로 구성했음.
- 모든 큐브의 양수 부피, Java 모델 좌표 범위, UV 범위 검사 통과.
- 오프라인 모델 렌더로 연결부를 확인하고 밑동/손잡이와 코등이 사이 틈 보정.
- Blockbench 실제 열기/재수출은 도구의 앱 제어 승인 거부로 수행하지 못함. 미리보기는 Blockbench 캡처나 게임 캡처가 아님.
- 게임 Java/YAML/리소스 파일을 변경하지 않았으며 JAR 설치도 수행하지 않음.

## 확인 방법
Blockbench에서 Ctrl+O를 누르고 mokgeom_reference_v1.bbmodel을 연다.
정면과 측면으로 돌려 칼날 곡률/두께, 코등이와 손잡이 연결, 끈 높낮이를 확인한다.
GUI/손에 든 상태의 표시 변환값은 초안으로, 실제 게임에서 크기와 손 위치를 별도로 맞춰야 한다.
중간 확인 뒤 승인된 모델을 기존 목검(CustomModelData 32001)에 연결한다.

## 파일
- mokgeom_reference_v1.bbmodel: 편집용 모델(텍스처 내장)
- mokgeom_reference_v1.json: Minecraft Java 아이템용 모델 초안
- mokgeom_reference.png: 변경하지 않은 원본 텍스처
- mokgeom_review.png: 오프라인 지오메트리 렌더
- manifest.json: 큐브 수/원본 해시/구조 검사

## 재생성
프로젝트 assets-source/blockbench/mokgeom-v1에서:
python build_model.py --reference review/mokgeom_reference.png
python render_review.py

## 다음 작업 요청
이 검토본을 Blockbench에서 실제로 열어 텍스처와 5개 그룹, 정면/측면 형상을 검증해라.
사용자의 중간 확인을 거친 뒤 Minecraft 26.2의 기존 목검(CustomModelData 32001)에만 연결하고 일반 나무 검은 보존해라.
기존 애니메이션 및 게임 규칙은 바꾸지 마라. Java/YAML 원본과 미커밋 변경을 유지하고, 적용 단계에서 test/build와 게임 리소스 로그 및 손에 든 모습을 구분해 검증해라.
