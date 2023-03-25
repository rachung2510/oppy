# Project Oppy: Gesture-controlled drumkit
Uses machine learning to classify different gestures into different parts of the drum (kick, snare, hi-hat). User wears a glove with embedded flex sensors and an IMU unit to play drums in the air.

## Data Collection
1. Upload **code>code.ino** to your Nano.
2. Connect the Nano to your laptop.
3. In **collect_data.py**, define your paths. For Windows, ```SERIAL_PATH``` would be like "COM10". For Unix systems, ```SERIAL_PATH``` should be something like "/dev/ttyUSB0". ```FIGURES_PATH``` is optional (directory doesn't have to exist).
4. Run **collect_data.py**. When prompted for the gesture, type in the gesture for that trial in the format ```<first_gesture_num><second_gesture_num>``` (e.g. 13). Gestures are: 
   - (0) Kick - fist
   - (1) Hihat - 1 finger (index)
   - (2) Snare - 2 fingers (index & middle)
   - (3) Tom - 3 fingers (index, middle & ring)
   - (4) Crash - open palm
6. Once ```Reading...``` is printed, start doing your gestures. Try not to go beyond 60 BPM. Collect 100 gestures.
7. Once 100 gestures have been performed, Ctrl-C to stop data collection. When prompted for your CSV filename, input your desired filename or just press Enter to set it as the gesture you previously inputted.

## Files
- **collect_data.py**: Python script for collecting data
- **create_dataset.ipynb**: Jupyter notebook for creating combined dataset from individual CSVs of trials for each gesture transition
- **predict.py**: Python script for realtime gesture prediction. Run with ```python3 predict.py [--dev <usb_device_no>] [--hand <l/r>] [--sound <k/p for keyboard or playing>]```.
- **model_XXX.ipynb**: Jupyter notebook for training of XXX model
