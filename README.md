# Fiji DanceNow Plugin

A Fiji/ImageJ plugin that provides a persistent navigation window for quickly jumping to specific X,Y,Z,T coordinates while preserving your current zoom level, with position list management capabilities.

## Features

- **Persistent Navigation Window**: A floating, always-on-top window that stays open for repeated use
- **Real-time Position Updates**: Shows your current view center and image information
- **Position List Management**: Save, edit, and navigate through multiple positions
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

### Features in Detail

- **Persistent Window**: The DanceNow window stays open and can be used multiple times without reopening
- **Real-time Updates**: Current position updates automatically every 500ms
- **Always on Top**: Window stays visible above other applications for easy access
- **Boundary Checking**: Automatically validates coordinates against image dimensions
- **Multi-dimensional Support**: Works with 2D, 3D (Z-stacks), and 4D (time series) images
- **Editable Position List**: Double-click any position in the list to edit it directly
- **Position File Format**: Simple text format with one position per line (X,Y,Z,T)

## Requirements

- Fiji/ImageJ
- Java 8 or later

## Building

```bash
mvn clean package
```

The compiled plugin will be available as `DanceNow.jar` in the `target/` directory.

## Example Position File Format

When exporting positions, the plugin creates a simple text file with one position per line:

```
512,384,1,1
1024,768,5,1
256,256,10,3
800,600,15,5
```

Each line represents X,Y,Z,T coordinates separated by commas. This format can be edited in any text editor and imported back into the plugin.

