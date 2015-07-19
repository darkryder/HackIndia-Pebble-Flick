import time
from flask import Flask, request
import subprocess

app = Flask(__name__)


def jaimatadi():
	subprocess.Popen(["vlc", "a.mp4"])

codes_map = {
	'-1': None,
	'99': jaimatadi
}

current_codes = ['0000']

@app.route("/setGesture")
def set():
	import pdb
	code = request.args.get('q')
	if len(code) == 1: code = '0' + code
	# pdb.set_trace()

	if code in codes_map:
		codes_map[code]()


	current_codes.append(code + str(time.time())[::-1][:2])
	return ""

@app.route("/getC")
def get():
    return str(current_codes[-1])
    # import random
    # return str(random.random())
import os

port = int(os.getenv("VCAP_APP_PORT"))
if __name__ == "__main__":
    app.run(host='0.0.0.0', port=port)