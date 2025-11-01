# YouTube Multi Chat Viewer (JavaFX)

이 저장소는 두 개의 유튜브 라이브 채팅을 동시에 확인할 수 있는 JavaFX GUI 프로그램입니다.

> **macOS 지원** – Maven 빌드는 Apple Silicon과 Intel 맥을 자동 감지하며, `src/main/resources/MyApp.icns` 아이콘을 사용해 `.app` 번들과 `.dmg` 이미지를 함께 생성합니다. 기본적으로 ad-hoc(로컬) 서명이 적용되므로 "앱이 손상되었습니다" 경고 없이 실행할 수 있습니다.

## 개발 환경 준비
1. **JDK 17 이상 설치** – `java -version`과 `javac -version`으로 확인합니다.
2. **Apache Maven 설치** – `mvn -v`로 확인합니다.
3. (Apple Silicon) `JAVA_HOME`과 PATH를 Apple Silicon용 OpenJDK 17+로 맞춥니다.

## 실행 방법
```bash
mvn clean javafx:run
```

## 패키징 & 배포
### 1. 실행 가능한 JAR 생성
```bash
mvn clean package
```

빌드가 끝나면 `target/yt-multichat-javafx-1.0.0.jar`와 `target/lib/`가 생성됩니다. JavaFX 모듈을 모듈 경로에 올리고 실행하세요.
```bash
java \
  --module-path target/lib \
  --add-modules javafx.controls,javafx.web \
  -jar target/yt-multichat-javafx-1.0.0.jar
```

> `target/lib` 안에 아키텍처별(JavaFX `mac` 또는 `mac-aarch64`) 네이티브 라이브러리가 복사되므로, JAR과 `lib` 폴더를 함께 배포해야 합니다.

### 2. jlink + jpackage 로 macOS 앱 번들 만들기
`mvn clean package` 단계에서 `target/yt-multichat/`에 JavaFX 런타임 이미지가 자동으로 생성됩니다. (macOS에서는 아키텍처를 자동으로 감지합니다.)

런타임 이미지는 즉시 실행할 수 있습니다.
```bash
target/yt-multichat/bin/yt-multichat
```

`.app` 번들과 `.dmg` 이미지는 같은 패키징 단계에서 `target/jpackage/` 아래에 생성됩니다.

* `target/jpackage/TubeMultiView.app` – 로컬 실행 및 서명 대상 번들
* `target/jpackage/TubeMultiView-1.0.0.dmg` – 배포용 디스크 이미지

빌드 직후에는 ad-hoc 서명이 자동으로 적용되어 macOS가 "앱이 수정 또는 손상되었습니다" 같은 보안 경고를 띄우지 않습니다. Apple Developer ID 인증서로 정식 서명을 하려면 다음과 같이 실행하세요.

```bash
mvn -Dmac.sign.identity="Developer ID Application: Your Name (TEAMID)" clean package
```

명시한 인증서는 `codesign --options runtime`으로 `.app` 번들에 적용된 뒤, 같은 번들을 이용해 `.dmg`가 다시 만들어집니다. 추가 배포 형식이 필요하면 `target/jpackage/TubeMultiView.app`를 기반으로 직접 `jpackage`를 재실행하거나 `hdiutil`을 사용할 수 있습니다.

## 종료 시 주의
애플리케이션을 닫을 때는 창 메뉴의 **Quit**(⌘+Q) 또는 창 닫기 버튼을 사용해 정상 종료하세요. 백그라운드 업데이트 스케줄러가 함께 내려가며, Preferences에 저장된 API 키/영상 ID 정보는 유지됩니다.
