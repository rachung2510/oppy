from flask import Flask, jsonify, request, render_template, make_response
from flask_sqlalchemy import SQLAlchemy
from flask_marshmallow import Marshmallow
import pymysql
pymysql.install_as_MySQLdb()
from sqlalchemy.dialects.postgresql import JSON
import json

# Constants
SQL_USER = "admin"
SQL_PASSWORD = "admin"
SQL_PORT = 3306
SQL_DB = "drum_app"
IP = "0.0.0.0"

app = Flask(__name__)

app.config['SQLALCHEMY_DATABASE_URI'] = 'mysql+pymysql://%s:%s@%s:%d/%s' % \
	(SQL_USER, SQL_PASSWORD, IP, SQL_PORT, SQL_DB)
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)
ma = Marshmallow(app)

class GestureData(db.Model):
	id = db.Column(db.Integer, primary_key=True)
	trial = db.Column(db.Integer)
	date = db.Column(db.String(50))
	index = db.Column(db.Double)
	middle = db.Column(db.Double)
	ring = db.Column(db.Double)
	pinky = db.Column(db.Double)
	beat = db.Column(db.Integer)
	gesture = db.Column(db.String(5))

	def __init__(self, trial, date, flex, beat, gesture):
		self.trial = trial
		self.date = date
		self.index = flex[0]
		self.middle = flex[1]
		self.ring = flex[2]
		self.pinky = flex[3]
		self.beat = beat
		self.gesture = gesture

@app.route("/", methods=['GET','POST'])
def home():
	return render_template('index.html')
	# return "<h2>Gesture Controlled Drums</h2>\
	# <p><a href='/get-gesture-data'>View gesture data</a></p>"

@app.route("/add-training-data", methods=['POST'])
def add_training_data():
	flex = []

	trial = request.json['trial']
	date = request.json['date']
	flex.append(request.json['index'])
	flex.append(request.json['middle'])
	flex.append(request.json['ring'])
	flex.append(request.json['pinky'])
	beat = request.json['beat']
	gesture = request.json['gesture']

	results = {
		'trial': trial,
		'date': date,
		'index': flex[0],
		'middle': flex[1],
		'ring': flex[2],
		'pinky': flex[3],
		'beat': beat,
		'gesture': gesture
	}
	# print(results)

	data = GestureData(trial, date, flex, beat, gesture)
	db.session.add(data)
	db.session.commit()

	return jsonify(results)

class GestureDataSchema(ma.Schema):
	class Meta:
		fields = ('id','trial','date','index','middle','ring','pinky','beat','gesture')
gesture_data_schema = GestureDataSchema(many=True)

@app.route("/get-next-trial", methods=['GET'])
def get_next_trial():
	all_data = GestureData.query.all()
	results = gesture_data_schema.dump(all_data)
	trials = [k['trial'] for k in results]
	next_trial = max(trials) + 1 if trials else 0
	return jsonify(next_trial)

@app.route("/get-gesture-data", methods=["GET","POST"])
def get_gesture_data():
	global sample
	all_data = GestureData.query.all()
	results = gesture_data_schema.dump(all_data)
	trials = [k['trial'] for k in results]
	last_trial = max(trials)
	result = [k for k in results if k['trial'] == last_trial]
	data = {
		'id': [k['id'] for k in result],
		'index': [k['index'] for k in result],
		'middle': [k['middle'] for k in result],
		'ring': [k['ring'] for k in result],
		'pinky': [k['pinky'] for k in result],
		'beat': [k['beat']*800 for k in result]
	}
	# data = [id, index, middle, ring, pinky, beat]
	response = make_response(json.dumps(data))
	response.content_type = 'application/json'
	return response

if __name__ == "__main__":
	app.run(host=IP, debug=True)
