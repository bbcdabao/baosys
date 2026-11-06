# BaoSys

[简体中文](README.md) | **English**

**A foundation for small projects with multiple subsystems.**

BaoSys brings account, login, permission, and session management together in a central Manager application, with shared conventions for integrating independent business subsystems. It helps individual developers and small teams spend less time rebuilding common administration features and more time on their business applications.

For example, as you build an admin console, a task platform, and internal tools, you can organize them around a shared account and permission catalog. Assign users access to specific systems and operations, and choose where they land after signing in.

## Why BaoSys?

- **Reuse common capabilities.** Manager provides user management, account activation and deactivation, permission assignment, session inspection and revocation, and auditing of authorization changes. New business systems can build on this account management foundation.
- **Use consistent navigation across systems.** A central catalog defines subsystem codes, public URLs, home pages, and permissions. Shared login returns users to the requested page according to their permissions, or shows the systems they can access.
- **Keep business systems independent.** Each subsystem has its own frontend base path and API prefix. Accounts and login are managed centrally, while each subsystem owns its business pages, APIs, and data logic.
- **Enforce permissions at the API level.** System access and permission to perform individual operations are separate. The gateway checks system access, and business services enforce API permissions, with distinct responsibilities for frontend presentation and backend authorization.
- **Start with code you can understand and extend.** The frontend and backend are separate. Backend modules distinguish API contracts, business implementation, and infrastructure. Shared security modules are split by responsibility, with documentation covering setup, design, and development conventions.

## Who is it for?

BaoSys is suitable as a shared foundation for personal projects, small-team internal platforms, and collections of administration tools. It can also serve as a learning project for authentication, authorization, and modular design in Vue and Spring Boot applications.

The project keeps its scope small. Permissions are currently assigned directly to accounts. Roles, organizational structures, multi-tenancy, and business data access controls are not yet implemented; you can extend the foundation to fit your own business model.

## Current capabilities

| Area | Implementation |
|---|---|
| Account management | User CRUD, account activation and deactivation, permission assignment, default subsystem selection |
| Login navigation | Shared login page, return to the requested page, default home page, list of authorized systems |
| Authentication and authorization | JWT, HttpOnly cookies, CSRF validation for browser requests, system access and API permission checks |
| Session management | Session inspection and revocation, logout, auditing of authorization changes |
| Administration UI | Vue frontend, Chinese and English, theme switching |
| Integration conventions | System and permission catalog, independent frontend paths and API prefixes, shared security modules for gateways and resource services |

**This repository currently contains the Manager application and shared modules.** Other business subsystems must be developed and integrated separately. CAT is a reserved catalog entry, not a business application included in this repository. The system configuration page is currently a UI scaffold.

## Project structure

| Module | Responsibility |
|---|---|
| `baosys-common-api` | Shared response and pagination contracts |
| `baosys-common-security-core` | Security configuration, JWT, and permission fundamentals |
| `baosys-common-security-resource` | Authentication and API authorization for Servlet services |
| `baosys-common-security-gateway` | Gateway authentication, system access checks, and CSRF |
| `baosys-common-password` | Password encoding and verification |
| `baosys-manager/baosys-manager-api` | Manager API contracts and DTOs |
| `baosys-manager/baosys-manager-service` | Business logic, authorization server, and database access; port 9090 by default |
| `baosys-manager-web` | API gateway and frontend static resource host; port 8080 by default |
| `baosys-manager-ui` | Vue administration frontend; development server on port 5173 by default |

The stack includes Java 17, Spring Boot, Spring Cloud Gateway, MyBatis Plus, PostgreSQL, Flyway, Vue 3, TypeScript, Vite, Pinia, and Arco Design. See [pom.xml](pom.xml) and [package.json](baosys-manager-ui/package.json) for exact versions.

## Getting started

Detailed guides are available in both Chinese and English:

- [Setup and deployment](docs/development.en.md): local startup, required configuration, testing, and packaging.
- [System design](docs/system.en.md): request flow, authentication and permissions, APIs, and the database.
- [Development conventions](docs/conventions.en.md): layering, dependencies, APIs, database conventions, and documentation maintenance.

To get started, you need Java 17 and PostgreSQL. Frontend development also requires Node.js; see the frontend `package.json` for the version requirement. Use an empty database or a new schema and let Flyway initialize it. Follow the setup guide to prepare signing keys and explicitly enable administrator initialization.

The packaged Manager UI is served at `/baosys-manager/`. During frontend development, use `http://localhost:5173/baosys-manager/`. To add a subsystem, start by registering it in the system catalog and configuring its frontend base URL and API prefix, then integrate shared login and API authorization. See [Setup and deployment](docs/development.en.md) for configuration details and steps.

[Explore the system catalog](baosys-systems.yml) to see how subsystem entry points and permissions are configured.
