package mcp.mobius.opis.server;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet3Chat;
import net.minecraft.util.ChatMessageComponent;
import mcp.mobius.opis.modOpis;
import mcp.mobius.opis.data.holders.AmountHolder;
import mcp.mobius.opis.data.holders.ChunkStats;
import mcp.mobius.opis.data.holders.EntityStats;
import mcp.mobius.opis.data.holders.TickHandlerStats;
import mcp.mobius.opis.data.holders.TileEntityStats;
import mcp.mobius.opis.data.managers.ChunkManager;
import mcp.mobius.opis.data.managers.EntityManager;
import mcp.mobius.opis.data.managers.TickHandlerManager;
import mcp.mobius.opis.data.managers.TileEntityManager;
import mcp.mobius.opis.network.enums.DataReq;
import mcp.mobius.opis.network.server.Packet_DataList;
import mcp.mobius.opis.network.server.Packet_LoadedChunks;
import mcp.mobius.opis.network.server.Packet_MeanTime;
import mcp.mobius.opis.overlay.OverlayStatus;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import mcp.mobius.mobiuscore.profiler.DummyProfiler;
import mcp.mobius.mobiuscore.profiler.ProfilerRegistrar;

public class OpisServerTickHandler implements ITickHandler {

	public long profilerUpdateTickCounter = 0;	
	public long clientUpdateTickCounter = 0;
	public long profilerRunningTicks;

	public static OpisServerTickHandler instance;
	
	public OpisServerTickHandler(){
		instance = this;
	}
	
	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		if(type.contains(TickType.SERVER)){
			clientUpdateTickCounter++;
			if (clientUpdateTickCounter % 100 == 0){
				updatePlayers();
				clientUpdateTickCounter = 0;
			}
			
			profilerUpdateTickCounter++;
			
			if (profilerRunningTicks < modOpis.profilerMaxTicks && modOpis.profilerRun){
				profilerRunningTicks++;
			}else if (profilerRunningTicks >= modOpis.profilerMaxTicks && modOpis.profilerRun){
				profilerRunningTicks = 0;
				modOpis.profilerRun = false;
				ProfilerRegistrar.turnOff();				
				
				// Here we should send a full update to all the clients registered
				
				ArrayList<TickHandlerStats> timingHandlers = TickHandlerManager.getCumulatedStats();
				ArrayList<EntityStats>      timingEntities = EntityManager.getTopEntities(100);
				ArrayList<TileEntityStats>  timingTileEnts = TileEntityManager.getTopEntities(100);
				ArrayList<ChunkStats>         timingChunks = ChunkManager.getTopChunks(100);

				
				for (EntityPlayer player : PlayerTracker.instance().playersSwing){

					// This portion is to get the proper filtered amounts depending on the player preferences.
					String name = player.getEntityName();
					boolean filtered = false;
					if (PlayerTracker.instance().filteredAmount.containsKey(name))
						filtered = PlayerTracker.instance().filteredAmount.get(name);
					ArrayList<AmountHolder> amountEntities = EntityManager.getCumulativeEntities(filtered);

					// Here we send a full update to the player
					PacketDispatcher.sendPacketToPlayer(Packet_DataList.create(DataReq.LIST, DataReq.AMOUNT, DataReq.ENTITIES,  amountEntities), (Player)player); 					
					PacketDispatcher.sendPacketToPlayer(Packet_DataList.create(DataReq.LIST, DataReq.TIMING, DataReq.HANDLERS,  timingHandlers), (Player)player);
					PacketDispatcher.sendPacketToPlayer(Packet_DataList.create(DataReq.LIST, DataReq.TIMING, DataReq.ENTITIES,  timingEntities), (Player)player);
					PacketDispatcher.sendPacketToPlayer(Packet_DataList.create(DataReq.LIST, DataReq.TIMING, DataReq.TILETENTS, timingTileEnts), (Player)player);
					PacketDispatcher.sendPacketToPlayer(Packet_DataList.create(DataReq.LIST, DataReq.TIMING, DataReq.CHUNK,       timingChunks), (Player)player);					
				}
				
				for (EntityPlayer player : PlayerTracker.instance().playersOpis)
					PacketDispatcher.sendPacketToPlayer(new Packet3Chat(ChatMessageComponent.createFromText(String.format("\u00A7oOpis automaticly stopped after %d ticks.", modOpis.profilerMaxTicks))), (Player)player);

			}			
		}
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.SERVER);
	}

	@Override
	public String getLabel() {
		return "opis.server.tickhandler";
	}

	private void updatePlayers(){
		for (Player player : modOpis.proxy.playerOverlayStatus.keySet()){
			if (modOpis.proxy.playerOverlayStatus.get(player) == OverlayStatus.CHUNKSTATUS)
				PacketDispatcher.sendPacketToPlayer( Packet_LoadedChunks.create(ChunkManager.getLoadedChunks(modOpis.proxy.playerDimension.get(player))), player);
			if (modOpis.proxy.playerOverlayStatus.get(player) == OverlayStatus.MEANTIME)
				PacketDispatcher.sendPacketToPlayer( Packet_MeanTime.create(TileEntityManager.getTimes(modOpis.proxy.playerDimension.get(player)), modOpis.proxy.playerDimension.get(player)), player);
		}
	}
	
}
