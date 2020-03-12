package net.floodlightcontroller.project;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

public class Parameters {
	
	public final static int T_ADV = 1000; //interval of router advertisements (1000ms = 1sec)
	public final static int T_DOWN = 3 * T_ADV; //time after which a router is considered dead
	
	//status variables for routers
	public static boolean R1_STATUS = true;
	public static boolean R2_STATUS = true;
	
	//public final static TransportPort ADV_PORT = TransportPort.of(8888);

	final static IPv4Address[] IP_ROUTER = {
		IPv4Address.of("10.0.2.1"),
		IPv4Address.of("10.0.2.2")
	};
	
	final static IPv4Address[] IP_HOST = {
		IPv4Address.of("10.0.2.3"),
		IPv4Address.of("10.0.2.4"),
		IPv4Address.of("10.0.2.5")
	};
	
	final static MacAddress[] MAC_ROUTER = {
		MacAddress.of("00:00:00:00:00:01"),
		MacAddress.of("00:00:00:00:00:02")
	};
	
	public final static IPv4Address VIRTUAL_IP = IPv4Address.of("10.0.2.254");
	public final static MacAddress VIRTUAL_MAC = MacAddress.of("00:00:E5:00:01:01");
}
