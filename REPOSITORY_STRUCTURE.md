# Repository Structure

## Project Overview
DanceNow - A Fiji/ImageJ plugin for persistent navigation window with X,Y,Z,T coordinate jumping and position list management.

## Directory Structure

```
DanceNow/
│
├── src/                                    # Source code directory
│   └── main/
│       ├── java/                          # Java source files
│       │   └── com/
│       │       └── github/
│       │           └── ttl/
│       │               └── dancenow/
│       │                   └── DanceNow.java    # Main plugin implementation
│       │                                        # - Compact navigation window UI
│       │                                        # - Position list with notes support
│       │                                        # - CSV/TXT import/export with auto-rename
│       │                                        # - Real-time crosshair overlay
│       │                                        # - Mouse-only interaction model
│       │                                        # - Table sorting and editing
│       │                                        # - Smart validation and error handling
│       │
│       └── resources/
│           └── plugins.config             # Fiji plugin configuration
│                                         # Defines menu location and entry point
│
├── target/                                # Build output directory (generated)
│   ├── DanceNow.jar                      # Compiled plugin JAR file
│   └── [build artifacts]                 # Maven build outputs
│
├── pom.xml                               # Maven project configuration
│                                        # - Dependencies: ImageJ/Fiji
│                                        # - Build configuration
│                                        # - Plugin packaging settings
│
├── README.md                             # Project documentation
│                                        # - Feature descriptions
│                                        # - Installation instructions
│                                        # - Usage guide
│                                        # - Position file format specs
│
├── CLAUDE.md                             # Development guidelines
│                                        # - Coding standards
│                                        # - Cross-platform requirements
│                                        # - Repository management rules
│
├── REPOSITORY_STRUCTURE.md              # This file - Repository organization
│
├── LICENSE                               # Project license file
│
└── test_positions.txt                    # Sample position data file
                                         # Format: X,Y,Z,T coordinates

```

## File Descriptions

### Core Implementation
- **DanceNow.java**: Main plugin class implementing the navigation window, position management, and all user interactions

### Configuration Files
- **plugins.config**: Fiji/ImageJ plugin registration (menu location: Plugins > EveryBody > DanceNow)
- **pom.xml**: Maven build configuration with ImageJ dependencies

### Documentation
- **README.md**: User-facing documentation with installation and usage instructions
- **CLAUDE.md**: Development guidelines and coding standards
- **REPOSITORY_STRUCTURE.md**: Repository organization documentation

### Build Artifacts
- **target/**: Contains compiled JAR and Maven build outputs (not tracked in git)

## Build System
- **Build Tool**: Maven
- **Target**: Java 8+
- **Output**: DanceNow.jar plugin for Fiji/ImageJ

## Key Components

### DanceNow.java Structure
- **DanceNowWindow**: Main window class (1500+ lines)
  - Real-time position tracking (50ms updates)
  - Compact navigation controls
  - Advanced position list with notes
  - CSV/TXT import/export with format detection
  - Context menu for table operations
  - Click-to-sort table headers
  - Visual crosshair overlay system
  - Mouse event listeners for ImageJ canvas

### Key Features Implemented
- **Position Class**: X,Y,Z,T coordinates with optional note field
- **Crosshair System**: Green center indicator with zoom-aware scaling
- **File Safety**: Auto-rename to prevent overwrites (adds (2), (3), etc.)
- **Smart Validation**: Boundary checking with helpful warnings
- **Table Editing**: Direct in-cell editing with immediate validation
- **Mouse-Only Design**: All keyboard shortcuts removed for better integration

## Development Notes
- Cross-platform compatibility (Mac/Windows)
- No unauthorized licensing
- Follows Fiji/ImageJ plugin architecture
- Uses Swing for UI components