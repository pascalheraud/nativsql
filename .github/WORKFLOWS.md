# GitHub Actions Workflows

This project uses GitHub Actions to automate building, testing, and releasing.

## Workflows

### 1. Build and Test (`build.yml`)

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop` branches

**Features:**
- Runs on Java 21 and 22 (matrix strategy)
- Starts MySQL, MariaDB, and PostgreSQL services for integration tests
- Executes full Gradle build and test suite
- Uploads test reports on failure
- Publishes test results

**Services:**
- MySQL 8.0 (port 3306)
- MariaDB 11 (port 3307)
- PostgreSQL 15 with PostGIS (port 5432)

### 2. Pull Request Checks (`pr-checks.yml`)

**Triggers:**
- Pull requests to `main` or `develop` branches

**Features:**
- Code formatting checks (spotlessCheck)
- Code linting
- Full build verification
- Security vulnerability scanning (dependencyCheck)
- Test coverage analysis with JaCoCo
- Codecov integration for coverage reports

### 3. Release (`release.yml`)

**Triggers:**
- Push of tags matching `v*` (e.g., `v1.0.0`, `v1.0.0-alpha`)

**Features:**
- Full build and test
- Creates GitHub Release with artifacts
- Detects prerelease status (alpha/beta versions)
- Optional Maven Central publishing

## Local Development

### Running the same checks locally:

```bash
# Build and test (same as CI)
./gradlew build test

# Code quality checks
./gradlew spotlessCheck

# Test coverage report
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

## Setting up Services Locally

For local integration testing without CI:

```bash
# Start Docker containers
docker-compose -f docker-compose.yml up -d

# Run tests
./gradlew test

# Stop containers
docker-compose down
```

## Release Process

### Creating a Release

1. Create and push a version tag:
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

2. The `release.yml` workflow will:
   - Build the project
   - Create a GitHub Release
   - Upload JAR artifacts
   - Optionally publish to Maven Central (if configured)

### Prerelease Versions

For alpha/beta releases, use tags like:
```bash
git tag -a v1.0.0-alpha -m "Alpha release"
git push origin v1.0.0-alpha
```

This will mark the release as a prerelease on GitHub.

## Requirements

- Java 21 or 22
- Gradle 8.0+
- Docker (for local service testing)

## Environment Variables

The workflows use the following environment variables:
- `MYSQL_HOST`: localhost (port 3306)
- `MARIADB_HOST`: localhost (port 3307)
- `POSTGRES_HOST`: localhost (port 5432)

These are automatically configured in CI and can be overridden locally.
