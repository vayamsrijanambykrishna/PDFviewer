# Releasing PDFviewer

## Coordinates

Core: in.krishna:pdfviewer:<version>

Compose adapter: in.krishna:pdfviewer-compose:<version>

The repository defaults to version 0.8.0. Release tags should use the same value with a leading v, for example v0.8.0.

## Local validation

Use JDK 17 and run:

    gradle check assembleRelease

To generate publication POMs without uploading:

    gradle :pdfviewer:generatePomFileForReleasePublication
    gradle :pdfviewer-compose:generatePomFileForReleasePublication

To install locally:

    gradle publishToMavenLocal

## Remote publishing

The Gradle build reads these environment variables:

- MAVEN_REPOSITORY_URL
- MAVEN_USERNAME
- MAVEN_PASSWORD
- SIGNING_KEY
- SIGNING_PASSWORD

Signing is enabled only when both signing values are present.

The GitHub release workflow publishes a tag to the configured Maven repository. Credentials should be stored as GitHub Actions secrets, not committed to the repository.

## Maven Central

The project is deliberately not hard-coded to a Maven Central endpoint. Maven Central moved away from the legacy OSSRH deployment protocol in 2025, and Sonatype's current documentation states that there is no official Gradle plugin for the Central Publisher Portal. The repository therefore exposes standard Maven publications and an environment-driven remote repository, leaving the Central Portal upload mechanism to the release environment.

For a Central Portal workflow, use a current supported publishing method such as a Central Portal-compatible community Gradle/JReleaser integration, while keeping the library's publication coordinates and signing configuration defined here.

## Versioning

Use semantic versioning:

- 0.x.y while the public API is still evolving.
- 1.0.0 when the API and behavior are declared stable.
