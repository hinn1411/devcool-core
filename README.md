### Application structure
```
├── application/          # Application Layer
│   ├── service/          # Application Services
│   └── dto/              # Data Transfer Objects
├── domain/               # Domain Layer (Heart)
│   ├── model/            # Domain Entities & Value Objects
│   ├── service/          # Domain Services
│   └── port/             # Repository Interfaces (Ports)
├── infrastructure/       # Infrastructure Layer (Adapters)
│   ├── config/           # Spring Configuration
│   ├── persistence/      # JPA Repository Implementations
│   ├── rest/             |# REST Controllers
│   ├── websocket/        # WebSocket Controllers/Handlers
│   └── security/         # Security Config
└── DevCoolApplication.java
```