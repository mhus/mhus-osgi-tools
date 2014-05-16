package de.mhus.karaf.vaadinkarafbridge.impl;

import java.io.PrintStream;

import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Action;
import org.apache.karaf.shell.commands.Command;

import de.mhus.lib.core.console.ConsoleTable;
import de.mhus.osgi.vaadinbridge.VaadinConfigurableResourceProviderAdmin;

@Command(scope = "vaadin", name = "resourceList", description = "List all resource providers")
public class CmdVaadinResourceList implements Action {

	private VaadinConfigurableResourceProviderAdmin provider;

	public Object execute(CommandSession session) throws Exception {
		PrintStream out = System.out;
		//session.getConsole();
		ConsoleTable table = new ConsoleTable();
		table.setHeaderValues("Bundle","Resources");
		for (String s : provider.getResourceBundles()) {
			
			StringBuffer res = new StringBuffer();
			boolean first = true;
			for (String p : provider.getResourcePathes(s)) {
				if (!first) out.print(',');
				out.print(p);
				first = false;
			}
			table.addRowValues(s,res.toString());
		}
		table.print(out);
		out.flush();
		return null;
	}

	public void setResourceProvider(VaadinConfigurableResourceProviderAdmin provider) {
		this.provider = provider;
	}

}
