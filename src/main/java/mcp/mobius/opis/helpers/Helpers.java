package mcp.mobius.opis.helpers;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class Helpers {
	public static Side getEffectiveSide(){
        Thread thr = Thread.currentThread();

        return FMLCommonHandler.instance().getEffectiveSide();
        
        /*
        if ((thr instanceof ThreadMinecraftServer) || (thr instanceof ServerListenThread) || (thr instanceof TcpWriterThread) || (thr instanceof TcpReaderThread))
        {
            return Side.SERVER;
        }
        
        return Side.CLIENT;
        */		
	}
}