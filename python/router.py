import time
import socket  
import select
import netifaces as ni
import sys
import signal

T_ADV = 1
NUM_ADV_DOWN = 3*T_ADV

COMM_PORT = 8888
BROADCAST_ADDRESS = "10.0.2.255"

sock = None
ROUTER_ID = sys.argv[1]

def init():
    
    global sock

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    sock.bind(("", COMM_PORT))

    print "[INFO] Socket bound to port %d" %(COMM_PORT)

    sock.sendto(ROUTER_ID, (BROADCAST_ADDRESS, COMM_PORT))
    print "[INFO] Packet sent to %s throught port %s" %(BROADCAST_ADDRESS, COMM_PORT)
    
def advertise():

	while True:
		sock.sendto(ROUTER_ID, (BROADCAST_ADDRESS, COMM_PORT));
		#print ROUTER_ID+"-HELLO"
		time.sleep(T_ADV)

if __name__ == '__main__':
	init()
	advertise()
