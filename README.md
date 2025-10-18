# YouTube Multi Chat Viewer (JavaFX)

이 저장소는 두 개의 유튜브 라이브 채팅을 동시에 확인할 수 있는 JavaFX GUI 프로그램입니다.

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
# Intel Mac
mvn clean package

# Apple Silicon (M1/M2)
mvn -Pmac-aarch64 clean package
```

빌드가 끝나면 `target/yt-multichat-javafx-1.0.0.jar`와 `target/lib/`가 생성됩니다. JavaFX는 **반드시 모듈 경로로 로드해야** 하므로, 아래 중 한 가지 방법으로 실행하세요.

#### A. 제공된 스크립트 사용 (권장)
```bash
./scripts/run-packaged.sh
```

스크립트는 `JAVA_HOME`이 설정돼 있으면 해당 `java` 바이너리를, 아니면 PATH상의 `java`를 사용해 자동으로 실행합니다. JAR과 `lib` 폴더가 없으면 친절한 오류 메시지를 출력합니다.

#### B. 명령어 직접 실행
```bash
java \
  --module-path target/lib \
  --add-modules javafx.controls,javafx.web \
  -jar target/yt-multichat-javafx-1.0.0.jar
```

> `target/lib` 안에 아키텍처별(JavaFX `mac` 또는 `mac-aarch64`) 네이티브 라이브러리가 복사되므로, JAR과 `lib` 폴더를 함께 배포해야 합니다. JAR만 단독으로 실행하면 `Error: JavaFX runtime components are missing` 오류가 발생합니다.

### 2. jlink + jpackage 로 macOS 앱 번들 만들기
`mvn clean package` 단계에서 `target/yt-multichat/`에 JavaFX 런타임 이미지가 자동으로 생성됩니다. (Apple Silicon은 `-Pmac-aarch64` 프로필을 추가하세요.)

런타임 이미지는 즉시 실행할 수 있습니다.
```bash
target/yt-multichat/bin/yt-multichat
```

`.app` 이미지가 필요하면 위 런타임을 `jpackage`에 넘깁니다.
```bash
jpackage \
  --type app-image \
  --name TubeMultiView \
  --app-version 1.0.0 \
  --input target \
  --main-jar yt-multichat-javafx-1.0.0.jar \
  --main-class app.Main \
  --runtime-image target/yt-multichat
```

3. 필요하다면 `--type dmg` 또는 `--type pkg` 옵션을 추가해 배포용 이미지를 만듭니다.

Apple Silicon용 `.app`이 필요하면 `mvn -Pmac-aarch64 clean package`로 런타임을 만든 뒤 동일한 `jpackage` 명령을 실행하세요.

## 종료 시 주의
애플리케이션을 닫을 때는 창 메뉴의 **Quit**(⌘+Q) 또는 창 닫기 버튼을 사용해 정상 종료하세요. 백그라운드 업데이트 스케줄러가 함께 내려가며, Preferences에 저장된 API 키/영상 ID 정보는 유지됩니다.
