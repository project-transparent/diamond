![Diamond](https://github.com/project-transparent/diamond/blob/master/diamond.png)

**Diamond** is a Gradle plugin and test framework that sets up a development environment for working with the Java
compiler. It's designed to allow Project Transparent's other projects to be JDK and Java version agnostic, but is able
to be used by anyone.

## Installation (Gradle - Local)

1. Clone the repo (https://github.com/project-transparent/diamond).
2. Run `gradlew publishToMavenLocal` in the root directory of the repo.
3. Add `mavenLocal()` to your plugin repositories.
4. Add `id 'org.transparent.diamond' version '<version>'` to your plugins.
