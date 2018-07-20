package de.mhus.karaf.commands.testit;

import java.lang.reflect.Method;

import de.mhus.lib.core.MApi;
import de.mhus.lib.core.MHousekeeper;
import de.mhus.lib.core.MHousekeeperTask;
import de.mhus.lib.core.MThreadPool;
import de.mhus.lib.core.lang.ValueProvider;
import de.mhus.osgi.services.util.OsgiBundleClassLoader;

public class MhusShit implements ShitIfc {

	private long doitTime;

	@Override
	public void printUsage() {
		System.out.println("lookup <ifc> [<def>]");
		System.out.println("housekeepertest");
		System.out.println("housekeepertasks");
	}

	@Override
	public Object doExecute(String cmd, String[] parameters) throws Exception {
		if (cmd.equals("lookup")) {
			OsgiBundleClassLoader loader = new OsgiBundleClassLoader();
			Class<?> ifc = loader.loadClass(parameters[0]);
			Object obj = null;
			if (parameters.length > 1) {
				Class<?> def = loader.loadClass(parameters[1]);
				Method method = MApi.class.getMethod("lookup", Class.class, Class.class);
				obj = method.invoke(null, ifc, def);
			} else {
				obj = MApi.lookup(ifc);
			}
			
			if (obj != null) {
				System.out.println(obj.getClass());
			}
			return obj;
		}
		if (cmd.equals("housekeepertasks")) {
			MHousekeeper housekeeper = MApi.lookup(MHousekeeper.class);
			for (String info : housekeeper.getHousekeeperTaskInfo())
				System.out.println(info);
		}
		if (cmd.equals("housekeepertest")) {
			MHousekeeper housekeeper = MApi.lookup(MHousekeeper.class);
			doitTime = 0;
			MHousekeeperTask task = new MHousekeeperTask() {
								
				@Override
				protected void doit() throws Exception {
					System.out.println("--- doit");
					doitTime = System.currentTimeMillis();
				}
			};
			housekeeper.register(task, 1000);
			
			String res = MThreadPool.getWithTimeout(new ValueProvider<String>() {

				@Override
				public String getValue() throws Exception {
					System.out.println("--- check");
					if (doitTime == 0)
						return null;
					return "ok";
				}
			}, 30000, false);
			System.out.println(res + " " + doitTime);
		}
		return null;
	}

}