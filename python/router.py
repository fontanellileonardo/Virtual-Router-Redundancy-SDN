import time
import socket  
import select
import netifaces as ni
import sys
import signal
from datetime import datetime

T_ADV = 1
NUM_ADV_DOWN = 3*T_ADV

COMM_PORT = 8787
BROADCAST_ADDRESS = "10.0.2.255"

sock = None
ROUTER_ID = sys.argv[1]

def init():
    
	global sock

	sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
	sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
	sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

	sock.bind(("", COMM_PORT))

	print("[INFO] Socket bound to port " +str(COMM_PORT))

	payload = ROUTER_ID
	sock.sendto(payload.encode(), (BROADCAST_ADDRESS, COMM_PORT));
	print("[INFO] Packet sent to "+str(BROADCAST_ADDRESS)+" throught port "+str(COMM_PORT))
    
def advertise():

	while True:
		payload = ROUTER_ID
		sock.sendto(payload.encode(), (BROADCAST_ADDRESS, COMM_PORT));
		print(str(time.time())+") R"+payload+" HELLO")
		time.sleep(T_ADV)

if __name__ == '__main__':
	init()
	advertise()
