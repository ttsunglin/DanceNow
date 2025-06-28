# Fiji DanceNow Plugin

A Fiji/ImageJ plugin that provides a persistent navigation window for quickly jumping to specific X,Y,Z,T coordinates while preserving your current zoom level.

## Features

- **Persistent Navigation Window**: A floating, always-on-top window that stays open for repeated use
- **Real-time Position Updates**: Shows your current view center and image information
- **Quick Navigation**: Navigate to any X,Y,Z,T coordinate with a single click or Enter key
- **Zoom Preservation**: Maintains your current zoom level during navigation
- **Input Validation**: Prevents out-of-bounds coordinates with immediate feedback
- **Keyboard Shortcuts**: Press Enter in any field to navigate instantly

## Installation

### Option 1: Build from Source
1. Clone this repository
2. Build with Maven: `mvn clean package`
3. Copy the generated JAR file from `target/` to your Fiji `plugins/` directory
4. Restart Fiji

### Option 2: Manual Installation
1. Download the compiled JAR file from releases
2. Place it in your Fiji `plugins/` directory
3. Restart Fiji

## Usage

1. Open an image in Fiji
2. Go to `Plugins > EveryBody > DanceNow`
3. A persistent navigation window will appear showing:
   - Current position (X,Y,Z,T coordinates of view center)
   - Image information (dimensions and title)
   - Input fields for target coordinates
4. Enter the desired X,Y,Z,T coordinates
5. Click "Go" or press Enter in any field to navigate

### Features in Detail

- **Persistent Window**: The DanceNow window stays open and can be used multiple times without reopening
- **Real-time Updates**: Current position updates automatically every 500ms
- **Always on Top**: Window stays visible above other applications for easy access
- **Boundary Checking**: Automatically validates coordinates against image dimensions
- **Multi-dimensional Support**: Works with 2D, 3D (Z-stacks), and 4D (time series) images

## Requirements

- Fiji/ImageJ
- Java 8 or later

## Building

```bash
mvn clean package
```

The compiled plugin will be available as `fiji-dancenow-1.0.0-SNAPSHOT.jar` in the `target/` directory.

