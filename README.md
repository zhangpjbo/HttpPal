# HttpPal Plugin / HttpPal 插件

[English](#english) | [中文](#中文)

---

## English

### Overview

HttpPal is a comprehensive HTTP/WebSocket client plugin for JetBrains IDEs (primarily IntelliJ IDEA) that provides developers with integrated API testing capabilities directly within their development environment.

### Key Features

#### 🔍 **Automatic API Discovery**
- Automatically scans Java projects to discover REST API endpoints
- Supports Spring MVC annotations (@RequestMapping, @GetMapping, @PostMapping, etc.)
- Supports JAX-RS annotations (@GET, @POST, @PUT, @DELETE, @PATCH)
- Real-time endpoint discovery with source code changes

#### 🛠️ **Manual Endpoint Management**
- Add and edit API endpoints manually
- Support for external APIs not discoverable from source code
- Persistent endpoint configuration storage
- Comprehensive endpoint validation

#### 🖥️ **Integrated User Interface**
- Native ToolWindow integration within JetBrains IDEs
- Intuitive request configuration interface
- Support for all HTTP methods (GET, POST, PUT, DELETE, PATCH)
- Real-time response display with syntax highlighting

#### 🔌 **WebSocket Support**
- Full WebSocket client functionality
- Real-time message sending and receiving
- Connection status monitoring
- Message history preservation

#### 📋 **Advanced Header Management**
- Individual request header configuration
- Global headers applied to all requests
- Header override capabilities
- Secure storage for sensitive headers

#### 📊 **Response Analysis**
- Complete response body display with formatting
- Response headers visualization
- Response time measurement
- JSON/XML syntax highlighting

#### ⚡ **Concurrent Execution**
- Parallel HTTP request execution
- Configurable thread count and iteration parameters
- Aggregated performance statistics
- Load testing capabilities

#### 📚 **History & Favorites**
- Automatic request history tracking
- Favorite requests management
- Quick access to frequently used endpoints
- Persistent storage across IDE sessions

#### 🌍 **Environment Management** *(Optional)*
- Multiple environment support (dev, staging, production)
- Environment-specific base URLs and global headers
- Easy environment switching
- Project-level environment configuration

#### 📤 **JMeter Export Integration**
- Export single HTTP requests as Apache JMeter .jmx files
- Export concurrent test scenarios with proper thread group configuration
- Multi-request export with structured test plan organization
- Environment integration for seamless JMeter compatibility
- Direct integration with JMeter ecosystem for advanced load testing

### Technical Stack

- **Language**: Kotlin (with Java compatibility)
- **Build Tool**: Gradle with `gradle-intellij-plugin`
- **HTTP Client**: Java 11+ HttpClient or OkHttp
- **Concurrency**: `java.util.concurrent` package
- **UI Framework**: IntelliJ Platform UI DSL
- **Code Analysis**: IntelliJ PSI (Program Structure Interface)

### Architecture Components

1. **Core Engine**: HTTP/WebSocket communication handling
2. **Discovery Service**: Automatic API endpoint detection
3. **UI Layer**: ToolWindow and form components
4. **Storage Layer**: Settings and history persistence
5. **Environment Manager**: Multi-environment configuration
6. **Concurrent Executor**: Parallel request execution

### Security Considerations

- Secure storage for sensitive header information
- Encrypted persistence for authentication tokens
- Project-level security isolation
- Safe handling of credentials in multi-user environments

### Installation & Usage

*(Coming soon - plugin will be available through JetBrains Plugin Marketplace)*

### Contributing

We welcome contributions! Please see our contributing guidelines for more information.

### License

*(License information to be determined)*

---
