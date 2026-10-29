# DailyLog Developer Documentation

This document provides instructions for setting up a development environment and building the DailyLog application.

## Prerequisites

- Android Studio (recommended) or command-line tools
- JDK 21 / OpenJDK 21 (required for building)
- Kotlin SDK 2.2.10 (as specified in build.gradle)
- Android SDK with API level 37
- Gradle 8.0 or higher

## Setting Up Development Environment Using Android Studio

Download the latest version of Android Studio.

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Seafloor-Studio/dailylog.git
   cd dailylog
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned repository directory

3. **Sync the project**:
   - Android Studio will automatically sync Gradle files
   - If prompted, accept any SDK or build tool updates

4. **Run all tests:**
   - Under `app/src` locate the two test folders: `test` (unit tests) and `androidTest` (instrumentation tests).
   - In Android Studio, right click on each folder and click on "Run 'All Tests'" (Ctrl + Shift + R)
   - Ensure all tests pass
   - Common issues:
     - If it's been a while since the app was updated, the current Gradle version may be incompatible with the latest version of Android Studio.
     - Android Studio won't build the app until this is resolved, this may impact running tests or building the debug version (step 5)
     - To resolve, follow the instructions in Android Studio to upgrade the Gradle and relevant library versions and put up a pull request.

5. **Debug build the project**:
   - In the top right-hand corner in Android studio:
      - select a device
      - select "app"
      - click on the green play button
   - Ensure the app builds with no errors
      - the debug apk is stored in `app/build/outputs/apk/debug/app-debug.apk`
   - In the device, play around with the app

6. **madCode only**: Set up signing key for releases
   - Locate the `dailylog.jks` keystore file.
   - Add the following secrets to the GitHub repository (**Settings → Secrets and variables → Actions**):

   | Secret | Value |
   |---|---|
   | `KEYSTORE_BASE64` | Base64-encoded keystore: `base64 -i dailylog.jks \| pbcopy` |
   | `KEYSTORE_PASSWORD` | Keystore password |
   | `KEY_ALIAS` | Key alias |
   | `KEY_PASSWORD` | Key password |

   - In Android Studio, go to **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** and select `openjdk-21`.

## Releasing a New App Version

### Part 1: Tag a New Release

1. **Update version information in `app/build.gradle`**:
   ```gradle
   defaultConfig {
       applicationId "com.app.dailylog"
       minSdkVersion 23
       targetSdkVersion 37
       versionCode 3001  // Increment this number
       versionName "3.0.1"  // Update to new semantic version
   }
   ```

2. **Commit and tag**:
   ```bash
   git add app/build.gradle
   git commit -m "Release version 3.0.1"
   git tag -a v3.0.1 -m "Release version 3.0.1"
   git push origin v3.0.1
   ```

3. **Pushing the tag** triggers the `release` GitHub Actions workflow, which builds a signed APK and a signed AAB and creates the GitHub Release page with both attached automatically.
   - **Note:** The four keystore secrets must be configured in the repository (see setup step 6 above) before this will work.
   - **Important:** F-Droid requires an APK and Google Play requires an AAB, so the workflow builds both in one Gradle run and attaches both. Because it is one run with one `signingConfig`, they carry the same signature. F-Droid's metadata names `app-release.apk` exactly, so don't rename that asset.
   - **Play app signing:** Play only *distributes* with this keystore if it was uploaded as the app signing key when the app was created in Play Console. If Play generated its own key instead, the Play build and the F-Droid build have different signatures and a user cannot move between them without reinstalling.

4. **Add release notes and publish**: once the workflow completes, a draft release will be waiting on the [Releases page](https://github.com/Seafloor-Studio/dailylog/releases). Edit it to add a title and description/changelog, then publish it.

### Part 2: Ensuring a Successful F-Droid Release

You can read about F-Droid's update process [here](https://gitlab.com/fdroid/wiki/-/wikis/FAQ#finding-updates). This page also links to where you can see the latest builds and releases.

After publishing a new release, wait at least 24 hours, then check the following:
1. The [DailyLog F-Droid page](https://f-droid.org/packages/com.app.dailylog/): see if the version at the bottom matches the new version.
2. If not, check the [metadata file](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.app.dailylog.yml?ref_type=heads): see if F-Droid successfully updated the version number.
3. If not, search [existing DailyLog merge requests](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/?sort=created_date&state=opened&search=DailyLog&first_page_size=20): see if the check-updates bot had issues and if anyone is working on it

## Testing

### Running Unit Tests
```bash
./gradlew test
```

### Running Instrumentation Tests
```bash
./gradlew connectedAndroidTest
```

### Running Coverage Reports
```bash
# Generate HTML coverage report
./gradlew koverHtmlReport

# Generate XML coverage report  
./gradlew koverXmlReport

# Verify coverage requirements
./gradlew koverVerify
```
- Report Locations
  - HTML: `app/build/reports/kover/html/index.html`
  - XML: `app/build/reports/kover/xml/report.xml`

### Pre-commit Hook
The pre-commit hook automatically runs when committing code:
1. Runs unit tests with coverage instrumentation
2. Generates Kover HTML and XML reports
3. Displays report locations and OS-specific open instructions

## Code Style and Formatting

### Kotlin Formatting
- Uses Kotlin 2.2.10 (from build.gradle)
- Follows Kotlin code style "official" as specified in gradle.properties

### Android Project Structure
The project follows standard Android conventions:
- `app/src/main/java/com/app/dailylog/` - Main application code
- `app/src/main/res/` - Resources (layouts, drawables, strings)
- `app/src/test/java/` - Unit tests
- `app/src/androidTest/java/` - Instrumentation tests

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes following the code style
4. Write tests for new functionality
5. Run all tests to ensure no regressions
6. Submit a pull request

## Troubleshooting

### Common Issues

1. **Build failures with Java version**:
   - Ensure JDK 21 (OpenJDK 21) is installed and set as the Gradle JDK in Android Studio (**Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**)
   - On macOS with Homebrew: `brew install openjdk@21`, then symlink it: `sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`

2. **File access problems**:
   - The app declares no permissions at all. It reaches the log file through a `content://`
     URI the user picks, and keeps access with `takePersistableUriPermission`.
   - So a file that has stopped opening usually means the URI is no longer grantable: the
     file was moved, renamed or deleted, or its provider revoked access. Pick the file again.

3. **F-Droid build errors**:
   - Ensure all dependencies are compatible with F-Droid's build environment
   - Verify the app builds with openjdk-21-jdk-headless