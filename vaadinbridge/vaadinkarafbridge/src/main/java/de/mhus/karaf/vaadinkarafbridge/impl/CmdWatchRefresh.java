package de.mhus.karaf.vaadinkarafbridge.impl;

import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Action;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;

import de.mhus.osgi.vaadinbridge.BundleWatch;
import de.mhus.osgi.vaadinbridge.VaadinConfigurableResourceProviderAdmin;

@Command(scope = "vaadin", name = "watchrefresh", description = "Full-Refresh automatic bundle watch")
public class CmdWatchRefresh implements Action {

	private BundleWatch watch;

	public Object execute(CommandSession session) throws Exception {
		
		watch.refreshAll();
		
		return null;
	}

	public void setBundleWatch(BundleWatch watch) {
		this.watch = watch;
	}

}
