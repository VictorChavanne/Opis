package mcp.mobius.opis.overlay;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.util.MathHelper;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mapwriter.api.IMwChunkOverlay;
import mapwriter.api.IMwDataProvider;
import mapwriter.map.MapView;
import mapwriter.map.mapmode.MapMode;
import mcp.mobius.opis.data.holders.CoordinatesBlock;
import mcp.mobius.opis.data.holders.CoordinatesChunk;
import mcp.mobius.opis.gui.events.MouseEvent;
import mcp.mobius.opis.gui.interfaces.CType;
import mcp.mobius.opis.gui.interfaces.IWidget;
import mcp.mobius.opis.gui.interfaces.WAlign;
import mcp.mobius.opis.gui.widgets.LayoutBase;
import mcp.mobius.opis.gui.widgets.LayoutCanvas;
import mcp.mobius.opis.gui.widgets.WidgetGeometry;
import mcp.mobius.opis.gui.widgets.tableview.TableRow;
import mcp.mobius.opis.gui.widgets.tableview.ViewTable;
import mcp.mobius.opis.network.client.Packet_ReqChunks;
import mcp.mobius.opis.network.client.Packet_ReqDataDim;
import mcp.mobius.opis.network.client.Packet_ReqTeleport;

public class OverlayEntityPerChunk implements IMwDataProvider {

	public class EntitiesTable extends ViewTable{
		MapView mapView;
		MapMode mapMode;
		OverlayEntityPerChunk overlay;		
		
		public EntitiesTable(IWidget parent, OverlayEntityPerChunk overlay) { 	
			super(parent);
			this.overlay = overlay;			
		}
		
		public void setMap(MapView mapView, MapMode mapMode){
		    this.mapView = mapView;
			this.mapMode = mapMode;			
		}
		
		@Override
		public void onMouseClick(MouseEvent event){
			TableRow row = this.getRow(event.x, event.y);
			if (row != null){
				CoordinatesBlock coord = ((ReducedData)row.getObject()).chunk.asCoordinatesBlock();
				
				if (this.mapView.getX() != coord.x || this.mapView.getZ() != coord.z || this.mapView.getDimension() != coord.dim){
					this.mapView.setDimension(coord.dim);
					this.mapView.setViewCentre(coord.x, coord.z);
					this.overlay.requestChunkUpdate(this.mapView.getDimension(), 
							MathHelper.ceiling_double_int(this.mapView.getX()) >> 4, 
							MathHelper.ceiling_double_int(this.mapView.getZ()) >> 4);
				} else {
					PacketDispatcher.sendPacketToServer(Packet_ReqTeleport.create(coord));
					Minecraft.getMinecraft().setIngameFocus();
				}
			}
		}
	}	
	
	public class ChunkOverlay implements IMwChunkOverlay{

		Point coord;
		int minEnts;
		int maxEnts;
		int ents;
		boolean selected;
		
		public ChunkOverlay(int x, int z, int minEnts, int maxEnts, int ents, boolean selected){
			this.coord = new Point(x, z);
			this.minEnts = minEnts;
			this.maxEnts = maxEnts;
			this.ents    = ents;
			this.selected = selected;
		}
		
		@Override
		public Point getCoordinates() {	return this.coord; }

		@Override
		public int getColor() {
			double scaledAmount = (double)this.ents / (double)this.maxEnts;
			int    red          = MathHelper.ceiling_double_int(scaledAmount * 255.0);
			int    blue         = 255 - MathHelper.ceiling_double_int(scaledAmount * 255.0);
			
			return (200 << 24) + (red << 16) + (blue);  }

		@Override
		public float getFilling() {	return 1.0f; }

		@Override
		public boolean hasBorder() { return true; }

		@Override
		public float getBorderWidth() { return 0.5f; }

		@Override
		public int getBorderColor() { return this.selected ? 0xffffffff : 0xff000000; }
		
	}	
	
	class ReducedData implements Comparable{
		CoordinatesChunk chunk;
		int amount;
		
		public ReducedData(CoordinatesChunk chunk, int amount){
			this.chunk = chunk;
			this.amount = amount;
		}
		
		@Override
		public int compareTo(Object arg0) {
			return ((ReducedData)arg0).amount - this.amount;
		}
		
	}
	
	private static OverlayEntityPerChunk _instance;
	public boolean    showList = false;
	public LayoutCanvas canvas = null;
	public HashMap<CoordinatesChunk, Integer> overlayData = new HashMap<CoordinatesChunk, Integer>(); 
	public ArrayList<ReducedData> reducedData = new ArrayList<ReducedData>();
	CoordinatesChunk selectedChunk = null;
	
	private OverlayEntityPerChunk(){}
	
	public static OverlayEntityPerChunk instance(){
		if(_instance == null)
			_instance = new OverlayEntityPerChunk();			
		return _instance;
	}	
	
	public void reduceData(){
		this.reducedData.clear();
		for (CoordinatesChunk chunk : this.overlayData.keySet())
			this.reducedData.add(new ReducedData(chunk, this.overlayData.get(chunk)));
		Collections.sort(this.reducedData);
		
		for (ReducedData data : this.reducedData)
			System.out.printf("%s %s\n", data.chunk, data.amount);
	}
	
	@Override
	public ArrayList<IMwChunkOverlay> getChunksOverlay(int dim, double centerX, double centerZ, double minX, double minZ, double maxX, double maxZ) {
		ArrayList<IMwChunkOverlay> overlays = new ArrayList<IMwChunkOverlay>();
		
		int minEnts = 9999;
		int maxEnts = 0;

		for (CoordinatesChunk chunk : overlayData.keySet()){
			minEnts = Math.min(minEnts, overlayData.get(chunk));
			maxEnts = Math.max(maxEnts, overlayData.get(chunk));
		}		
		
		for (CoordinatesChunk chunk : overlayData.keySet()){
			if (chunk.dim == dim)
				if (this.selectedChunk != null)
					overlays.add(new ChunkOverlay(chunk.toChunkCoordIntPair().chunkXPos, chunk.toChunkCoordIntPair().chunkZPos, minEnts, maxEnts, overlayData.get(chunk), chunk.equals(this.selectedChunk)));
				else
					overlays.add(new ChunkOverlay(chunk.toChunkCoordIntPair().chunkXPos, chunk.toChunkCoordIntPair().chunkZPos, minEnts, maxEnts, overlayData.get(chunk), false));
		}
		return overlays;
	}

	@Override
	public String getStatusString(int dim, int bX, int bY, int bZ) {
		CoordinatesChunk chunk = new CoordinatesChunk(dim, bX >> 4, bZ >> 4);
		if (this.overlayData.containsKey(chunk))
			return String.format(", entities: %d", this.overlayData.get(chunk));
		else
			return ", entities: 0";
	}

	@Override
	public void onMiddleClick(int dim, int bX, int bZ, MapView mapview) {
		this.showList = false;
		
		int chunkX = bX >> 4;
		int chunkZ = bZ >> 4;		
		CoordinatesChunk clickedChunk = new CoordinatesChunk(dim, chunkX, chunkZ); 
		
		if (this.overlayData.containsKey(clickedChunk)){
			if (this.selectedChunk == null)
				this.selectedChunk = clickedChunk;
			else if (this.selectedChunk.equals(clickedChunk))
				this.selectedChunk = null;
			else
				this.selectedChunk = clickedChunk;
		} else {
			this.selectedChunk = null;
		}
		
		if (this.selectedChunk == null)
			this.showList = true;
		
		//if (this.selectedChunk != null)
		//	PacketDispatcher.sendPacketToServer(Packet_ReqTEsInChunk.create(this.selectedChunk));
		
		//ArrayList<CoordinatesChunk> chunks = new ArrayList<CoordinatesChunk>();
		//for (int x = -5; x <= 5; x++)
		//	for (int z = -5; z <= 5; z++)
		//		chunks.add(new CoordinatesChunk(dim, x, z));
		//PacketDispatcher.sendPacketToServer(Packet_ReqChunks.create(chunks));

	}

	@Override
	public void onDimensionChanged(int dimension, MapView mapview) {
		PacketDispatcher.sendPacketToServer(Packet_ReqDataDim.create(dimension, "overlay:chunk:entities"));
	}

	@Override
	public void onMapCenterChanged(double vX, double vZ, MapView mapview) {}

	@Override
	public void onZoomChanged(int level, MapView mapview) {}

	@Override
	public void onOverlayActivated(MapView mapview) {
		PacketDispatcher.sendPacketToServer(Packet_ReqDataDim.create(mapview.getDimension(), "overlay:chunk:entities"));
	}

	@Override
	public void onOverlayDeactivated(MapView mapview) {}

	@Override
	public void onDraw(MapView mapview, MapMode mapmode) {
		if (this.canvas == null)
			this.canvas = new LayoutCanvas();
		
		if (mapmode.marginLeft != 0){
			this.canvas.hide();
			return;
		}
		
		if (!this.showList)
			this.canvas.hide();
		else{
			this.canvas.show();		
			this.canvas.draw();
		}		
	}

	@SideOnly(Side.CLIENT)
	public void setupChunkTable(){
		LayoutBase layout = (LayoutBase)this.canvas.addWidget("Table", new LayoutBase(null));
		//layout.setGeometry(new WidgetGeometry(100.0,0.0,300.0,100.0,CType.RELXY, CType.REL_Y, WAlign.RIGHT, WAlign.TOP));
		layout.setGeometry(new WidgetGeometry(100.0,0.0,20.0,100.0,CType.RELXY, CType.RELXY, WAlign.RIGHT, WAlign.TOP));		
		layout.setBackgroundColors(0x90202020, 0x90202020);
		
		EntitiesTable table  = (EntitiesTable)layout.addWidget("Table_", new EntitiesTable(null, this));
		
		table.setGeometry(new WidgetGeometry(0.0,0.0,100.0,100.0,CType.RELXY, CType.RELXY, WAlign.LEFT, WAlign.TOP));
	    table.setColumnsAlign(WAlign.CENTER, WAlign.CENTER)
		     //.setColumnsTitle("\u00a7a\u00a7oType", "\u00a7a\u00a7oPos", "\u00a7a\u00a7oUpdate Time")
	    	 .setColumnsTitle("Pos", "N Entities")
			 .setColumnsWidth(75,25)
			 .setRowColors(0xff808080, 0xff505050)
			 .setFontSize(1.0f);

		int nrows = 0;
		for (ReducedData data : this.reducedData){
				table.addRow(data, data.chunk.toString(), String.valueOf(data.amount));
				nrows++;
				if (nrows > 100) break;
		}

		this.showList = true;
	}	
	
	@Override
	public boolean onMouseInput(MapView mapview, MapMode mapmode) {
		if (this.canvas != null && this.canvas.shouldRender() && ((LayoutCanvas)this.canvas).hasWidgetAtCursor()){
			((EntitiesTable)this.canvas.getWidget("Table").getWidget("Table_")).setMap(mapview, mapmode);
			this.canvas.handleMouseInput();
			return true;
		}
		return false;
	}

	private void requestChunkUpdate(int dim, int chunkX, int chunkZ){
		ArrayList<CoordinatesChunk> chunks = new ArrayList<CoordinatesChunk>();
		//HashSet<ChunkCoordIntPair> chunkCoords = new HashSet<ChunkCoordIntPair>(); 

		for (int x = -5; x <= 5; x++){
			for (int z = -5; z <= 5; z++){
				chunks.add(new CoordinatesChunk(dim, chunkX + x, chunkZ + z));
				if (chunks.size() >= 1){
					Packet250CustomPayload packet = Packet_ReqChunks.create(dim, chunks);
					if (packet != null)
						PacketDispatcher.sendPacketToServer(packet);
					chunks.clear();
				}
			}
		}
		
		if (chunks.size() > 0)
			PacketDispatcher.sendPacketToServer(Packet_ReqChunks.create(dim, chunks));				
	}	
	
}