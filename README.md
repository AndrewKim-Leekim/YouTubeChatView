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

빌드가 끝나면 `target/yt-multichat-javafx-1.0.0.jar`와 `target/lib/`가 생성됩니다. 동일한 디렉터리에서 아래 명령으로 실행합니다.
```bash
java -jar target/yt-multichat-javafx-1.0.0.jar
```

> `target/lib` 안에 JavaFX 네이티브 라이브러리가 함께 복사되므로, JAR과 `lib` 폴더를 같은 위치에 둔 채 배포하세요.

### 2. jlink + jpackage 로 macOS 앱 번들 만들기
1. 런타임 이미지 생성
   ```bash
   # Intel Mac
   mvn clean javafx:jlink

   # Apple Silicon
   mvn -Pmac-aarch64 clean javafx:jlink
   ```
   실행 후 `target/image/`에 JavaFX 런타임이 만들어집니다.

2. `.app` 이미지 생성
   ```bash
   jpackage \
     --type app-image \
     --name TubeMultiView \
     --app-version 1.0.0 \
     --input target \
     --main-jar yt-multichat-javafx-1.0.0.jar \
     --main-class app.Main \
     --runtime-image target/image
   ```

3. 필요하다면 `--type dmg` 또는 `--type pkg` 옵션을 추가해 배포용 이미지를 만듭니다.

Apple Silicon용 `.app`이 필요하면 `mvn -Pmac-aarch64 clean javafx:jlink`로 런타임을 만든 뒤 동일한 `jpackage` 명령을 실행하세요.

## 종료 시 주의
애플리케이션을 닫을 때는 창 메뉴의 **Quit**(⌘+Q) 또는 창 닫기 버튼을 사용해 정상 종료하세요. 백그라운드 업데이트 스케줄러가 함께 내려가며, Preferences에 저장된 API 키/영상 ID 정보는 유지됩니다.
