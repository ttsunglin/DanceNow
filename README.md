# Fiji DanceNow Plugin

A Fiji/ImageJ plugin that provides a persistent navigation window for quickly jumping to specific X,Y,Z,T coordinates while preserving your current zoom level, with advanced position list management and visual center indication.

## Features

### Core Navigation
- **Persistent Navigation Window**: A floating, always-on-top window that stays open for repeated use
- **Real-time Position Updates**: Shows your current view center and image information updated every 50ms
- **Zoom Preservation**: Maintains your current zoom level during navigation
- **Visual Center Indicator**: Optional green crosshair showing exact center position for accurate position marking
- **Mouse-Only Interface**: All interactions through mouse clicks (keyboard shortcuts disabled for better integration)

### Position Management
- **Position List with Notes**: Save positions with optional notes for easy identification
- **In-Table Editing**: Edit positions directly in the table with automatic validation
- **Smart Position Adding**: Automatically fills first empty row instead of appending
- **Sorting**: Click column headers to sort by position or note
- **Clear All**: Quick removal of all positions with confirmation dialog
- **Auto-Rename on Export**: Prevents file overwrites by auto-numbering duplicates

## Installation

1. Download the compiled `target/DanceNow.jar` JAR file
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
- Enter X,Y,Z,T coordinates in the compact input fields
- Click "Go" button to navigate (Enter key disabled)
- The view will center on the specified position while preserving zoom
- Toggle "Show center +" to display/hide the green crosshair indicator

### Position List Management

#### Adding Positions
- Click **"Add"** to add the current view center position to the list
- Optional **Note field** persists after adding for rapid annotation
- **Right-click** on the position list and select "Paste Positions" from context menu
- Positions are displayed in X,Y,Z,T format with optional notes
- Edit positions directly in the table - changes are validated immediately
- First empty row is automatically used when adding new positions

#### Navigating Through Positions
- Click **"< Back"** to go to the previous position in the list
- Click **"Next >"** to go to the next position in the list
- Click any position in the list to select it, then click "Go"
- Navigation wraps around (Next from last position goes to first)
- Out-of-bounds positions show warnings and prevent navigation
- Manual edits in table are immediately reflected in navigation

#### Managing the List
- Select a position and click **"Remove"** to delete it from the list
- Click **"Clear All"** to remove all positions (with confirmation)
- Click **"Export"** to save positions as TXT or CSV format
- Click **"Load"** to import positions from TXT or CSV files
- **Sort positions**: Click column headers to sort by position or note
- **Paste positions via context menu**:
  - Right-click and select "Paste Positions"
  - Supports multiple delimiters: comma, space, or tab
  - Perfect for copying data directly from spreadsheets
  - Format: One position per line with optional note

### Features in Detail

- **Compact Interface**: Optimized field widths and button sizes for minimal window footprint
- **Real-time Updates**: Position and crosshair update every 50ms with mouse movement listeners
- **Smart Crosshair**: Size adjusts with zoom level to remain visually consistent
- **File Safety**: Auto-rename prevents overwriting existing export files
- **CSV Support**: Export/import with headers and note column preservation
- **Validation**: Real-time coordinate validation with helpful error messages
- **Multi-dimensional Support**: Works with 2D, 3D (Z-stacks), and 4D (time series) images
- **Cross-platform**: Compatible with both Mac and Windows systems

## Requirements

- Fiji/ImageJ
- Java 8 or later

## Building

```bash
mvn clean package
```

The compiled plugin will be available as `DanceNow.jar` in the `target/` directory.

## File Formats

### CSV Format (with Notes)
```csv
X,Y,Z,T,Note
512,384,1,1,Cell nucleus
1024,768,5,1,Region of interest
256,256,10,3,Background sample
```

### TXT Format (Position only)
```
512,384,1,1
1024,768,5,1
256,256,10,3
```

### Import Flexibility
The plugin intelligently handles various formats:
- **Missing X,Y**: Position skipped with warning
- **Missing Z,T**: Defaults to 1 with notification
- **Out-of-bounds X,Y**: Imported but warns during navigation
- **Out-of-bounds Z,T**: Auto-adjusted to valid range
- **Multiple delimiters**: Comma, space, tab, or mixed

### Excel/Spreadsheet Integration
1. Arrange data in columns (X, Y, Z, T, optional Note)
2. Copy cells from spreadsheet
3. Right-click in DanceNow position list
4. Select "Paste Positions" from context menu
5. Positions automatically parse and validate

