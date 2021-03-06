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
			
			// Cast to Packet-In
			OFPacketIn pi = (OFPacketIn) msg;

	        // Dissect Packet included in Packet-In
			if (pkt instanceof ARP) {
				if (eth.isBroadcast() || eth.isMulticast() 
						|| eth.getDestinationMACAddress().compareTo(Parameters.VIRTUAL_MAC) == 0 
						|| eth.getSourceMACAddress().compareTo(Parameters.MAC_ROUTER[0]) == 0 
						|| eth.getSourceMACAddress().compareTo(Parameters.MAC_ROUTER[1])== 0) {
				
				System.out.printf("Processing ARP request...\n");
				
				// Process ARP request
				handleARPRequest(sw, pi, cntx);
				
				// Interrupt the chain
				return Command.STOP;
				}
			} else if (pkt instanceof IPv4) {
					
					System.out.printf("Processing IPv4 packet...\n");
					
					IPv4 ip_pkt = (IPv4) pkt;
					
					handleIPPacket(sw, pi, cntx);

					return Command.STOP;
			}
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
		
		//CASE 1
		// Destination Virtual Router because we want to travel to network B
		// host is trying to discover who has this virtual IP
		// I (the controller) will respond to this ARP request
		if(arpRequest.getTargetProtocolAddress().compareTo(Parameters.VIRTUAL_IP) == 0) { 
			
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
		else if(eth.getSourceMACAddress().compareTo(Parameters.MAC_ROUTER[0]) == 0 || eth.getSourceMACAddress().compareTo(Parameters.MAC_ROUTER[1])== 0) { 
			//CASE 2
			// Destination node of network A, ARP coming from R2 (gateway for net B) aimed at discovering host_A address
			// I want to hide the real MAC of R2 and mask it with the VIRTUAL MAC
					
			System.out.println("Managing incoming ARP Request from net B...");
			
			OFFlowAdd.Builder fmb = sw.getOFFactory().buildFlowAdd();
			
			fmb.setIdleTimeout(Parameters.ARP_IDLE_TIMEOUT);
			fmb.setHardTimeout(Parameters.ARP_HARD_TIMEOUT);
			fmb.setBufferId(OFBufferId.NO_BUFFER);
			fmb.setOutPort(OFPort.ANY);
			fmb.setCookie(U64.of(0));
			fmb.setPriority(FlowModUtils.PRIORITY_MAX);
			
			// Create the match structure  
			Match.Builder mb = sw.getOFFactory().buildMatch();
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.ETH_SRC, eth.getSourceMACAddress());
			
			OFActions actions = sw.getOFFactory().actions();
			
			// Create the actions (Change the SRC_MAC to VIRTUAL_MAC)
			ArrayList<OFAction> actionList = new ArrayList<OFAction>();
			
			OFOxms oxms = sw.getOFFactory().oxms();
			
			OFActionSetField setVMAC = actions.buildSetField()
				    .setField(
				        oxms.buildEthSrc()
				        .setValue(Parameters.VIRTUAL_MAC)
				        .build()
				    )
				    .build();
			actionList.add(setVMAC);
			
			setVMAC = actions.buildSetField()
				    .setField(
				        oxms.buildArpSha() //sha = sender hardware address
				        .setValue(Parameters.VIRTUAL_MAC)
				        .build()
				    )
				    .build();
			actionList.add(setVMAC);
			
			OFActionSetField setVIP = actions.buildSetField()
				    .setField(
				        oxms.buildArpSpa() //spa = sender protocol address
				        .setValue(Parameters.VIRTUAL_IP)
				        .build()
				    )
				    .build();
			actionList.add(setVIP);
			
			//specify the port
			OFActionOutput output = actions.buildOutput()
				    .setMaxLen(0xFFffFFff)
				    .setPort(OFPort.FLOOD)
				    .build();
			actionList.add(output);
			
			fmb.setActions(actionList);
			fmb.setMatch(mb.build());
			
			sw.write(fmb.build());
			
			//INSTALLING opposite rule -> when host A replies to ARP request to router
			fmb = null;
			fmb = sw.getOFFactory().buildFlowAdd();
			
			fmb.setIdleTimeout(Parameters.ARP_IDLE_TIMEOUT);
			fmb.setHardTimeout(Parameters.ARP_HARD_TIMEOUT);
			fmb.setBufferId(OFBufferId.NO_BUFFER);
			fmb.setOutPort(OFPort.CONTROLLER);
			fmb.setCookie(U64.of(0));
			fmb.setPriority(FlowModUtils.PRIORITY_MAX);
			
			// Create the match structure  
			mb = null;
			mb = sw.getOFFactory().buildMatch();
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.ETH_DST, Parameters.VIRTUAL_MAC);
				//.setExact(MatchField.ETH_SRC, eth.getDestinationMACAddress()); //src mac is the one from the host of net A
			
			actions = null;
			actions = sw.getOFFactory().actions();
			
			// Create the actions (Change the SRC_MAC to VIRTUAL_MAC)
			ArrayList<OFAction> actionListRev = new ArrayList<OFAction>();
			
			oxms = null;
			oxms = sw.getOFFactory().oxms();
			
			OFActionSetField setMAC = actions.buildSetField()
				    .setField(
				        oxms.buildEthDst()
				        .setValue(eth.getSourceMACAddress())
				        .build()
				    )
				    .build();
			actionListRev.add(setMAC);
			
			setVMAC = actions.buildSetField()
				    .setField(
				        oxms.buildArpTha() //tha = target hardware address
				        .setValue(eth.getSourceMACAddress())
				        .build()
				    )
				    .build();
			actionListRev.add(setMAC);
			
			OFActionSetField setIP = actions.buildSetField()
				    .setField(
				        oxms.buildArpTpa() //tpa = target protocol address
				        .setValue(arpRequest.getSenderProtocolAddress())
				        .build()
				    )
				    .build();
			actionListRev.add(setIP);
			
			//specify the port
			output = null;
			output = actions.buildOutput()
				    .setMaxLen(0xFFffFFff)
				    .setPort(pi.getMatch().get(MatchField.IN_PORT))
				    .build();
			actionListRev.add(output);
			
			fmb.setActions(actionListRev);
			fmb.setMatch(mb.build());
			
			sw.write(fmb.build());
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
		
		// prepare the rules for incoming and outgoing ICMP packets
		if(Parameters.MASTER_STATUS) {
			ArrayList<OFAction> sendList = sendICMP(sw, pi, cntx, eth);
			ArrayList<OFAction> recvList = recvICMP(sw, pi, cntx, eth);
			
			OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
			pob.setBufferId(pi.getBufferId());
			pob.setInPort(OFPort.ANY);
			
			// only save the actions needed for this case (captain obvious: if the destination = VMAC then it is an outgoing packet)
			if(eth.getDestinationMACAddress().compareTo(Parameters.VIRTUAL_MAC) == 0)
				pob.setActions(sendList);
			else 
				pob.setActions(recvList);
			
			// Packet might be buffered in the switch or encapsulated in Packet-In 
			// If the packet is encapsulated in Packet-In sent it back
			if (pi.getBufferId() == OFBufferId.NO_BUFFER) {
				// Packet-In buffer-id is none, the packet is encapsulated -> send it back
			    		byte[] packetData = pi.getData();
			    		pob.setData(packetData);
			    
			} 
					
			sw.write(pob.build());
			
		}
		else ICMP_unreachable(sw, pi, cntx, src, dst);		
	}
	
	//Add rule from network A to network B
	private ArrayList<OFAction> sendICMP(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, Ethernet eth) {
		
		System.out.println("Adding rule for send ICMP packets...");
		
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
			.setExact(MatchField.ETH_DST, Parameters.VIRTUAL_MAC);
			
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
		    
		return actionList;
	}
	
	//Add rule from network B to network A
	private ArrayList<OFAction> recvICMP(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, Ethernet eth) {
		
		System.out.println("Adding rule for recv ICMP packets...");
		
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
			.setExact(MatchField.ETH_SRC, Parameters.MAC_ROUTER[1]); //Router 2 because is gateway for NetB
			
		OFActions actions = sw.getOFFactory().actions();

		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		
		OFOxms oxms = sw.getOFFactory().oxms();
		
		//change source MAC -> VIRTUAL_MAC
		OFActionSetField setSrcVMAC = actions.buildSetField()
			    .setField(
			        oxms.buildEthSrc()
			        .setValue(Parameters.VIRTUAL_MAC)
			        .build()
			    )
			    .build();
		actionList.add(setSrcVMAC);
		
		//if the received packet_in is a ping request 
		if(eth.getDestinationMACAddress().compareTo(Parameters.VIRTUAL_MAC) == 0) {
			OFActionOutput output = actions.buildOutput()
				    .setMaxLen(0xFFffFFff)
				    .setPort(pi.getMatch().get(MatchField.IN_PORT))
				    .build();
			actionList.add(output);
		}
		//if the packet_in received is a ping reply
		else if(eth.getSourceMACAddress().compareTo(Parameters.MAC_ROUTER[1]) == 0) {
			OFActionOutput output = actions.buildOutput()
				    .setMaxLen(0xFFffFFff)
				    .setPort(Parameters.H_PORTS.get(eth.getDestinationMACAddress()))
				    .build();
			actionList.add(output);
		}
		
		fmb.setActions(actionList);
    	fmb.setMatch(mb.build());

        sw.write(fmb.build());
        
        return actionList;
        
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
