# Contributing to Agentic Integration Factory

Thank you for your interest in contributing! This guide will help you get started.

## Getting Started

1. Fork the repository and clone your fork
2. Create a new branch for your feature or bugfix:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Setup

1. Install dependencies:
   ```bash
   make install
   ```

2. Start local services:
   ```bash
   make compose-up
   ```

3. Run database migrations:
   ```bash
   make migrate
   ```

4. Start the development servers:
   ```bash
   # Terminal 1: API server
   make dev-api
   
   # Terminal 2: Web app
   npm run dev
   ```

## Running Tests

Run the test suite:
```bash
make test
```

Tests must pass with at least 80% code coverage before your PR can be merged.

## Code Style

We use automated formatting and linting:

- **Python**: `ruff` for linting and formatting
- **TypeScript**: ESLint and TypeScript compiler

Run linters:
```bash
make lint
```

Format code:
```bash
make format
```

We recommend installing pre-commit hooks:
```bash
pip install pre-commit
pre-commit install
```

## Type Checking

Python code should pass `mypy` type checking:
```bash
cd apps/api
uv run mypy src/
```

## Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) format:

- `feat:` new features
- `fix:` bug fixes
- `docs:` documentation changes
- `refactor:` code refactoring
- `test:` test additions or changes
- `chore:` maintenance tasks

Example:
```
feat: add PostgreSQL source adapter

- Implement polling consumer for CDC events
- Add schema introspection
- Include integration tests
```

## Pull Request Process

1. Ensure all tests pass and coverage meets the threshold
2. Update documentation for any new features
3. Add entries to relevant sections if introducing breaking changes
4. Request review from maintainers

### PR Checklist

- [ ] Tests pass locally (`make test`)
- [ ] Code is formatted (`make format`)
- [ ] Linters pass (`make lint`)
- [ ] Documentation updated (if applicable)
- [ ] Commit messages follow conventions

## Adding New Source Adapters

To add a new source type:

1. Create a new adapter in `apps/api/src/spec2event/adapters/source/`
2. Implement the `SourceAdapter` interface:
   - `parse()`: Parse raw input into structured data
   - `summarize()`: Generate human-readable summary
   - `canonicalize()`: Transform into canonical event model
3. Register the adapter in `__init__.py`
4. Add tests in `apps/api/tests/`
5. Update documentation in `references/source_adapter_matrix.md`

## Code of Conduct

This project adheres to the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## Questions?

- Open an issue for bug reports or feature requests
- Start a discussion for questions or ideas

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.