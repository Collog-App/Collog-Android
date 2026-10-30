# iOS → Android 포팅 규칙

**2026-10-30부터 화면 기준은 팀 피그마의 「최종」 페이지 → 「최종본」 섹션(노드 `269:3887`)이다.**
피그마와 다른 곳은 피그마에 맞춘다(로그인·가입 흐름 포함). 앱 이름은 「콜록」을 쓴다(피그마의 「콜로그」·「렌토」는 「콜록」으로).
피그마에 없는 화면·동작은 아래 iOS 규칙을 그대로 따른다. 바꾼 화면은 KDoc에 피그마 노드 번호를 적는다.

그 전까지 정본은 iOS 앱이었다: [Collog-iOS](https://github.com/Collog-App/Collog-iOS)의 `collog-ios/` (`main` f738313).
동작과 화면을 그대로 옮긴다. 다시 설계하지 않는다. 문구·색·간격·순서·조건 분기를 iOS와 같게 둔다.

## 이미 있는 기반 (고치지 말고 가져다 쓴다)

| iOS | Android |
|---|---|
| `AppEnvironment` (`@Environment(AppEnvironment.self)`) | `LocalAppEnvironment.current` (`config/AppEnvironment.kt`) |
| `AppSettings`, `AuthSession`, `FamilyStore` | 같은 이름 (`config/`, `shared/services/`) |
| `CollogAPI`, DTO 전부 | `shared/services/api/` (`CollogApi`, `ApiModels.kt`, `ApiReportModels.kt`). Swift `XxxDTO` → Kotlin `XxxDto` |
| `APIError` | `ApiError` (sealed). `error.localizedDescription` → `error.message` |
| `Date.fromCollogTimestamp` | `CollogTimestamp.parse` |
| `TabManager`, `NavigationStore`, `NavigationManager`, `Route`, `MainTab` | `shared/navigation/Navigation.kt`, `LocalTabManager`, `LocalNavigationStore`, `LocalNavigationManager` |
| `HealthCondition`, `UserRoleOption`, `PreviewQuestion`, `FamilyContact` | 같은 이름 (`shared/models/`, `features/home/models/`). enum은 `UserRoleOption.Parent.rawValue` 식 |
| `Color.gray900` 등 | `CollogColors.gray900` |
| `.headline_02(.gray900)` 등 | `Text(t, style = CollogType.headline02, color = CollogColors.gray900)` (`body_02_semibold` → `body02Semibold`) |
| `.pretendard(.semiBold, 15, c)` / `pretendardStyle(...)` | `pretendard(Pretendard.SemiBold, 15f)` / `pretendardPlain(...)` |
| `Spacing`, `Radius`, `IconSize` | 같은 이름 (Dp) |
| `.cardSurface()`, `DividerLine` | `Modifier.cardSurface()`, `DividerLine()` |
| `Icon(name: "phone.fill", size:, color:)`, `Image(systemName:)` | `CollogIcon("phone.fill", size = , color = )`, `sfSymbol(name)`. iOS가 쓰는 SF Symbol은 모두 대응돼 있다 |
| `Haptics.press()` 등 | `Haptics.press()` (같은 이름) |
| `Button {} .buttonStyle(.plain)` | `Modifier.pressable { }` (물결 없음, 누르면 흐려짐) |
| `Picker(.segmented)`, `Toggle`, `ProgressView()`, `ShareLink` | `SegmentedPicker`, `CollogToggle`, `CollogProgress`, `shareText(context, text)` (`shared/ui/Controls.kt`) |
| `.onChange(of: x) { old, new in }` | `OnChange(x) { old, new -> }` (`shared/ui/ComposeUtils.kt`) |

## 옮기는 방법

- `@Observable final class Foo` → `class Foo`, 속성은 `var x by mutableStateOf(...)`, `private(set)` → `private set`.
- `@State private var vm = Foo()` → `val vm = remember { Foo() }`. `@State var x` → `var x by remember { mutableStateOf(...) }`.
- `@Binding var x` → 값 인자 + `onXChange: (T) -> Unit` 인자.
- `.task {}` → `LaunchedEffect(Unit) {}`, `.task(id: k)` → `LaunchedEffect(k)`. 버튼 안 `Task {}` → `rememberCoroutineScope().launch {}`.
- `async throws` → `suspend`. 일반 `catch` 앞에 반드시 `catch (e: CancellationException) { throw e }`.
- `VStack(spacing: s)` → `Column(verticalArrangement = Arrangement.spacedBy(s))`, `HStack` → `Row`, `ZStack` → `Box`, `Spacer()` → `Spacer(Modifier.weight(1f))`.
- `.frame(maxWidth: .infinity, alignment: .leading)` → `fillMaxWidth()` + 정렬. `RoundedRectangle(cornerRadius:, style: .continuous)` → `RoundedCornerShape`.
- `.ultraThinMaterial` → `CollogColors.gray00.copy(alpha = 0.92f)` (흐림 효과는 넣지 않는다).
- `NavigationStack` + `navigationDestination(for: Route.self)` → 탭 루트 화면이 `LocalNavigationManager.current.path.lastOrNull()`를 보고 밀어 넣은 화면을 그린다. 뒤로 가기는 `RootScreen`이 처리한다.
- `.sheet` → material3 `ModalBottomSheet`(`containerColor = CollogColors.gray50`). `.alert` → material3 `AlertDialog`(글꼴은 `CollogType`).
- `Date` → `java.time.Instant`, 날짜 계산은 `ZoneId.systemDefault()`, 한국어 형식은 `DateTimeFormatter.ofPattern(p, Locale.KOREAN)`. 주 계산은 iOS `Calendar(identifier: .gregorian)` 결과와 같은지 확인하고, 다르면 보고한다.
- iOS의 `#Preview`는 옮기지 않는다.
- 파일 이름: 화면(`FooView.swift`, 탭·전체 화면) → `FooScreen.kt`, 부품(`FooCardView`) → `FooCard.kt`. 폴더 구조는 iOS와 같게, 패키지는 소문자(`features.home.views.components`).
- 주석은 기반 코드처럼 적게, 한국어로. 클래스 KDoc에 iOS 원본 이름을 적는다.

## 테스트

`collog-iosTests/`의 해당 테스트를 `app/src/test/java/live/collog/app/...`에 JUnit4로 옮긴다. 네트워크·운영 서버는 부르지 않는다.
iOS `TestEnvironment`·`MemoryCredentials`는 `app/src/test/java/live/collog/app/TestEnvironment.kt`에 있다(`fixture.respond = { request -> 200 to json }`, `fixture.paths`). 쓰는 법은 `features/auth/AuthFlowTests.kt`를 본다.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew -q compileDebugKotlin
./gradlew -q testDebugUnitTest
```

## 맡은 범위 밖

- 맡은 파일 밖은 고치지 않는다. 기반에 없는 것이 필요하면 자기 기능 패키지 안에 두고 보고한다.
- `PendingPort`로 된 자리 파일은 맡은 화면만 통째로 바꾼다(함수 이름·인자는 유지).
- git은 자기 작업 브랜치에만 커밋한다. push·merge하지 않는다.
