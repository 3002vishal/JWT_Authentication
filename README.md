# Spring Boot JWT Authentication

A Java 17 / Spring Boot demo implementing **JWT-based authentication** with Spring Security.

## Features

- User signup and login flow
- BCrypt password hashing
- JWT creation and verification
- Custom JWT authentication filter
- Protected endpoints
- Spring Security filter-chain configuration
- JPA persistence with H2 for local development

## Authentication Flow

```text
Client
  |
  | credentials
  v
Auth API
  |
  | successful authentication
  v
JWT issued
  |
  | Authorization: Bearer <token>
  v
JwtAuthenticationFilter
  |
  v
Spring Security
  |
  v
Protected Controller
```

## Tech Stack

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- JJWT
- H2
- Maven

## Run

```bash
cd demo
./mvnw spring-boot:run
```

Before starting the application, provide the required secrets through environment variables.

Example:

```bash
export JWT_SECRET="replace-with-a-long-random-secret"
export CASHFREE_CLIENT_ID="your-test-client-id"
export CASHFREE_CLIENT_SECRET="your-test-client-secret"
```

## Security

Application secrets are loaded from environment variables rather than hard-coded configuration. Any credential that has previously been committed should be revoked/rotated even after it is removed from the current branch.

## Purpose

This project demonstrates how JWT authentication fits into the **Spring Security request/filter pipeline** and how protected APIs are authenticated before controller execution.
