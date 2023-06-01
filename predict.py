#!/usr/bin/env python3
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

	global event, beat_count

	gesture = 4 # defaults to crash

	timestep = 10
	data = {
		"index": [],
		"middle": [],
		"ring": [],
		"pinky": []
	}

	def predict_gesture():
		x = np.c_[
			np.array(data["index"][-timestep:]).reshape(-1,1),
			np.array(data["middle"][-timestep:]).reshape(-1,1),
			np.array(data["ring"][-timestep:]).reshape(-1,1),
			np.array(data["pinky"][-timestep:]).reshape(-1,1)
			]
		x = uniform_filter1d(x, axis=0, size=10)
		x = np.ravel(x, order="F").reshape(1,-1)

		# Get model prediction
		y_pred = model.predict(x, verbose=0)[0]
		gesture = np.argmax(y_pred)
		
		return gesture

	SOUND_ACTIVATED = False # prime sound to be played at next condition (accX rises to peak)
	SOUND_OVER = True # prevent double triggering (accX must fall to a valley)
	ACC_THRES_1 = 400
	ACC_THRES_2 = 0
	ACC_OVER_THRES = -2500

	ready_counter = 0
	while event.is_set():
		line = my_serial.readline()   # read a byte
		if not line:
			continue
		try:
			string = line.decode()	# convert the byte string to a unicode string
	
			# Parse data into 5 values for flex sensors + accX
			lst = [float(k) for k in string.split(',')[:-1]]
			if len(lst) != 5:
				continue
			ready_counter += 1
			if ready_counter == 20:
				print("Ready.")
		
			accX = lst[4]

			if accX > ACC_THRES_1 and SOUND_OVER:
				SOUND_ACTIVATED = True
				SOUND_OVER = False

			if accX < ACC_THRES_2 and SOUND_ACTIVATED:
				SOUND_ACTIVATED = False
				gesture = predict_gesture()
				t = threading.Thread(target=play_sound, args=[gesture])
				t.start()

			if accX < ACC_OVER_THRES:
				SOUND_OVER = True

			if not SOUND_ACTIVATED:
				continue

			# Parse data and pre-process for input to model
			data["index"].append(lst[0])
			data["middle"].append(lst[1])
			data["ring"].append(lst[2])
			data["pinky"].append(lst[3])

		except Exception as e:
			continue
			# print("Exception in main loop: {!s}".format(e))

beat_count = 1
# Play the sound according to the gesture
def play_sound(ind):
	global PLAY_SOUND, SOUNDS, beat_count
	if PLAY_SOUND == "play":
		SOUNDS[ind].play()
	else:
		global keyboard
		keyboard.press(SOUNDS[ind])
		time.sleep(0.05)
		keyboard.release(SOUNDS[ind])
	print("BEAT %d: %s     " % (beat_count, GESTURES[ind]))
	beat_count += 1
	return


# ======== Main code ========
parser = argparse.ArgumentParser()
parser.add_argument('-d', '--dev', default="ttyUSB0", type=str, help="Device ID under /dev/ (e.g. cu.usbserial-XXXXXX")
parser.add_argument('-n', '--hand', default="r", type=str, help="Hand: 'r' for right, 'l' for left. Default is right.")
parser.add_argument('-s', '--sound', default="p", type=str, help="Method for playing sound: 'k' for keyboard press, or 'p' for playing sample. Default is playing.")
args = parser.parse_args()

SERIAL_PATH = "/dev/" + str(args.dev)
BAUD_RATE = 115200
HAND = "left" if args.hand == "l" else "right"
MODEL_PATH = "models/mlp"
GESTURES = ["Kick","Hihat","Snare","Tom","Crash"]
PLAY_SOUND = "play" if args.sound == "p" else "keyboard"
if PLAY_SOUND == "play":
	import pyglet
	SOUNDS = [pyglet.resource.media("samples/" + file + ".wav", streaming=False)
		for file in ["kick","hihat","snare","tom","crash"]
		]
else:
	from pynput.keyboard import Controller
	keyboard = Controller()
	# Kick, Hihat, Snare, Tom, Crash
	# SOUNDS = ["a", "t", "s", "h", "o"] # Logic Pro
	SOUNDS = ["z", "g", "x", "b", "2"] # Hydrogen

event = threading.Event()
model = keras.models.load_model(MODEL_PATH)
my_serial = serial.Serial(SERIAL_PATH, BAUD_RATE, timeout=1)
event.set()
try:
	read_serial(model, HAND)
	
except KeyboardInterrupt:
	event.clear()
	if PLAY_SOUND == "play":
		pyglet.app.exit()
	my_serial.close()
