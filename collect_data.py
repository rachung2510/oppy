import serial
import time
import threading
import matplotlib.pyplot as plt
import pandas as pd
import os
import sys

SERIAL_PATH = "/dev/ttyUSB0"
BAUD_RATE = 115200
DATA_PATH = "../data/"
FIGURES_PATH = "figures/"
GESTURES = ["Kick","Hihat","Snare","Tom","Crash"]

def read_serial():
	global event, data
	while event.is_set():
		line = serial.readline()   # read a byte
		if not line:
			continue
		try:
			string = line.decode()  # convert the byte string to a unicode string
			print(string, end="")
			lst = [float(k) for k in string.split(',')[:-1]]
			if len(lst) != 5:
				continue
			data["time"].append(time.time())
			data["index"].append(lst[0])
			data["middle"].append(lst[1])
			data["ring"].append(lst[2])
			data["pinky"].append(lst[3])
			data["accX"].append(lst[4])
		except:
			pass
	serial.close()

def plot_data():
	global data, gesture
	trial_duration = data["time"][-1] - data["time"][0]
	freq = len(data["accX"]) / trial_duration
	plt.figure(figsize=(15,6))
	for k,v in data.items():
		if k in ["index", "middle", "ring", "pinky", "accX"]:
			plt.plot(v, label=k)
	try:
		[g1,g2] = [int(k) for k in gesture.split('-')]
	except:
		g1,g2 = 0,0
	plt.ylim((400,900))
	plt.title("Gesture data for %s-%s (Freq = %.3fHz)" % (GESTURES[g1], GESTURES[g2], freq))
	plt.legend()
	if os.path.exists(FIGURES_PATH):
		plt.savefig(FIGURES_PATH + gesture + ".png")
	plt.show()

# Check paths
if not os.path.exists(DATA_PATH):
	print(f"[ERROR] Data path {DATA_PATH} does not exist. Make directory if needed or change DATA_PATH.")
	sys.exit()
if not os.path.exists(SERIAL_PATH):
	print(f"[ERROR] Path to USB {SERIAL_PATH} is wrong. Check again.")
	sys.exit()

# Main code
data = {
	"gesture": [],
	"time": [],
	"index": [],
	"middle": [],
	"ring": [],
	"pinky": [],
	"accX": []
}
event = threading.Event()
t = threading.Thread(target=read_serial)
serial = serial.Serial(SERIAL_PATH, BAUD_RATE, timeout=1)

gesture = input("Gesture (e.g. 01): ")
gesture = gesture[0] + '-' + gesture[1]
event.set()
t.start()
print("Reading...(Ctrl-C once you've done 100 gestures)")
try:
	while True:
		time.sleep(1)

except KeyboardInterrupt:
	event.clear()
	t.join()
	plot_data()
	data["gesture"] = [gesture for k in data["accX"]]
	filename = input("Save data as (if left empty, filename will be <gesture>.csv): ")
	if not filename:
		filename = gesture + ".csv"
	df = pd.DataFrame(data)	
	df.to_csv(DATA_PATH + filename)
	print("Saved data as " + filename)
