# kotlin_runner
Kotlin script runner with GUI

## Project structure
`/src/main/kotlin/`: Project source code

`/src/main/kotlin/gui`: Classes responsible for gui

`/src/main/kotlin/run`: Classes responsible for running script

`/build.gradle.kts`: Configuration of gradle build

## Usage
In root folder
```
./gradlew clean build
``` 
```
./gradlew run
```
New window with opened application will appear

## Notes
*Use caching* is used for remembering output of the same script

It saves output only if script finished with code 0

It saves cache even if it`s turned off

Application checks if Kotlin is installed on user`s PC

## Dependencies
- **Kotlin Standard Library**:  
  `org.jetbrains.kotlin:kotlin-stdlib`

- **Kotlin Scripting JVM**:  
  `org.jetbrains.kotlin:kotlin-scripting-jvm:1.9.0`

- **JavaFX Controls**:  
  `org.openjfx:javafx-controls:20.0.1`

- **JavaFX Graphics**:  
  `org.openjfx:javafx-graphics:17.0.2`

- **RichTextFX**:  
  `org.fxmisc.richtext:richtextfx:0.11.4`