import os
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
import argparse
import serial
import time
import threading
import numpy as np
from tensorflow import keras
from scipy.ndimage import uniform_filter1d
# import pyglet
import keyboard

def read_serial(model, HAND):
	global event

	gesture = "4-4" # defaults to crash
	prev_gesture = "4-4"

	sample_num = 0
	prev_sample_num = 0

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
	BEAT = False
	sound_activated = False
	LOW = 100
	HIGH = 1000

	while event.is_set():
		line = serial.readline()   # read a byte
		if not line:
			continue
		try:
			string = line.decode()	# convert the byte string to a unicode string
			if prev_gesture != gesture:
				# print(gesture)
				prev_gesture = gesture
			# print(string, end="")
			lst = [float(k) for k in string.split(',')[:-1]]
			if len(lst) != 5:
				continue
			sample_num += 1

			if HAND == "right":
				data["index"].append(lst[0])
				data["middle"].append(lst[1])
				data["ring"].append(lst[2])
				data["pinky"].append(lst[3])
			else:
				data["index"].append(lst[3])
				data["middle"].append(lst[2])
				data["ring"].append(lst[1])
				data["pinky"].append(lst[0])

			accX = lst[4]

			if not BEAT:
				if accX > HIGH and not sound_activated:
					sound_activated = True
					prev_sample_num = sample_num
				if sound_activated and accX < LOW:
					ind = int(gesture[2])
					BEAT = True
					t = threading.Thread(target=play_sound, args=[ind])
					t.start()
					sound_activated = False
			else:
				if sample_num - prev_sample_num > 60:
					BEAT = False

			if len(data["index"]) == timestep:
				x = np.c_[
					np.array(data["index"]).reshape(-1,1),
					np.array(data["middle"]).reshape(-1,1),
					np.array(data["ring"]).reshape(-1,1),
					np.array(data["pinky"]).reshape(-1,1)
					]
				x = uniform_filter1d(x, axis=0, size=10)
				for k in range(4):
					# x[:,k] = x[:,k] - np.mean(x)
					data_min, data_max = np.min(x), np.max(x)
					x[:,k] = (x[:,k] - data_min) / (data_max - data_min)
				x = np.ravel(x, order="F").reshape(1,-1)
				for k,v in data.items():
					data[k] = v[window_size:]	# prediction occurs every 10 samples

				y_pred = model.predict(x, verbose=0)[0]
				if max(y_pred) > 0.90:
					transition = np.argmax(y_pred)
					predicted_gesture = TRANSITIONS[transition]
					if not ((predicted_gesture[0] == predicted_gesture[2]) and 
						(predicted_gesture[0] != prev_gesture[2])):
						gesture = predicted_gesture
		except:
			pass

beat_count = 1
def play_sound(ind):
	global SOUNDS, beat_count
	keyboard.press_and_release('a')
	# SOUNDS[ind].play()
	print("BEAT %d: %s     " % (beat_count, GESTURES[ind]), end="\r")
	beat_count += 1
	return


# ======== Main code ========
parser = argparse.ArgumentParser()
parser.add_argument('-d', '--dev', default=0, type=str)
parser.add_argument('-n', '--hand', default="r", type=str)
args = parser.parse_args()

SERIAL_PATH = "/dev/ttyUSB" + str(args.dev)
BAUD_RATE = 115200
HAND = "left" if args.hand == "l" else "right"
GESTURES = ["Kick","Hihat","Snare","Tom","Crash"]
SOUNDS = [pyglet.resource.media("samples/" + file + ".wav", streaming=False)
		for file in ["kick","hihat","snare","tom","crash"]
			]
# sound = pyglet.resource.media("trap_snare.wav", streaming=False)

event = threading.Event()
model = keras.models.load_model('mlp')
serial = serial.Serial(SERIAL_PATH, BAUD_RATE, timeout=1)

event.set()
try:
	print("Ready.")
	read_serial(model, HAND)
	
except KeyboardInterrupt:
	event.clear()
	pyglet.app.exit()
	serial.close()
