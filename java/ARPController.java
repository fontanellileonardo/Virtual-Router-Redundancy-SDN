package net.floodlightcontroller.task2;

import java.io.IOException;
import java.util.*;

import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.*;
import org.projectfloodlight.openflow.util.HexString;
import org.python.constantine.platform.darwin.IPProto;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.module.*;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.util.FlowModUtils;

public class ARPController implements IOFMessageListener, IFloodlightModule {
	
	protected IFloodlightProviderService floodlightProvider; // Reference to the provider
		
	@Override
	public String getName() {
		return ARPController.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IFloodlightProviderService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		
		System.out.println("\n ARPController Starting... \n");
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
			
			Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
			
			IPacket pkt = eth.getPayload();

			// Print the source MAC address
			Long sourceMACHash = Ethernet.toLong(eth.getSourceMACAddress().getBytes());
			//System.out.printf("MAC Address: {%s} seen on switch: {%s}\n",
			//HexString.toHexString(sourceMACHash),
			//sw.getId());
			
			// Cast to Packet-In
			OFPacketIn pi = (OFPacketIn) msg;

	        // Dissect Packet included in Packet-In
			if (eth.isBroadcast() || eth.isMulticast() || eth.getDestinationMACAddress().compareTo(Parameters.VIRTUAL_MAC) == 0) {
				if (pkt instanceof ARP) {
					
					System.out.printf("Processing ARP request\n");
					
					// Process ARP request
					handleARPRequest(sw, pi, cntx);
					
					// Interrupt the chain
					return Command.STOP;
				}
			} else {
				if (pkt instanceof IPv4) {
					
					System.out.printf("Processing IPv4 packet\n");
					
					IPv4 ip_pkt = (IPv4) pkt;
					
					if(ip_pkt.getDestinationAddress().compareTo(Parameters.VIRTUAL_IP) == 0){
						handleIPPacket(sw, pi, cntx);
						
						// Interrupt the chain
						return Command.STOP;
					}
				}
			}
			
			// Interrupt the chain
			return Command.CONTINUE;

	}
	
	private void handleARPRequest(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {

		// Double check that the payload is ARP
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		if (! (eth.getPayload() instanceof ARP))
			return;
		
		// Cast the ARP request
		ARP arpRequest = (ARP) eth.getPayload();
		
		if(arpRequest.getTargetProtocolAddress().compareTo(Parameters.VIRTUAL_IP) == 0) { //Destination Virtual Router			
			System.out.printf("Managing Virtual ARP Request...");
			// Generate ARP reply
			IPacket arpReply = new Ethernet()
				.setSourceMACAddress(Parameters.VIRTUAL_MAC)
				.setDestinationMACAddress(eth.getSourceMACAddress())
				.setEtherType(EthType.ARP)
				.setPriorityCode(eth.getPriorityCode())
				.setPayload(
					new ARP()
					.setHardwareType(ARP.HW_TYPE_ETHERNET)
					.setProtocolType(ARP.PROTO_TYPE_IP)
					.setHardwareAddressLength((byte) 6)
					.setProtocolAddressLength((byte) 4)
					.setOpCode(ARP.OP_REPLY)
					.setSenderHardwareAddress(Parameters.VIRTUAL_MAC) // Set my MAC address
					.setSenderProtocolAddress(Parameters.VIRTUAL_IP) // Set my IP address
					.setTargetHardwareAddress(arpRequest.getSenderHardwareAddress())
					.setTargetProtocolAddress(arpRequest.getSenderProtocolAddress()));
			
			// Create the Packet-Out and set basic data for it (buffer id and in port)
			OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
			pob.setBufferId(OFBufferId.NO_BUFFER);
			pob.setInPort(OFPort.ANY);
			
			// Create action -> send the packet back from the source port
			OFActionOutput.Builder actionBuilder = sw.getOFFactory().actions().buildOutput();
			
			// The method to retrieve the InPort depends on the protocol version 
			OFPort inPort = pi.getMatch().get(MatchField.IN_PORT);
			actionBuilder.setPort(inPort);
			
			// Assign the action
			pob.setActions(Collections.singletonList((OFAction) actionBuilder.build()));
			
			// Set the ARP reply as packet data 
			byte[] packetData = arpReply.serialize();
			pob.setData(packetData);
			
			System.out.printf("Sending out ARP reply\n");
			
			sw.write(pob.build());
		}
		else { //Destination node of network A
					
			System.out.println("Managing Broadcast ARP Request...");
			
			//in sospeso
			//boh come cazzo si fa?
			//di tecco fa le due funzioni addBroadcastFlowRule e addVirtualFlowRule
		}		
	}

	private void handleIPPacket(IOFSwitch sw, OFPacketIn pi,
			FloodlightContext cntx) {

		// Double check that the payload is IPv4
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		if (! (eth.getPayload() instanceof IPv4))
			return;
		
		// Cast the IP packet
		IPv4 ipv4 = (IPv4) eth.getPayload();

		// Check that the IP is actually an ICMP request
		if (! (ipv4.getPayload() instanceof ICMP))
			return;
		
		IPv4Address src = ipv4.getSourceAddress();
		IPv4Address dst = ipv4.getDestinationAddress();
		
		if(Parameters.MASTER_STATUS) {
			sendICMP(sw, pi, cntx, src, dst);
			recvICMP(sw, pi, cntx, src, dst);
		}
		else ICMP_unreachable(sw, pi, cntx, src, dst);		
	}
	
	//Add rule from network A to network B
	private void sendICMP(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, IPv4Address src, IPv4Address dst) {
		
		OFFlowAdd.Builder fmb = sw.getOFFactory().buildFlowAdd();
		
		fmb.setIdleTimeout(Parameters.ICMP_IDLE_TIMEOUT);
		fmb.setHardTimeout(Parameters.ICMP_HARD_TIMEOUT);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setOutPort(OFPort.ANY);
		fmb.setCookie(U64.of(0));
		fmb.setPriority(FlowModUtils.PRIORITY_MAX);
		
		// Create the match structure  
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
			.setExact(MatchField.IPV4_DST, dst)
			.setExact(MatchField.ETH_DST, Parameters.VIRTUAL_MAC)
			.setExact(MatchField.IPV4_SRC, src)
			.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
			
		OFActions actions = sw.getOFFactory().actions();
        
		// Create the actions (Change DST mac and IP addresses and set the out-port)
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		
		OFOxms oxms = sw.getOFFactory().oxms();
		
		OFActionSetField setDlDst = actions.buildSetField()
			    .setField(
			        oxms.buildEthDst()
			        .setValue(Parameters.MAC_ROUTER[Parameters.MASTER_ID-1])
			        .build()
			    )
			    .build();
		actionList.add(setDlDst);
		
		OFActionOutput output = actions.buildOutput()
			    .setMaxLen(0xFFffFFff)
			    .setPort(Parameters.SW_PORTS[Parameters.MASTER_ID-1])
			    .build();
		actionList.add(output);
		
		fmb.setActions(actionList);
        	fmb.setMatch(mb.build());

	        sw.write(fmb.build());
	        
	        System.out.println("sendICMP rule added!");
	        
	        // If we do not apply the same action to the packet we have received and we send it back the first packet will be lost
        
		// Create the Packet-Out and set basic data for it (buffer id and in port)
		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
		pob.setBufferId(pi.getBufferId());
		pob.setInPort(OFPort.ANY);
		
		// Assign the action
		pob.setActions(actionList);
		
		// Packet might be buffered in the switch or encapsulated in Packet-In 
		// If the packet is encapsulated in Packet-In sent it back
		if (pi.getBufferId() == OFBufferId.NO_BUFFER) {
			// Packet-In buffer-id is none, the packet is encapsulated -> send it back
            		byte[] packetData = pi.getData();
            		pob.setData(packetData);
            
		} 
				
		sw.write(pob.build());
	}
	
	//Add rule from network B to network A
	private void recvICMP(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, IPv4Address src, IPv4Address dst) {
		OFFlowAdd.Builder fmb = sw.getOFFactory().buildFlowAdd();
		
		fmb.setIdleTimeout(Parameters.ICMP_IDLE_TIMEOUT);
		fmb.setHardTimeout(Parameters.ICMP_HARD_TIMEOUT);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setOutPort(OFPort.ANY);
		fmb.setCookie(U64.of(0));
		fmb.setPriority(FlowModUtils.PRIORITY_MAX);
		
		// Create the match structure  
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
			.setExact(MatchField.IPV4_DST, src)
			.setExact(MatchField.IPV4_SRC, dst)
			.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
			
		OFActions actions = sw.getOFFactory().actions();

		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		
		OFOxms oxms = sw.getOFFactory().oxms();
		
		//change source MAC -> VIRTUAL_MAC
		OFActionSetField setDlDst = actions.buildSetField()
			    .setField(
			        oxms.buildEthDst()
			        .setValue(Parameters.VIRTUAL_MAC)
			        .build()
			    )
			    .build();
		actionList.add(setDlDst);
		
		OFActionOutput output = actions.buildOutput()
			    .setMaxLen(0xFFffFFff)
			    .setPort(pi.getMatch().get(MatchField.IN_PORT))
			    .build();
		actionList.add(output);
		
		fmb.setActions(actionList);
        	fmb.setMatch(mb.build());

	        sw.write(fmb.build());
	        
	        System.out.println("recvICMP rule added!");
	}
	
	private void ICMP_unreachable(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, IPv4Address src, IPv4Address dst) {
		// Double check that the payload is IPv4
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		if (! (eth.getPayload() instanceof IPv4))
			return;
		
		// Cast the IP packet
		IPv4 ipv4 = (IPv4) eth.getPayload();

		// Check that the IP is actually an ICMP request
		if (! (ipv4.getPayload() instanceof ICMP))
			return;

		// Cast to ICMP packet
		ICMP icmpRequest = (ICMP) ipv4.getPayload();
			
		// Generate ICMP reply
		IPacket icmpReply = new Ethernet()
			.setSourceMACAddress(Parameters.VIRTUAL_MAC)
			.setDestinationMACAddress(eth.getSourceMACAddress())
			.setEtherType(EthType.IPv4)
			.setPriorityCode(eth.getPriorityCode())
			.setPayload(
				new IPv4()
				.setProtocol(IpProtocol.ICMP)
				.setDestinationAddress(ipv4.getSourceAddress())
				.setSourceAddress(Parameters.VIRTUAL_IP)
				.setTtl((byte)64)
				.setProtocol(IpProtocol.IPv4)
				// Set the same payload included in the request
				.setPayload(
						new ICMP()
						.setIcmpType(ICMP.DESTINATION_UNREACHABLE)
						.setIcmpCode(ICMP.CODE_PORT_UNREACHABLE)
                        .setPayload(icmpRequest.getPayload())
				)
				);
		
		// Create the Packet-Out and set basic data for it (buffer id and in port)
		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
		pob.setBufferId(OFBufferId.NO_BUFFER);
		pob.setInPort(OFPort.ANY);
		
		// Create action -> send the packet back from the source port
		OFActionOutput.Builder actionBuilder = sw.getOFFactory().actions().buildOutput();
		// The method to retrieve the InPort depends on the protocol version 
		OFPort inPort = pi.getMatch().get(MatchField.IN_PORT);
		actionBuilder.setPort(inPort); 
		
		// Assign the action
		pob.setActions(Collections.singletonList((OFAction) actionBuilder.build()));
		
		// Set the ICMP reply as packet data 
		byte[] packetData = icmpReply.serialize();
		pob.setData(packetData);
		
		sw.write(pob.build());
		
		System.out.println("Master is dead. ICMP error has been sent.");
	}
}
