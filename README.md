# Stella type checker in Kotlin

## Cloning & setup

Project uses Kotlin 1.9 with Java 21 and Gradle to build, which can be run through `gradlew` wrapper.

## Build

### Grammar

Grammar located in `/src/main/antrl`.

```
./gradlew generateGrammarSource
```

### Running

```bash
# Build
./gradlew build

# Build & Run
./gradlew run
```