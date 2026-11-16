# Verification

- Maven `verify` passed from a fresh target directory with JDK 21: three HTTP tests, zero failures.
- Executable JAR returned `Hi World!` over HTTP.
- Docker image built successfully. Container HTTP greeting and health endpoint checks passed.
- Cloud Build execution, GitHub trigger connection and live Cloud Run deployment have not been performed. They require your Google Cloud project, billing/IAM setup and GitHub connection. Follow README.md.
