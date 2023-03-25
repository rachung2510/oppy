import os
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3' # Ignore tensorflow warnings
import argparse
import serial
import time
import threading
import numpy as np
from tensorflow import keras
from scipy.ndimage import uniform_filter1d

def read_serial(model, HAND):
	global event

	gesture = "4-4" # defaults to crash
	prev_gesture = gesture

	# Keep track of sample number (to ensure a certain no. of samples between each beat)
	sample_num = 0
	prev_sample_num = sample_num

	timestep = 30
	window_size = 10 # prediction occurs every <window_size> samples
	data = {
		"index": [],
		"middle": [],
		"ring": [],
		"pinky": []
	}
	TRANSITIONS = [
		"0-0","0-1","0-2","0-3","0-4",
		"1-0","1-1","1-2","1-3","1-4",
		"2-0","2-1","2-2","2-3","2-4",
		"3-0","3-1","3-2","3-3","3-4",
		"4-0","4-1","4-2","4-3","4-4"
	]
	BEAT = False # 
	BEAT_ACTIVATiED = False # 
	BEAT_INTERVAL = 60 # number of samples between each beat (to prevent double-triggering)
	LOW = 100
	HIGH = 1000

	while event.is_set():
		line = serial.readline()   # read a byte
		if not line:
			continue
		try:
			string = line.decode()	# convert the byte string to a unicode string
			# print(string, end="")
			if prev_gesture != gesture:
				# print(gesture)
				prev_gesture = gesture

			# Parse data into 5 values for flex sensors + accX
			lst = [float(k) for k in string.split(',')[:-1]]
			if len(lst) != 5:
				continue
			sample_num += 1
			data["index"].append(lst[0])
			data["middle"].append(lst[1])
			data["ring"].append(lst[2])
			data["pinky"].append(lst[3])
		
			accX = lst[4]

			# If next beat is ready to be triggered
			if not BEAT:
				# Right hand (accX goes up then down)
				if HAND == "right":
					# Activate beat to be sounded
					if accX > HIGH and not BEAT_ACTIVATED:
						BEAT_ACTIVATED = True
						prev_sample_num = sample_num

					# Actually sound beat
					if BEAT_ACTIVATED and accX < LOW:
						BEAT = True
						BEAT_ACTIVATED = False # reset to False to allow next activation of beat
						ind = int(gesture[2]) # gesture index
						t = threading.Thread(target=play_sound, args=[ind])
						t.start()
				# Left hand (accX goes down then up)
				else:
					if accX < -HIGH and not BEAT_ACTIVATED:
						BEAT_ACTIVATED = True
						prev_sample_num = sample_num
					if BEAT_ACTIVATED and accX > -LOW:
						BEAT = True
						BEAT_ACTIVATED = False
						ind = int(gesture[2])
						t = threading.Thread(target=play_sound, args=[ind])
						t.start()
				
			# Ensure that there're 60 samples between each beat
			# Prevents double triggering
			else:
				if sample_num - prev_sample_num > BEAT_INTERVAL:
					BEAT = False

			# Parse data and pre-process for input to model
			if len(data["index"]) == timestep:
				x = np.c_[
					np.array(data["index"]).reshape(-1,1),
					np.array(data["middle"]).reshape(-1,1),
					np.array(data["ring"]).reshape(-1,1),
					np.array(data["pinky"]).reshape(-1,1)
					]
				x = uniform_filter1d(x, axis=0, size=10)
				for k in range(4):
					data_min, data_max = np.min(x), np.max(x)
					x[:,k] = (x[:,k] - data_min) / (data_max - data_min)
				x = np.ravel(x, order="F").reshape(1,-1)
				for k,v in data.items():
					data[k] = v[window_size:]	# prediction occurs every 10 samples

				# Get model prediction
				y_pred = model.predict(x, verbose=0)[0]
				if max(y_pred) > 0.90:
					transition = np.argmax(y_pred)
					predicted_gesture = TRANSITIONS[transition]
					if not ((predicted_gesture[0] == predicted_gesture[2]) and 
						(predicted_gesture[0] != prev_gesture[2])):
						gesture = predicted_gesture
		except:
			pass

# Play the sound according to the gesture
beat_count = 1
def play_sound(ind):
	global PLAY_SOUND, SOUNDS, beat_count
	if PLAY_SOUND == "play":
		SOUNDS[ind].play()
	else:
		keyboard.press_and_release(SOUNDS[ind])
	print("BEAT %d: %s     " % (beat_count, GESTURES[ind]), end="\r")
	beat_count += 1
	return


# ======== Main code ========
parser = argparse.ArgumentParser()
parser.add_argument('-d', '--dev', default=0, type=str, help="Device ID under /dev/ttyUSB")
parser.add_argument('-n', '--hand', default="r", type=str, help="Hand: 'r' for right, 'l' for left. Default is right.")
parser.add_argument('-s', '--sound', default="p", type=str, help="Method for playing sound: 'k' for keyboard press, or 'p' for playing sample. Default is playing.")
args = parser.parse_args()

SERIAL_PATH = "/dev/ttyUSB" + str(args.dev)
BAUD_RATE = 115200
HAND = "left" if args.hand == "l" else "right"
MODEL_PATH = "models/mlp_right" if HAND == "right" else "models/mlp_left"
GESTURES = ["Kick","Hihat","Snare","Tom","Crash"]
PLAY_SOUND = "play" if args.sound == "p" else "keyboard"
if PLAY_SOUND == "play":
	import pyglet
	SOUNDS = [pyglet.resource.media("samples/" + file + ".wav", streaming=False)
		for file in ["kick","hihat","snare","tom","crash"]
		]
else:
	import keyboard
	SOUNDS = ["a", "t", "s", "h", "o"]

event = threading.Event()
model = keras.models.load_model(MODEL_PATH)
serial = serial.Serial(SERIAL_PATH, BAUD_RATE, timeout=1)
event.set()
try:
	print("Ready.")
	read_serial(model, HAND)
	
except KeyboardInterrupt:
	event.clear()
	pyglet.app.exit()
	serial.close()
