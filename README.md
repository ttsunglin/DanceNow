# Fiji ViewMover Plugin

A Fiji/ImageJ plugin that allows you to quickly navigate to specific X,Y,Z,T coordinates while preserving your current zoom level.

## Features

- Navigate to any X,Y,Z,T coordinate in your image
- Maintains current zoom level during navigation  
- Input validation to prevent out-of-bounds coordinates
- Simple dialog interface with current image information

## Installation

### Option 1: Build from Source
1. Clone this repository
2. Build with Maven: `mvn clean package`
3. Copy the generated JAR file from `target/` to your Fiji `plugins/` directory
4. Restart Fiji

### Option 2: Manual Installation
1. Download the compiled JAR file
2. Place it in your Fiji `plugins/` directory
3. Restart Fiji

## Usage

1. Open an image in Fiji
2. Go to `Plugins > Navigation > ViewMover`
3. Enter the desired X,Y,Z,T coordinates in the dialog
4. Click OK to navigate to the position

The plugin will:
- Move the view to center on your specified X,Y coordinates
- Change to the specified Z slice and T frame
- Preserve your current zoom level
- Keep you within image boundaries

## Requirements

- Fiji/ImageJ
- Java 8 or later

## Building

```bash
mvn clean package
```

The compiled plugin will be in the `target/` directory.