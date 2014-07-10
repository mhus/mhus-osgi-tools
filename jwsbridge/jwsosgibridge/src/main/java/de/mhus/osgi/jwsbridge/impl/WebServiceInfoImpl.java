package de.mhus.osgi.jwsbridge.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.ws.Endpoint;

import org.osgi.framework.ServiceReference;

import de.mhus.osgi.jwsbridge.JavaWebService;
import de.mhus.osgi.jwsbridge.WebServiceInfo;

public class WebServiceInfoImpl extends WebServiceInfo {

	private static Logger log = Logger.getLogger(WebServiceInfoImpl.class.getSimpleName());
	@SuppressWarnings("unused")
	private JavaWebServiceAdminImpl admin;
	private ServiceReference<JavaWebService> reference;
	private JavaWebService service;
	private Object webService;
	private String error;
	private Endpoint endpoint;
	private static long nextId = 0;
	private long id = ++nextId;

	public WebServiceInfoImpl(JavaWebServiceAdminImpl admin,
			ServiceReference<JavaWebService> reference) {
		this.admin = admin;
		this.reference = reference;
		service = admin.getContext().getService(reference);
		if (service != null) setName(service.getServiceName());
	}

	public void disconnect() {
		if (!isConnected()) return;
		log.fine("JWS Disconnect: " + getName());
		endpoint.stop();
		webService = null;
		service.stopped(this);
		endpoint = null;
		
	}

	public JavaWebService getJavaWebService() {
		return service;
	}

	public void connect() {
		if (isConnected() || getName() == null || getName().length() == 0 || service == null) return;
		error = null;
		endpoint = null;
		webService = service.getServiceObject();
		try {
			log.fine("JWS Connect: " + getName());
			endpoint = Endpoint.publish("/" + getName(), webService);
		} catch (Throwable t) {
			error = t.getMessage();
			webService = null;
			endpoint = null;
			log.log(Level.WARNING, "ERROR: " + getName() + " " + webService, t);
		}
		if (endpoint != null)
			service.published(this);
	}
	
	public boolean isConnected() {
		return endpoint != null;
	}
	
	
	public String getStatus() {
		if (error != null)
			return "Error: " + error;
		
		if (!isConnected())
			return "not connected";
		
		return endpoint.isPublished() ? "published" : "not published";
		
	}
	
	public String getBindingInfo() {
		if (!isConnected()) return "";
		
		try {
			//TODO This could be more generic
			String myName = webService.getClass().getSimpleName();
			if (myName.endsWith("Impl")) myName = myName.substring(0, myName.length()-4);
			return "jws|" + myName + "|http://localhost:8181/cxf/" + getName() + "?wsdl";
		} catch (Throwable t) {}
		return "/" + getName();
	}

	private String turnArround(String name) {
		String[] parts = name.split("\\.");
		StringBuffer out = new StringBuffer();
		for (String part : parts) {
			if (out.length() != 0)
				out.insert(0, ".");
			out.insert(0, part);
		}
		return out.toString();
	}

	public boolean is(JavaWebService service2) {
		return service2.equals(service);
	}

	public boolean is(String name) {
		if (name.equals(String.valueOf(id))) return true;
		return name.equals(getName());
	}

	public long getId() {
		return id;
	}

	@Override
	public String getBundleName() {
		return reference.getBundle().getSymbolicName();
	}
	
	public Endpoint getEndpoint() {
		return endpoint;
	}
	
	public void setName(String name) {
		if (name == null || name.length() == 0 || name.equals(getName())) return;
		boolean connected = isConnected();
		disconnect();
		super.setName(name);
		if (connected) connect();
	}

}
