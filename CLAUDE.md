# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project status

This repository is being built from scratch.

Nothing described in this file should be treated as already implemented until the corresponding
code actually exists in the repository.

As the project develops, update this file with the real build, test, lint, Docker, and development
commands used by the application.

---

## What DocFit AI is

DocFit AI is a full-stack healthcare navigation platform, built from scratch, that helps users
find and compare healthcare providers that fit their needs.

The primary matching factors are:

1. Care type / provider specialty
2. Insurance compatibility
3. Geographic location
4. Provider availability, where reliable data exists
5. Provider information and ratings
6. Optional user preferences

A core problem DocFit AI aims to solve is helping users avoid manually searching provider
directories and trying to determine whether a provider is compatible with their insurance.

The expected user flow is:

1. User selects the type of care or specialty they need.
2. User selects or enters their insurance plan.
3. User provides a location or ZIP code.
4. DocFit AI searches for relevant providers.
5. The system filters and ranks providers using available compatibility and provider data.
6. The user compares provider options and can continue to the provider's contact or booking channel.

This is a new implementation.

Do not assume, port, or reuse any previous UI, dataset, database schema, scoring algorithm,
or architecture from elsewhere.

---

## Hard scope boundary

DocFit AI is strictly a healthcare navigation application.

It must never:

- diagnose medical conditions
- interpret symptoms
- recommend treatments
- prescribe medication
- provide clinical advice

Provider recommendation means ranking or matching healthcare providers.

It must never mean recommending clinical treatment.

If a requested feature could cross into diagnosis, symptom interpretation, or treatment advice,
flag the issue before implementing it.

---

## Target architecture

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- Maven
- REST APIs

### Frontend

- React
- TypeScript
- Vite

### Database

- PostgreSQL

### Engineering

- Git
- GitHub
- Docker
- Automated testing
- API documentation
- Environment-based configuration
- CI/CD
- Cloud deployment

---

## Potential functionality

The project may eventually include:

- User registration and login
- Secure authentication and authorization
- Provider search
- Care category / specialty filtering
- Insurance-plan filtering
- Location and ZIP-code search
- Provider profiles
- Provider sorting and filtering
- Provider recommendation / ranking
- Saved providers
- Provider comparison
- Search history
- External provider contact links
- External booking links
- Integration with legitimate healthcare/provider APIs
- Provider availability where reliable data exists

These features are not automatically considered implemented.

Build them incrementally.

---

## Development workflow

- Build incrementally.
- Do not generate the entire application at once.
- Explain the approach before major implementation.
- Prefer small, reviewable changes over large rewrites.
- Use clear Controller -> Service -> Repository separation in the Spring Boot backend.
- Use DTOs rather than exposing JPA entities directly through REST APIs.
- Use constructor injection instead of field injection.
- Validate input at API boundaries.
- Use centralized error handling where appropriate.
- Never hardcode passwords, API keys, tokens, or credentials.
- Never commit secrets.
- Use environment-based configuration.
- Do not introduce major dependencies without explaining why they are needed.
- Run backend tests after meaningful backend changes.
- Run frontend type checking/build/tests after meaningful frontend changes.
- Do not silently ignore failing tests.
- Do not change valid tests merely to make broken code pass.
- Keep the healthcare navigation boundary explicit.

Before modifying several files, briefly explain the implementation plan.

After meaningful implementation work, summarize:

- files changed
- functionality added
- tests/build commands executed
- errors or warnings encountered
- remaining work

Do not create Git commits or push to GitHub unless explicitly asked.
