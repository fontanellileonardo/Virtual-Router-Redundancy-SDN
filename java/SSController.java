import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.util.FlowModUtils;

public class SSController implements IFloodlightModule, IOFMessageListener {

	protected IFloodlightProviderService floodlightProvider;
	
	//--------Initialization--------
	
	//Retrieve reference to the provider
	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException{
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class)
	}
	
	//Dependences Specification, add dependency on the provider
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies(){
			Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
			l.add(IFloodlightProviderService.class);
			return l;
	}
	
	//---------Handle received packet messages-------------
	
	//Set module name
	@Override
	public String getName(){
			return ModuleExample.class.getSimpleName();
	}
	
	//StartUp after module initialization
	@Override
	public void startUp(FloodlightModuleContext context){
			floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}
	
	//When a packet-in is received
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx){
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		//Print source MAC address
		Long sourceMACHash = Ethernet.toLong(eth.getSourceMACAddress().getBytes());
		System.out.printf("Source MAC Address: {%s} on switch: {%s}\n", HexString.toHexString(sourceMACHash), sw.getId());
		
		//Let other module process the packet 
		return Command.CONTINUE;
	}
}












