package net.floodlightcontroller.task2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

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

public class TController implements IFloodlightModule, IOFMessageListener {
	
	protected IFloodlightProviderService floodlightProvider; // Reference to the provider
	private IOFSwitch sw;
	
	private HashMap<Integer, Long> timestamps = new HashMap<Integer, Long>(); //coppie <ROUTER_ID, timestamp>
	
	private static Timer TIMER = null;
	TimerTask routerDisconnection = null;
	
	public class MasterHandler extends TimerTask {
		public void run() {
			System.out.println("Master Timer expired! \n");
			
			handleMasterTimeout();
		}
	}
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return TController.class.getSimpleName();
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
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IFloodlightProviderService.class);
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		
		//System.out.println("\n TController.init() \n");
		
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		
		for(int i=0; i<Parameters.NUM_ROUTERS; i++)
			timestamps.put(i+1, (long) 0);
		
		System.out.println("\n TController Starting...\n");
	}
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
			
		IPacket pkt = eth.getPayload();
		
		if(this.sw == null)
			this.sw = sw;

        // Dissect Packet included in Packet-In
		if(eth.isBroadcast()) {
			
			if(pkt instanceof IPv4) {
				
				IPv4 ip = (IPv4) pkt;			
				
				if(ip.getProtocol() == IpProtocol.UDP) {
					
					UDP udp = (UDP) ip.getPayload();
					if(udp.getDestinationPort().compareTo(Parameters.ADV_PORT) == 0) {
						
						//ROUTER ID is in the payload of advertisement
						Data payload = (Data) udp.getPayload();

						int id = Integer.parseInt(new String(payload.getData()));
						long currentTS = System.currentTimeMillis() / 1000;
						
						//System.out.println("Router "+id+": "+currentTS);
						
						timestamps.put(id, currentTS);
						
						if(id == Parameters.MASTER_ID) resetTimer();

						return Command.STOP;	
					}
				}
			}
		}
		
		return Command.CONTINUE;
	}
	
	private void resetTimer()
	{
		if(TIMER != null) {
			TIMER.cancel();
			TIMER.purge();
		}
		
		TIMER = new Timer();
		TIMER.schedule(new MasterHandler(), Parameters.T_DOWN);
		
	}
	
	private void handleMasterTimeout() {
		
		int backup_id = (Parameters.MASTER_ID == 1)? 2 : 1;
		Parameters.MASTER_STATUS = false;
		
		System.out.println("MASTER ("+Parameters.MASTER_ID+") is down. Attempting to connect to backup...");

		if((System.currentTimeMillis() / 1000) - timestamps.get(backup_id) >= 3) close(); //last ADV from backup was over 3secs ago -> router dead
		else {
			Parameters.MASTER_ID = backup_id;
			Parameters.MASTER_STATUS = true;
			Parameters.BACKUP_STATUS = false;
			
			System.out.println("Router "+Parameters.MASTER_ID+" is now MASTER.");
			
			resetTimer();
			resetRules();
		}
	}
	
	private void close() {
		//close everything
		System.out.println("No Router is available, closing application...");
		
		//come si fa correttamente?
		System.exit(0);
	}
	
	private void resetRules() {
		
		System.out.println("Resetting flow rules...");
		//Delete previously installed rules
		
		OFFlowMod.Builder fb = sw.getOFFactory().buildFlowDelete();
		fb.setBufferId(OFBufferId.NO_BUFFER);
		fb.setOutPort(OFPort.ANY);
		fb.setCookie(U64.of(0));
		fb.setPriority(FlowModUtils.PRIORITY_MAX);
        
        sw.write(fb.build());
        
        //Insert the default rules
        
        fb = sw.getOFFactory().buildFlowAdd();
        
        fb.setIdleTimeout(0);
        fb.setHardTimeout(0);
        fb.setBufferId(OFBufferId.NO_BUFFER);
        fb.setOutPort(OFPort.ANY);
        fb.setCookie(U64.of(0));
        fb.setPriority(0);
        
        ArrayList<OFAction> actionList = new ArrayList<OFAction>();
        
        OFActions actions = sw.getOFFactory().actions();
        
        OFActionOutput output = actions.buildOutput()
    	    .setMaxLen(0xFFffFFff)
    	    .setPort(OFPort.CONTROLLER)
    	    .build();
        actionList.add(output);
        
        fb.setActions(actionList);
        sw.write(fb.build());
	}
}

