# integrate-boot

A foundational framework for business systems, encapsulating core capabilities.

## Module

```
integrate-boot
├── bom                          # BOM (Bill of Materials) — dependency version management
└── module
    └── integrate-boot-data      # Data-access integration (MyBatis-Flex + Spring Boot)
```

## Build

This project uses Gradle (Kotlin DSL). Use the included wrapper — no local Gradle install required.

```bash
# List all projects
./gradlew projects

# Build everything
./gradlew build
```

## Requirements

- JDK 25
- Gradle 8.14+ (provided via the wrapper)
