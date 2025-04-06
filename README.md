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