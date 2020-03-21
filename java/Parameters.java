package net.floodlightcontroller.task2;

import org.projectfloodlight.openflow.types.*;

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
		IPv4Address.of("10.0.2.1"),
		IPv4Address.of("10.0.2.2")
	};
	
	final static IPv4Address[] IP_HOST = { IPv4Address.of("10.0.2.3"), IPv4Address.of("10.0.2.4"), IPv4Address.of("10.0.2.5") };
	
	final static MacAddress[] MAC_ROUTER = {
		MacAddress.of("00:00:00:00:00:01"),
		MacAddress.of("00:00:00:00:00:02")
	};
	
	public final static IPv4Address VIRTUAL_IP = IPv4Address.of("10.0.2.254");
	public final static MacAddress VIRTUAL_MAC = MacAddress.of("00:00:E5:00:01:01");
	
	final static OFPort[] SW_PORTS = {
			OFPort.of(4), //first 2 are the routers so I can access them with the ROUTER IDs
			OFPort.of(5),
			OFPort.of(1),
			OFPort.of(2),
			OFPort.of(3)			
	};
}
