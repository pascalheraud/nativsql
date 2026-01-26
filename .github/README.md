# GitHub Workflows and CI/CD Configuration

This directory contains GitHub Actions workflows and configuration for automated building, testing, and releasing.

## Quick Links

- [Workflows Documentation](.github/WORKFLOWS.md) - Detailed workflow descriptions
- [Contributing Guide](.github/CONTRIBUTING.md) - How to contribute
- [Build Status](https://github.com/pascalheraud/nativsql/actions) - Check workflow runs

## Files Overview

### Workflows

| File | Purpose | Triggers |
|------|---------|----------|
| `build.yml` | Build and test on multiple Java versions | Push to main/develop, PRs |
| `pr-checks.yml` | Code quality and coverage checks | PRs to main/develop |
| `release.yml` | Create GitHub releases and publish artifacts | Tag push (v*) |

### Configuration

| File | Purpose |
|------|---------|
| `dependabot.yml` | Automatic dependency update checks |
| `CONTRIBUTING.md` | Contribution guidelines |
| `WORKFLOWS.md` | Detailed workflow documentation |
| `ISSUE_TEMPLATE/` | Templates for bug reports and feature requests |

## Quick Start

### For Contributors

1. Read [Contributing Guide](CONTRIBUTING.md)
2. Set up development environment (see Workflow docs)
3. Make changes on a feature branch
4. Open a Pull Request
5. Ensure CI passes

### For Maintainers

1. Review and merge PRs
2. Tag releases: `git tag -a v1.0.0 && git push origin v1.0.0`
3. Monitor GitHub Actions results
4. Review Dependabot PRs

## Continuous Integration

All PRs and pushes run:

✅ Build with Gradle (Java 17+)
✅ Integration tests (MySQL, MariaDB, PostgreSQL)
✅ Code quality checks
✅ Security scanning
✅ Test coverage reporting

## Continuous Deployment

On version tag push:

📦 Build artifacts
📋 Create GitHub Release
📄 Generate changelog
🚀 Publish artifacts

## Local Development

```bash
# Start services
docker-compose up -d

# Run tests
./gradlew test

# Check code quality
./gradlew spotlessCheck build

# Stop services
docker-compose down
```

## GitHub Actions Status

[![Build Status](https://github.com/pascalheraud/nativsql/actions/workflows/build.yml/badge.svg)](https://github.com/pascalheraud/nativsql/actions/workflows/build.yml)
[![PR Checks](https://github.com/pascalheraud/nativsql/actions/workflows/pr-checks.yml/badge.svg)](https://github.com/pascalheraud/nativsql/actions/workflows/pr-checks.yml)

## Support

- 📖 [Workflows Documentation](WORKFLOWS.md)
- 💬 [GitHub Discussions](https://github.com/pascalheraud/nativsql/discussions)
- 🐛 [Report Issues](https://github.com/pascalheraud/nativsql/issues)
