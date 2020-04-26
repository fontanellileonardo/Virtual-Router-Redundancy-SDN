package net.floodlightcontroller.task2;

import org.projectfloodlight.openflow.types.*;
import java.util.*;

public class Parameters {
	
	public final static int NUM_ROUTERS = 2;
	
	public final static int T_ADV = 1000; //interval of router advertisements (1000ms = 1sec)
	public final static int T_DOWN = 3 * T_ADV; //time after which a router is considered dead
	
	//status variables for routers
	public static boolean MASTER_STATUS = true;
	public static boolean BACKUP_STATUS = true;
	
	public static int MASTER_ID = 1; //1: R1, 2: R2
	
	public final static int ICMP_IDLE_TIMEOUT = 1;
	public final static int ICMP_HARD_TIMEOUT = 2;
	
	public final static int ARP_IDLE_TIMEOUT = 10;
	public final static int ARP_HARD_TIMEOUT = 20;
	
	public final static TransportPort ADV_PORT = TransportPort.of(8787);

	final static IPv4Address[] IP_ROUTER = {
		IPv4Address.of("10.0.2.1"), //r1
		IPv4Address.of("10.0.2.2") //r2
	};
	
	final static IPv4Address[] IP_HOST = { IPv4Address.of("10.0.2.3"), IPv4Address.of("10.0.2.4"), IPv4Address.of("10.0.2.5") };
	
	final static MacAddress[] MAC_ROUTER = {
		MacAddress.of("00:00:00:00:00:01"), //router 1
		MacAddress.of("00:00:00:00:00:02") //router 2
	};
	
	public final static IPv4Address VIRTUAL_IP = IPv4Address.of("10.0.2.254"); //virtual IP is the last addr of net 10.0.2
	public final static MacAddress VIRTUAL_MAC = MacAddress.of("00:00:5E:00:01:01");
	
	//switch ports connected to routers, I can easily access them with ROUTER IDs
	final static OFPort[] SW_PORTS = {
			OFPort.of(4),//r1
			OFPort.of(5)	//r2		
	};
	
	//for simplicity's sake, host's MAC addresses have been hardcoded with the respective port
	//if we wanted to do it dynamically, we could have saved this info the first time we receive an ARP request
	public static HashMap<MacAddress, OFPort> H_PORTS;
	static {
		H_PORTS = new HashMap<>();
		H_PORTS.put(MacAddress.of("00:00:00:00:00:a1"), OFPort.of(1));
		H_PORTS.put(MacAddress.of("00:00:00:00:00:a2"), OFPort.of(2));
		H_PORTS.put(MacAddress.of("00:00:00:00:00:a3"), OFPort.of(3));
	}
}
