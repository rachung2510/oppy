# Project Oppy: Gesture-controlled drumkit
Uses machine learning to classify different gestures into different parts of the drum (kick, snare, hi-hat). User wears a glove with embedded flex sensors and an IMU unit to play drums in the air.

## Data Collection
1. Upload **code>code.ino** to your Nano.
2. Connect the Nano to your laptop.
3. In **collect_data.py**, define your paths. For Windows, SERIAL_PATH would be like "COM10". For Unix systems, the SERIAL_PATH should be something like "/dev/ttyUSB0". 
4. Run **collect_data.py**. When prompted for the gesture, type in the gesture for that trial in the format ```<first_gesture_num>-<second_gesture_num>```. Gestures are: 
   - (0) Kick - fist
   - (1) Hihat - 1 finger (index)
   - (2) Snare - 2 fingers (index & middle)
   - (3) Tom - 3 fingers (index, middle & ring)
   - (4) Crash - open palm
But you can define your own transition names too.
6. Once ```Reading...``` is printed, start doing your gestures. Try not to go beyond 60 BPM. Collect 100 gestures.
7. Once 100 gestures have been performed, Ctrl-C to stop data collection. When prompted for your CSV filename, input your desired filename or just press Enter to set it as the gesture you previously inputted.
