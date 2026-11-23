# 앱 아이콘

현재 아이콘은 **아레나 티켓 V** 시안이다. 어두운 배경 위에 기울어진 적색 종이 티켓이 있고, 오른쪽에는 검정 막대 두 개가 있는 크림색 반권이 붙어 있다. 티켓 기울기에 맞춘 검정 V는 긴 획이 오른쪽에 있다. 2026-11-23 사용자가 최종 승인한 시안을 그대로 쓰며, 그림을 다시 그리거나 색·형태를 바꾸지 않는다. 생성기는 리샘플링, 크롭·패딩, 마스크, 배경 분리(matte), 색공간 태그만 한다.

런처 이름은 `VLR.GG Mobile`이며 Play 제목과 About의 전체 이름은 `VLR.GG Mobile (Unofficial)`이다. 이전 선택 기록인 스코어 윈도 시안과 [`candidates.svg`](candidates.svg)는 남겨 두었지만 생성 입력이 아니다.

## 원본

- `app-icon-source.png`: 1254×1254, 8-bit RGB PNG, 알파 없음, ICC profile 없음(sRGB로 해석).
- SHA-256 `239fabcbc510363bc1eb14c5f7d690ce78ec44e1784f69b82f87a4ab8bdcf186`. 생성기가 크기와 SHA가 다르면 중단한다.

## 산출물

모든 PNG는 sRGB(`sRGB IEC61966-2.1`)로 태그한다.

| 경로 | 크기 | 포맷 | 내용 |
| --- | --- | --- | --- |
| `app/androidApp/src/main/res/values/ic_launcher_background.xml` | – | color `#1D1A18` | adaptive 배경과 스플래시 아이콘 배경 |
| `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png` | 108/162/216/324/432px (108dp) | RGBA | 투명 배경 위 티켓 cut-out |
| `res/mipmap-*/ic_launcher_monochrome.png` | 108dp, 위와 같음 | RGBA(흰색 + 알파) | 종이(적색·크림) 실루엣. V와 반권 막대는 투명 구멍 |
| `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` | – | adaptive icon | 위 background·foreground·monochrome 참조 |
| `res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png` | 48/72/96/144/192px (48dp) | RGBA | 배경+foreground 합성의 중앙 72dp를 22% 라운드 사각·원으로 자른 legacy 아이콘 |
| `res/drawable-{mdpi,…,xxxhdpi}/ic_splash.png` | 240/360/480/720/960px (240dp) | RGBA | foreground와 같은 108단위 구도를 240dp로 확대한 스플래시 아이콘 |
| `app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png` | 1024×1024 | RGB, 알파 없음 | 원본 정사각형 구도 그대로(full-bleed). iOS가 마스크를 적용하므로 사전 라운딩 없음. 질감 때문에 약 1.3MB |
| `play-icon-512.png`, `fastlane/metadata/android/ko-KR/images/icon.png` | 512×512 | RGBA 32-bit, 1MB 미만 | full-bleed. 두 파일은 바이트가 같다. Play가 마스크를 적용하므로 사전 라운딩·그림자 없음 |
| `app/shared/src/commonMain/composeResources/drawable/app_icon.png` | 224×224 (56dp × 4) | RGBA | About 화면 full-bleed 아이콘. 화면에서 12dp 라운드로 자른다 |

iOS `Contents.json`의 dark·tinted 슬롯은 비워 두어 시스템이 파생한다.

## 생성 방식과 측정값

- **배경색:** 원본 가장자리 24px 띠의 채널별 중앙값 `#1D1A18`.
- **matte:** 배경색과의 RGB 거리가 56 이하인 픽셀을 이미지 테두리에서 flood fill해 바깥으로 본다. 닿지 않은 영역(검정 V, 반권 막대 포함)은 불투명이다. 좌우 반원 노치는 바깥과 이어져 투명하다. 테두리 안쪽·바깥쪽 4px 띠에서만 가장 가까운 종이 색과 배경색으로 알파를 풀고(C = aF + (1−a)B), 경계 색에서 배경을 걷어내(un-premultiply) 어두운 테가 남지 않게 한다. 떨어져 있는 몇 픽셀짜리 점은 버린다.
- **safe zone:** 알파 0.1 이상 matte의 원본 중심 기준 최대 반지름은 608.5px이다. 이를 108dp adaptive 캔버스의 32.3dp에 맞춰 원본 1px = 0.05309dp(원본 전체 66.57dp)로 배치하고 원본 중심을 캔버스 중심에 둔다. 생성기는 모든 density의 최종 foreground·monochrome PNG에서 알파 0.1 이상 픽셀의 가장 먼 모서리를 다시 재고 66dp safe zone(반지름 33dp)을 넘으면 중단한다. 리샘플링 번짐과 픽셀 격자를 포함한 측정값은 mdpi 32.80, hdpi 32.69, xhdpi 32.41, xxhdpi 32.48, xxxhdpi 32.37dp다(foreground·monochrome 같음). 32.5dp로 배치하면 mdpi가 33.42dp가 되어 32.3dp로 줄였다.
- **스플래시:** 티켓 반지름은 `32.3 × 240 / 108 = 71.8dp`이고, 모든 density의 최종 PNG에서 잰 값은 최대 72.05dp로 160dp 원(반지름 80dp) 안에 들어간다. 생성기가 density마다 80dp 이하를 검사한다. 창 배경은 DESIGN.md Surface와 같은 흰색이고, 아이콘 배경(`windowSplashScreenIconBackgroundColor`)은 런처와 같은 `@color/ic_launcher_background`다. 원본의 어두운 바탕 위 티켓 구도를 그대로 보여 주기 위해서다.
- **full-bleed 마스크:** 알파 0.1 이상 티켓 픽셀은 iOS squircle 근사(superellipse n=5), Play 30% 라운드 사각(Play 규격의 마스크 반경), 22.37% 라운드 사각 모두의 안쪽에 있다. 가장 가까운 여유는 반변 기준 0.097(512px에서 약 25px)이라 배경 패딩은 넣지 않았다.
- **리샘플링:** premultiplied RGBA에 separable Lanczos-3를 적용한다. legacy 아이콘 마스크는 8×8 supersampling이다. 결과는 결정적이라 두 번 실행해도 바이트가 같다.
- **색 확인:** 쓴 iOS PNG를 다시 디코드해 메모리의 바이트와 같은지, 배경 픽셀이 대응하는 원본 픽셀 평균과 ±3 이내인지 확인한다.

## 다시 생성하기와 검증

macOS Swift(AppKit·CoreGraphics·ImageIO·CryptoKit)만 사용한다. 앱 dependency 추가는 없다. 실행에 약 1분 걸린다.

```sh
swift docs/design/app-icon/export.swift
sips -g pixelWidth -g pixelHeight -g hasAlpha -g space app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png docs/design/app-icon/play-icon-512.png
cmp docs/design/app-icon/play-icon-512.png fastlane/metadata/android/ko-KR/images/icon.png
app/iosApp/Scripts/test_release_config.sh
./gradlew :app:androidApp:assembleDebug :app:androidApp:lintDebug
```

생성기는 원본 SHA·크기, density별 safe zone·스플래시 원, 마스크 여유, Play 1MB, 색 왕복을 precondition으로 검사한다. 시안 비교 파일과 에뮬레이터·contact sheet 캡처는 저장소에 포함하지 않는다.

규격 근거: [Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive), [Splash screen dimensions](https://developer.android.com/develop/ui/views/launch/splash-screen#splash_screen_dimensions), [Google Play icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
