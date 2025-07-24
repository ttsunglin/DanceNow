# Fiji DanceNow Plugin

A Fiji/ImageJ plugin that provides a persistent navigation window for quickly jumping to specific X,Y,Z,T coordinates while preserving your current zoom level, with position list management capabilities.

## Features

- **Persistent Navigation Window**: A floating, always-on-top window that stays open for repeated use
- **Real-time Position Updates**: Shows your current view center and image information
- **Position List Management**: Save, edit, and navigate through multiple positions
- **Bulk Import from Excel**: Copy and paste multiple positions directly from spreadsheets
- **Quick Navigation**: Navigate to any X,Y,Z,T coordinate with a single click or Enter key
- **Zoom Preservation**: Maintains your current zoom level during navigation
- **Input Validation**: Prevents out-of-bounds coordinates with immediate feedback
- **Keyboard Shortcuts**: Press Enter in any field to navigate instantly
- **Export/Import Positions**: Save position lists to text files for later use

## Installation

1. Download the compiled `traget/DanceNow.jar` JAR file from releases
2. Place it in your Fiji `plugins/EveryBody` directory
3. Restart Fiji

## Usage

1. Open an image in Fiji
2. Go to `Plugins > EveryBody > DanceNow`
3. A persistent navigation window will appear showing:
   - Current position (X,Y,Z,T coordinates of view center)
   - Image information (dimensions and title)
   - Input fields for target coordinates
   - Position list with editable entries
   - Navigation and management buttons

### Basic Navigation
- Enter X,Y,Z,T coordinates in the input fields
- Click "Go" or press Enter in any field to navigate
- The view will center on the specified position while preserving zoom

### Position List Management

#### Adding Positions
- Click **"Add here"** to add the current view center position to the list
- **Direct paste**: Press Ctrl+V (Cmd+V on Mac) anywhere in the window to paste multiple positions
- **Right-click** on the position list and select "Paste Positions"
- Positions are displayed in X,Y,Z,T format (e.g., "512,384,1,1")
- You can manually edit positions directly in the list

#### Navigating Through Positions
- Click **"<Back"** to go to the previous position in the list
- Click **"Next>"** to go to the next position in the list
- Click any position in the list to select it, then click "Go"
- Navigation wraps around (Next from last position goes to first)

#### Managing the List
- Select a position and click **"Remove"** to delete it from the list
- Click **"Export"** to save all positions to a text file
- Click **"Load"** to import positions from a previously saved text file
- **Paste multiple positions directly**:
  - Press Ctrl+V (Cmd+V on Mac) to paste from Excel or other sources
  - Right-click and select "Paste Positions" from the context menu
  - Supports multiple delimiters: comma, space, or tab
  - Perfect for copying data directly from spreadsheets
  - Format: One position per line (e.g., `100 200 1 1` or `100,200,1,1` or `100→200→1→1`)

### Features in Detail

- **Persistent Window**: The DanceNow window stays open and can be used multiple times without reopening
- **Real-time Updates**: Current position updates automatically every 500ms
- **Always on Top**: Window stays visible above other applications for easy access
- **Boundary Checking**: Automatically validates coordinates against image dimensions
- **Multi-dimensional Support**: Works with 2D, 3D (Z-stacks), and 4D (time series) images
- **Editable Position List**: Double-click any position in the list to edit it directly
- **Flexible Import Format**: Accepts comma, space, or tab-separated values for easy Excel integration
- **Position File Format**: Simple text format with one position per line (X,Y,Z,T)

## Requirements

- Fiji/ImageJ
- Java 8 or later

## Building

```bash
mvn clean package
```

The compiled plugin will be available as `DanceNow.jar` in the `target/` directory.

## Position File Format & Excel Integration

### File Format
When exporting or importing positions, the plugin supports flexible formatting with one position per line:

```
512,384,1,1
1024 768 5 1
256	256	10	3
800,600,15,5
```

### Supported Delimiters
Each line represents X,Y,Z,T coordinates. The plugin accepts multiple delimiters:
- **Commas**: `512,384,1,1`
- **Spaces**: `512 384 1 1`
- **Tabs**: `512	384	1	1` (Excel default)
- **Mixed**: `512,384 1	1` (any combination works)

### Excel Integration
To import positions from Excel:
1. Arrange your data in 4 columns: X, Y, Z, T
2. Select and copy the cells (Ctrl+C/Cmd+C)
3. In DanceNow, press Ctrl+V (Cmd+V on Mac) to paste directly
4. Positions are automatically added to the list

This flexibility makes it seamless to work with position data from spreadsheets, analysis software, or manual entry.

