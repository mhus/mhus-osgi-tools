package de.mhus.osgi.web.virtualization.impl;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;

import org.osgi.service.component.ComponentContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import de.mhus.lib.core.MXml;
import de.mhus.lib.core.config.XmlConfig;
import de.mhus.osgi.web.virtualization.api.VirtualHost;
import de.mhus.osgi.web.virtualization.api.VirtualHostProvider;

@Component(immediate=true)
public class DefaultHostProvider implements VirtualHostProvider {

	private File configDir = new File("etc/vhosts");
	private HashMap<String,VirtualHost> hostMapping = new HashMap<>();

	@Activate
	public void doActivate(ComponentContext ctx) {
		updateConfiguration();
	}
	

	@Deactivate
	public void doDeactivate(ComponentContext ctx) {
		hostMapping.clear();
	}
	
	private void updateConfiguration() {
		synchronized (hostMapping) {
			hostMapping.clear();
			loadDirectory(configDir);
		}
	}
	
	private void loadDirectory(File dir) {
		for (File file : dir.listFiles()) {
			if (file.isFile() && file.getName().endsWith(".xml")) {
				try {
					java.io.FileInputStream is = new FileInputStream(file);
					Document doc = MXml.loadXml(is);
					loadDocument(doc);
					is.close();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			} else
			if (file.isDirectory())
				loadDirectory(file);
		}
	}


	private void loadDocument(Document doc) {
		for (Element host : MXml.getLocalElementIterator(doc.getDocumentElement(), "virtualhost")) {
			loadVHost(host);
		}
	}


	private void loadVHost(Element host) {
		try {
			XmlConfig config = new XmlConfig(host);
			DefaultVirtualHost vhost = new DefaultVirtualHost(config);
			for (String name : vhost.getHostNames()) {
				hostMapping.put(name, vhost);
			}
		} catch (Throwable t) {
			t.printStackTrace(); //TODO LOG!
		}
	}

	@Override
	public String[] getProvidedHosts() {
		synchronized (hostMapping) {
			return hostMapping.keySet().toArray(new String[hostMapping.size()]);
		}
	}

	@Override
	public boolean existsHost(String host) {
		synchronized (hostMapping) {
			return hostMapping.containsKey(host);
		}
	}

	@Override
	public VirtualHost getHost(String host) {
		synchronized (hostMapping) {
			return hostMapping.get(host);
		}
	}

}
